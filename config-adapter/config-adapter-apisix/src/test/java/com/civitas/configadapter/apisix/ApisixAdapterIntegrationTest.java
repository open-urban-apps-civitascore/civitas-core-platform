/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.Constants;
import com.civitas.configadapter.Topics;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.apisix.ApisixConfigValue;
import com.civitas.configadapter.model.apisix.RouteConfigValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Integration test for ApisixAdapter using Testcontainers. Tests actual APISIX operations for
 * upstream management.
 */
@Testcontainers
class ApisixAdapterIntegrationTest {

  private static final String ADMIN_API_KEY = "edd1c9f034335f136f87ad84b625c8f1";
  private static final Network network = Network.newNetwork();

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> etcd =
      new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.6.6"))
          .withNetwork(network)
          .withNetworkAliases("etcd")
          .withExposedPorts(2379, 2380)
          .withEnv("ETCD_ENABLE_V2", "true")
          .withEnv("ALLOW_NONE_AUTHENTICATION", "yes")
          .withEnv("ETCD_ADVERTISE_CLIENT_URLS", "http://etcd:2379")
          .withEnv("ETCD_LISTEN_CLIENT_URLS", "http://0.0.0.0:2379")
          .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1))
          .withReuse(false);

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> apisix =
      new GenericContainer<>(DockerImageName.parse("apache/apisix:3.14.0-debian"))
          .withNetwork(network)
          .withNetworkAliases("apisix")
          .dependsOn(etcd)
          .withExposedPorts(9080, 9180, 9443)
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("apisix-test-config.yaml", 0644),
              "/usr/local/apisix/conf/config.yaml")
          .waitingFor(
              Wait.forHttp("/apisix/admin/upstreams")
                  .forPort(9180)
                  .withHeader("X-API-KEY", ADMIN_API_KEY)
                  .forStatusCode(200))
          .withReuse(false);

  private ApisixAdapter adapter;
  private TestEventPublisher eventPublisher;
  private HttpClient httpClient;
  private ObjectMapper objectMapper;
  private String adminApiUrl;

  @BeforeEach
  void setUp() {
    adminApiUrl = "http://" + apisix.getHost() + ":" + apisix.getMappedPort(9180);

    waitForApisixReady(adminApiUrl);

    Map<String, Object> props = new HashMap<>();
    props.put("apisix.admin.url", adminApiUrl);
    props.put("apisix.admin.key", ADMIN_API_KEY);
    props.put(
        "apisix.topics",
        String.join(
            ",",
            Topics.BACKEND_CREATED.toString(),
            Topics.BACKEND_UPDATED.toString(),
            Topics.BACKEND_DELETED.toString(),
            Topics.ROUTE_CREATED.toString(),
            Topics.ROUTE_UPDATED.toString(),
            Topics.ROUTE_DELETED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    adapter = new ApisixAdapter();
    adapter.initialize(config);

    eventPublisher = new TestEventPublisher();
    adapter.setEventPublisher(eventPublisher);

    httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    objectMapper = new ObjectMapper();
  }

  @AfterEach
  void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
  }

  @Test
  void createUpstream() throws FatalAdapterException, RetryableAdapterException {
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1, "backend2:8080", 1));

    ConfigEvent event = createConfigEvent("upstreams", Operation.CREATE, upstreamConfig);

    adapter.processConfigEvent(Topics.BACKEND_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void updateUpstream() throws Exception {
    String upstreamId = "test-upstream-update";

    Map<String, Object> initialConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));

    createUpstreamDirectly(upstreamId, initialConfig);

    Map<String, Object> updatedConfig =
        Map.of(
            "type",
            "roundrobin",
            "nodes",
            Map.of("backend1:8080", 2, "backend2:8080", 1, "backend3:8080", 1));

    ConfigEvent event =
        createConfigEvent("upstreams/" + upstreamId, Operation.UPDATE, updatedConfig);

    adapter.processConfigEvent(Topics.BACKEND_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode upstream = getUpstreamFromApisix(upstreamId);
              assertNotNull(upstream);
              JsonNode nodes = upstream.get("value").get("nodes");
              assertEquals(3, nodes.size());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void deleteUpstream() throws Exception {
    String upstreamId = "test-upstream-delete";

    Map<String, Object> initialConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));

    createUpstreamDirectly(upstreamId, initialConfig);

    JsonNode upstream = getUpstreamFromApisix(upstreamId);
    assertNotNull(upstream, "Upstream should exist before delete");

    ConfigEvent event = createConfigEvent("upstreams/" + upstreamId, Operation.DELETE, null);

    adapter.processConfigEvent(Topics.BACKEND_DELETED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              try {
                getUpstreamFromApisix(upstreamId);
              } catch (Exception e) {
                assertTrue(e.getMessage().contains("404") || e.getMessage().contains("not found"));
              }
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void handleInvalidUpstreamConfiguration() {
    Map<String, Object> invalidConfig = Map.of("type", "invalid-type");

    ConfigEvent event = createConfigEvent("upstreams", Operation.CREATE, invalidConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.BACKEND_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_UPSTREAM_ERROR, exception.getErrorCode());
  }

  // ============== ROUTE INTEGRATION TESTS ==============

  @Test
  void createRoute() throws Exception {
    // First create an upstream that the route will reference
    String upstreamId = "test-upstream-for-route";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with plugins
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/test/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("prometheus", Map.of()));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void updateRoute() throws Exception {
    String routeId = "test-route-update";

    // First create an upstream
    String upstreamId = "test-upstream-for-route-update";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create the initial route directly
    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/initial/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    // Now update the route via the adapter
    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/updated/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins", Map.of("prometheus", Map.of(), "proxy-rewrite", Map.of("uri", "/updated")));

    ConfigEvent event =
        createRouteConfigEvent("routes/" + routeId, Operation.UPDATE, updatedRouteConfig);

    adapter.processConfigEvent(Topics.ROUTE_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route);
              String uri = route.get("value").get("uri").asText();
              assertEquals("/api/v1/updated/*", uri);
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void deleteRoute() throws Exception {
    String routeId = "test-route-delete";

    // First create an upstream
    String upstreamId = "test-upstream-for-route-delete";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route directly
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/to-delete/*");
    routeConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, routeConfig);

    // Verify route exists
    JsonNode route = getRouteFromApisix(routeId);
    assertNotNull(route, "Route should exist before delete");

    // Delete the route via the adapter
    ConfigEvent event = createRouteConfigEvent("routes/" + routeId, Operation.DELETE, null);

    adapter.processConfigEvent(Topics.ROUTE_DELETED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              try {
                getRouteFromApisix(routeId);
              } catch (Exception e) {
                assertTrue(e.getMessage().contains("404") || e.getMessage().contains("not found"));
              }
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithAllPlugins() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-for-full-route";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with all CIVITAS/CORE V1 plugins
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/full-plugins/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put("prometheus", Map.of());
    plugins.put("proxy-rewrite", Map.of("uri", "/rewritten"));
    plugins.put(
        "response-rewrite",
        Map.of("headers", Map.of("set", Map.of("X-Custom-Header", "custom-value"))));
    routeConfig.put("plugins", plugins);

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void handleInvalidRouteConfiguration() {
    // Create a route without required fields (no uri, no upstream)
    Map<String, Object> invalidConfig = Map.of("methods", List.of("GET"));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, invalidConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }

  // ============== SERVERLESS-POST-FUNCTION PLUGIN TESTS ==============

  @Test
  void createRouteWithServerlessPostFunction() throws Exception {
    // First create an upstream that the route will reference
    String upstreamId = "test-upstream-serverless-post";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with serverless-post-function plugin (log phase)
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-post/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Post-function executed') end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithServerlessPostFunctionHeaderFilter() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-serverless-header-filter";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with serverless-post-function using header_filter phase
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/header-filter/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "header_filter",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.header['X-Custom-Post-Header'] = 'processed' end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void updateRouteAddServerlessPostFunction() throws Exception {
    String routeId = "test-route-add-serverless-post";

    // First create an upstream
    String upstreamId = "test-upstream-for-add-serverless";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create initial route without serverless-post-function
    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-serverless/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    // Update route to add serverless-post-function plugin
    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-serverless/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of("return function(conf, ctx) ngx.log(ngx.INFO, 'Added via UPDATE') end"))));

    ConfigEvent event =
        createRouteConfigEvent("routes/" + routeId, Operation.UPDATE, updatedRouteConfig);

    adapter.processConfigEvent(Topics.ROUTE_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route);
              JsonNode plugins = route.get("value").get("plugins");
              assertNotNull(plugins);
              assertTrue(plugins.has("serverless-post-function"));
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithMultipleServerlessFunctions() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-multi-serverless";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create route with multiple Lua functions in serverless-post-function
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/multi-serverless/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 1') end",
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 2') end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithServerlessPostFunctionAndOtherPlugins() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-serverless-combo";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create route with serverless-post-function combined with other plugins
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-combo/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put("prometheus", Map.of());
    plugins.put(
        "response-rewrite", Map.of("headers", Map.of("set", Map.of("X-Processed-By", "apisix"))));
    plugins.put(
        "serverless-post-function",
        Map.of(
            "phase",
            "log",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.log(ngx.INFO, 'Request processed: ' .. ngx.var.uri) end")));
    routeConfig.put("plugins", plugins);

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPostFunction() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-invalid-lua-post";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with INVALID Lua syntax in serverless-post-function
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-lua-post/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of("phase", "log", "functions", List.of("this is not valid lua syntax !!!"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    // APISIX should reject invalid Lua with HTTP 400 → FatalAdapterException
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }

  // ============== SERVERLESS-PRE-FUNCTION PLUGIN TESTS ==============

  @Test
  void createRouteWithServerlessPreFunction() throws Exception {
    // First create an upstream that the route will reference
    String upstreamId = "test-upstream-serverless-pre";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with serverless-pre-function plugin (rewrite phase)
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-pre/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Request-ID', ngx.var.request_id) end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithServerlessPreFunctionAccessPhase() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-serverless-pre-access";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with serverless-pre-function using access phase
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/pre-access/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "access",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Pre-access check passed') end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void updateRouteAddServerlessPreFunction() throws Exception {
    String routeId = "test-route-add-serverless-pre";

    // First create an upstream
    String upstreamId = "test-upstream-for-add-serverless-pre";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create initial route without serverless-pre-function
    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-serverless-pre/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    // Update route to add serverless-pre-function plugin
    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-serverless-pre/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Added-Via', 'UPDATE') end"))));

    ConfigEvent event =
        createRouteConfigEvent("routes/" + routeId, Operation.UPDATE, updatedRouteConfig);

    adapter.processConfigEvent(Topics.ROUTE_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route);
              JsonNode plugins = route.get("value").get("plugins");
              assertNotNull(plugins);
              assertTrue(plugins.has("serverless-pre-function"));
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithMultipleServerlessPreFunctions() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-multi-serverless-pre";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create route with multiple Lua functions in serverless-pre-function
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/multi-serverless-pre/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-1', 'value1') end",
                    "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-2', 'value2') end"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithServerlessPreAndPostFunctions() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-pre-and-post";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create route with both serverless-pre-function and serverless-post-function
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/pre-and-post/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put(
        "serverless-pre-function",
        Map.of(
            "phase",
            "rewrite",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.req.set_header('X-Request-Start', ngx.now()) end")));
    plugins.put(
        "serverless-post-function",
        Map.of(
            "phase",
            "log",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.log(ngx.INFO, 'Request completed: ' .. ngx.var.uri) end")));
    routeConfig.put("plugins", plugins);

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPreFunction() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-invalid-lua-pre";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with INVALID Lua syntax in serverless-pre-function
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-lua-pre/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of("phase", "rewrite", "functions", List.of("this is not valid lua syntax !!!"))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    // APISIX should reject invalid Lua with HTTP 400 → FatalAdapterException
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }

  // ============== RESPONSE-REWRITE PLUGIN TESTS ==============

  @Test
  void createRouteWithResponseRewriteSetHeaders() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-set";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin using "set" headers operation
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-set/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "headers",
                Map.of(
                    "set",
                    Map.of(
                        "X-Server-Id", "server-1",
                        "X-Environment", "production")))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithResponseRewriteRemoveHeaders() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-remove";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin to remove headers
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-remove/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of("headers", Map.of("remove", List.of("X-Internal-Header", "X-Debug-Info")))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithResponseRewriteStatusCode() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-status";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin to override status code
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-status/*");
    routeConfig.put("methods", List.of("POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("response-rewrite", Map.of("status_code", 201)));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithResponseRewriteBody() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-body";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin to replace response body
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-body/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of("response-rewrite", Map.of("body", "{\"status\":\"ok\",\"processed\":true}")));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithResponseRewriteBodyBase64() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-base64";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin using base64 encoded body
    // Base64 of: {"result":"encoded"}
    String base64Body = "eyJyZXN1bHQiOiJlbmNvZGVkIn0=";
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-base64/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins", Map.of("response-rewrite", Map.of("body", base64Body, "body_base64", true)));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithResponseRewriteFilters() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-filters";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin using Lua-based body filters
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-filters/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "filters",
                List.of(
                    Map.of("regex", "old_text", "replace", "new_text"),
                    Map.of("regex", "internal_value", "replace", "public_value")))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithResponseRewriteVars() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-vars";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin using APISIX variables
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-vars/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "headers",
                Map.of(
                    "set",
                    Map.of(
                        "X-Upstream-Status", "$upstream_status",
                        "X-Request-Id", "$request_id")))));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void updateRouteAddResponseRewrite() throws Exception {
    String routeId = "test-route-add-response-rewrite";

    // First create an upstream
    String upstreamId = "test-upstream-for-add-response-rewrite";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create initial route without response-rewrite
    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-response-rewrite/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    // Update route to add response-rewrite plugin
    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-response-rewrite/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "response-rewrite", Map.of("headers", Map.of("set", Map.of("X-Added-Via", "UPDATE")))));

    ConfigEvent event =
        createRouteConfigEvent("routes/" + routeId, Operation.UPDATE, updatedRouteConfig);

    adapter.processConfigEvent(Topics.ROUTE_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route);
              JsonNode plugins = route.get("value").get("plugins");
              assertNotNull(plugins);
              assertTrue(plugins.has("response-rewrite"));
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithResponseRewriteFullConfig() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-response-rewrite-full";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with response-rewrite plugin combining headers, status, and body
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-full/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> responseRewriteConfig = new HashMap<>();
    responseRewriteConfig.put("status_code", 200);
    responseRewriteConfig.put(
        "headers",
        Map.of(
            "set", Map.of("X-Processed", "true", "Content-Type", "application/json"),
            "remove", List.of("X-Internal", "X-Debug")));
    responseRewriteConfig.put("body", "{\"result\":\"success\",\"processed\":true}");
    routeConfig.put("plugins", Map.of("response-rewrite", responseRewriteConfig));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
            });
  }

  @Test
  void createRouteWithInvalidResponseRewriteConfig() throws Exception {
    // First create an upstream
    String upstreamId = "test-upstream-invalid-response-rewrite";
    Map<String, Object> upstreamConfig =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1));
    createUpstreamDirectly(upstreamId, upstreamConfig);

    // Create a route with INVALID response-rewrite config (invalid status_code)
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-response-rewrite/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    // status_code must be between 200-598, using 999 should be invalid
    routeConfig.put("plugins", Map.of("response-rewrite", Map.of("status_code", 999)));

    ConfigEvent event = createRouteConfigEvent("routes", Operation.CREATE, routeConfig);

    // APISIX should reject invalid status_code with HTTP 400 → FatalAdapterException
    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }

  // ============== HELPER METHODS ==============

  private void createRouteDirectly(String routeId, Map<String, Object> config) throws Exception {
    String url = adminApiUrl + "/apisix/admin/routes/" + routeId;
    String json = objectMapper.writeValueAsString(config);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("X-API-KEY", ADMIN_API_KEY)
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(30))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException(
          "Failed to create route directly. Status: "
              + response.statusCode()
              + ", Body: "
              + response.body());
    }

    await()
        .atMost(5, SECONDS)
        .pollInterval(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route, "Route should be created");
            });
  }

  private JsonNode getRouteFromApisix(String routeId) throws Exception {
    String url = adminApiUrl + "/apisix/admin/routes/" + routeId;

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("X-API-KEY", ADMIN_API_KEY)
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 404) {
      throw new RuntimeException("Route not found: " + routeId);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException(
          "Failed to get route. Status: " + response.statusCode() + ", Body: " + response.body());
    }

    return objectMapper.readTree(response.body());
  }

  private void waitForApisixReady(String adminApiUrl) {
    await()
        .atMost(60, SECONDS)
        .pollInterval(2, SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(URI.create(adminApiUrl + "/apisix/admin/upstreams"))
                      .header("X-API-KEY", ADMIN_API_KEY)
                      .GET()
                      .timeout(Duration.ofSeconds(5))
                      .build();

              HttpResponse<String> response =
                  HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

              assertEquals(200, response.statusCode());
            });
  }

  private void createUpstreamDirectly(String upstreamId, Map<String, Object> config)
      throws Exception {
    String url = adminApiUrl + "/apisix/admin/upstreams/" + upstreamId;
    String json = objectMapper.writeValueAsString(config);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", Constants.CONTENT_TYPE_JSON)
            .header("X-API-KEY", ADMIN_API_KEY)
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofSeconds(30))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException(
          "Failed to create upstream directly. Status: "
              + response.statusCode()
              + ", Body: "
              + response.body());
    }

    await()
        .atMost(5, SECONDS)
        .pollInterval(500, java.util.concurrent.TimeUnit.MILLISECONDS)
        .untilAsserted(
            () -> {
              JsonNode upstream = getUpstreamFromApisix(upstreamId);
              assertNotNull(upstream, "Upstream should be created");
            });
  }

  private JsonNode getUpstreamFromApisix(String upstreamId) throws Exception {
    String url = adminApiUrl + "/apisix/admin/upstreams/" + upstreamId;

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("X-API-KEY", ADMIN_API_KEY)
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 404) {
      throw new RuntimeException("Upstream not found: " + upstreamId);
    }

    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new RuntimeException(
          "Failed to get upstream. Status: "
              + response.statusCode()
              + ", Body: "
              + response.body());
    }

    return objectMapper.readTree(response.body());
  }

  private ConfigEvent createConfigEvent(
      String targetResource, Operation operation, Map<String, Object> value) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");

    ApisixConfigValue apisixValue = new ApisixConfigValue(value);
    Config config = new Config(targetResource, apisixValue);
    Payload payload = new Payload("apisix", targetResource, operation, config);
    return new ConfigEvent(metadata, payload);
  }

  private ConfigEvent createRouteConfigEvent(
      String targetResource, Operation operation, Map<String, Object> value) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");

    RouteConfigValue routeValue = new RouteConfigValue(value);
    Config config = new Config(targetResource, routeValue);
    Payload payload = new Payload("apisix", targetResource, operation, config);
    return new ConfigEvent(metadata, payload);
  }

  static class TestEventPublisher implements EventPublisher {
    private final List<ConfigResultEvent> publishedEvents =
        Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(String topic, ConfigResultEvent event) {
      publishedEvents.add(event);
    }

    public List<ConfigResultEvent> getPublishedEvents() {
      return new ArrayList<>(publishedEvents);
    }

    @Override
    public String getName() {
      return "test";
    }

    @Override
    public void initialize(ApplicationConfig config, ConfigAdapter adapter) {}
  }
}

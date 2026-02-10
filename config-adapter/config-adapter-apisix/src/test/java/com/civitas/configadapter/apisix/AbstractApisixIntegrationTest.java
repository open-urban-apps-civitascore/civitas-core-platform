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

import com.civitas.configadapter.Constants;
import com.civitas.configadapter.Topics;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for ApisixAdapter integration tests. Uses the singleton container pattern so etcd and
 * APISIX start only once per JVM.
 */
abstract class AbstractApisixIntegrationTest {

  protected static final String ADMIN_API_KEY = "edd1c9f034335f136f87ad84b625c8f1";
  protected static final Network NETWORK = Network.newNetwork();

  @SuppressWarnings("resource")
  protected static final GenericContainer<?> ETCD;

  @SuppressWarnings("resource")
  protected static final GenericContainer<?> APISIX;

  static {
    ETCD =
        new GenericContainer<>(DockerImageName.parse("quay.io/coreos/etcd:v3.6.6"))
            .withNetwork(NETWORK)
            .withNetworkAliases("etcd")
            .withExposedPorts(2379, 2380)
            .withEnv("ETCD_ENABLE_V2", "true")
            .withEnv("ALLOW_NONE_AUTHENTICATION", "yes")
            .withEnv("ETCD_ADVERTISE_CLIENT_URLS", "http://etcd:2379")
            .withEnv("ETCD_LISTEN_CLIENT_URLS", "http://0.0.0.0:2379")
            .waitingFor(Wait.forLogMessage(".*ready to serve client requests.*", 1))
            .withReuse(false);
    ETCD.start();

    APISIX =
        new GenericContainer<>(DockerImageName.parse("apache/apisix:3.14.0-debian"))
            .withNetwork(NETWORK)
            .withNetworkAliases("apisix")
            .dependsOn(ETCD)
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
    APISIX.start();
  }

  protected ApisixAdapter adapter;
  protected TestEventPublisher eventPublisher;
  protected HttpClient httpClient;
  protected ObjectMapper objectMapper;
  protected String adminApiUrl;

  @BeforeEach
  void setUp() {
    adminApiUrl = "http://" + APISIX.getHost() + ":" + APISIX.getMappedPort(9180);

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

  // ---- Convenience helpers ----

  protected String createDefaultUpstream(String suffix) throws Exception {
    String id = "test-upstream-" + suffix;
    createUpstreamDirectly(id, ApisixTestFixtures.defaultUpstreamConfig());
    return id;
  }

  protected void awaitSuccessResult() {
    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              assertEquals(
                  ConfigResultEvent.Status.SUCCESS,
                  eventPublisher.getPublishedEvents().getFirst().status());
            });
  }

  // ---- Direct APISIX Admin API helpers ----

  protected void createUpstreamDirectly(String upstreamId, Map<String, Object> config)
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

  protected JsonNode getUpstreamFromApisix(String upstreamId) throws Exception {
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

  protected void createRouteDirectly(String routeId, Map<String, Object> config) throws Exception {
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

  protected JsonNode getRouteFromApisix(String routeId) throws Exception {
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

  private void waitForApisixReady(String adminUrl) {
    await()
        .atMost(60, SECONDS)
        .pollInterval(2, SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              HttpRequest request =
                  HttpRequest.newBuilder()
                      .uri(URI.create(adminUrl + "/apisix/admin/upstreams"))
                      .header("X-API-KEY", ADMIN_API_KEY)
                      .GET()
                      .timeout(Duration.ofSeconds(5))
                      .build();

              HttpResponse<String> response =
                  HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

              assertEquals(200, response.statusCode());
            });
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

/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.configadapter.apisix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
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
          .waitingFor(Wait.forListeningPort())
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
          .waitingFor(Wait.forListeningPort())
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
            Topics.BACKEND_DELETED.toString()));
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
  void shouldCreateUpstream() throws Exception {
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
  void shouldUpdateUpstream() throws Exception {
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
  void shouldDeleteUpstream() throws Exception {
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
  void shouldHandleInvalidUpstreamConfiguration() throws Exception {
    Map<String, Object> invalidConfig = Map.of("type", "invalid-type");

    ConfigEvent event = createConfigEvent("upstreams", Operation.CREATE, invalidConfig);

    adapter.processConfigEvent(Topics.BACKEND_CREATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              assertEquals(1, eventPublisher.getPublishedEvents().size());
              ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
              assertEquals(ConfigResultEvent.Status.FAILURE, resultEvent.status());
              assertNotNull(resultEvent.errorCode());
            });
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
            .header("Content-Type", "application/json")
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

  private ConfigEvent createConfigEvent(String targetResource, Operation operation, Object value) {
    return createConfigEventWithCorrelation(
        targetResource, operation, value, UUID.randomUUID().toString());
  }

  private ConfigEvent createConfigEventWithCorrelation(
      String targetResource, Operation operation, Object value, String correlationId) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            correlationId,
            "1.0",
            "result.topic");

    Config config = new Config(targetResource, value);

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

    public void clear() {
      publishedEvents.clear();
    }

    @Override
    public void initialize(
        com.civitas.configadapter.configuration.ApplicationConfig config,
        com.civitas.configadapter.adapter.ConfigAdapter adapter) {}
  }
}

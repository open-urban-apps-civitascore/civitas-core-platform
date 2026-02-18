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
package com.civitas.configadapter.frost;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.frost.FrostConfigValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration2.MapConfiguration;
import org.glassfish.jersey.client.HttpUrlConnectorProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for FrostAdapter using Testcontainers with a real FROST-Server and PostGIS
 * database. Tests FROST Projects extension: Project CRUD and project-scoped entity creation.
 */
@Testcontainers
class FrostProjectsIntegrationTest {

  static Network network = Network.newNetwork();

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> postgis =
      new GenericContainer<>(DockerImageName.parse("postgis/postgis:16-3.4-alpine"))
          .withNetwork(network)
          .withNetworkAliases("database")
          .withEnv("POSTGRES_DB", "sensorthings")
          .withEnv("POSTGRES_USER", "sensorthings")
          .withEnv("POSTGRES_PASSWORD", "ChangeMe")
          .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> frost =
      new GenericContainer<>(DockerImageName.parse("hylkevds/frost-http-projects:latest"))
          .withNetwork(network)
          .withExposedPorts(8080)
          .dependsOn(postgis)
          .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
          .withEnv("plugins_modelLoader_enable", "true")
          .withEnv("plugins_multiDatastream_enable", "false")
          .withEnv("plugins_actuation_enable", "false")
          .withEnv("persistence_db_driver", "org.postgresql.Driver")
          .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
          .withEnv("persistence_db_username", "sensorthings")
          .withEnv("persistence_db_password", "ChangeMe")
          .withEnv("persistence_autoUpdateDatabase", "true")
          .withEnv("plugins_modelLoader_securityPath", "")
          .withEnv("plugins_modelLoader_securityFiles", "")
          .waitingFor(
              Wait.forHttp("/FROST-Server/v1.1/Projects")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  private FrostAdapter adapter;
  private TestEventPublisher eventPublisher;
  private String frostBaseUrl;
  private Client httpClient;
  private ObjectMapper objectMapper;

  @AfterAll
  static void tearDownContainers() {
    network.close();
  }

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();
    objectMapper = new ObjectMapper();

    waitForFrostReady(frostBaseUrl);

    Map<String, Object> props = new HashMap<>();
    props.put("frost.url", frostBaseUrl);
    props.put("frost.api.key", "test-api-key");
    props.put("frost.api.key.header", "X-API-Key");
    props.put(
        "frost.topics",
        String.join(
            ",",
            Topics.FROST_PROJECT_CREATED.toString(),
            Topics.FROST_PROJECT_UPDATED.toString(),
            Topics.FROST_PROJECT_DELETED.toString(),
            Topics.THING_CREATED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    adapter = new FrostAdapter();
    adapter.initialize(config);

    // Replace the adapter's client with one that supports HTTP PATCH on JDK 21+
    Client patchCapableClient =
        ClientBuilder.newBuilder()
            .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    adapter.setClient(patchCapableClient);

    eventPublisher = new TestEventPublisher();
    adapter.setEventPublisher(eventPublisher);
  }

  @AfterEach
  void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
    if (httpClient != null) {
      httpClient.close();
    }
  }

  @Test
  void createProjectReturnsSuccessAndEntityExists() throws Exception {
    Map<String, Object> projectData =
        Map.of(
            "name", "Smart City Project",
            "description", "A test project for smart city sensors");

    ConfigEvent event = createConfigEvent(Operation.CREATE, "Projects", projectData);

    adapter.processConfigEvent(Topics.FROST_PROJECT_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        result.status(),
        () -> "Create failed: " + result.message() + " (errorCode: " + result.errorCode() + ")");
    assertNotNull(result.resourceId());

    JsonNode entity = getEntityFromFrost("Projects", result.resourceId());
    assertEquals("Smart City Project", entity.get("name").asText());
    assertEquals("A test project for smart city sensors", entity.get("description").asText());
  }

  @Test
  void updateProjectReturnsSuccessAndEntityIsModified() throws Exception {
    String projectId =
        createEntityDirectly(
            "Projects",
            Map.of("name", "Original Project", "description", "Original project description"));

    Map<String, Object> updateData = Map.of("description", "Updated project description");
    ConfigEvent event = createConfigEvent(Operation.UPDATE, "Projects/" + projectId, updateData);

    adapter.processConfigEvent(Topics.FROST_PROJECT_UPDATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        result.status(),
        () -> "Update failed: " + result.message() + " (errorCode: " + result.errorCode() + ")");
    assertEquals(projectId, result.resourceId());

    JsonNode entity = getEntityFromFrost("Projects", projectId);
    assertEquals("Updated project description", entity.get("description").asText());
    assertEquals("Original Project", entity.get("name").asText());
  }

  @Test
  void deleteProjectReturnsSuccessAndEntityIsRemoved()
      throws FatalAdapterException, RetryableAdapterException {
    String projectId =
        createEntityDirectly(
            "Projects", Map.of("name", "Project To Delete", "description", "Will be deleted"));

    ConfigEvent event = createConfigEvent(Operation.DELETE, "Projects/" + projectId, null);

    adapter.processConfigEvent(Topics.FROST_PROJECT_DELETED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());

    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void createThingScopedToProjectReturnsSuccessAndEntityExists() throws Exception {
    String projectId =
        createEntityDirectly(
            "Projects",
            Map.of("name", "Scoped Project", "description", "Project for scoped thing test"));

    Map<String, Object> thingData =
        Map.of(
            "name", "Project-Scoped Thing",
            "description", "A thing created within a project scope");

    ConfigEvent event =
        createConfigEvent(Operation.CREATE, "Projects/" + projectId + "/Things", thingData);

    adapter.processConfigEvent(Topics.THING_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertNotNull(result.resourceId());

    JsonNode entity = getEntityFromFrost("Things", result.resourceId());
    assertEquals("Project-Scoped Thing", entity.get("name").asText());
  }

  // --- Helper methods ---

  private String createEntityDirectly(String entityType, Map<String, Object> data) {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(entityType)
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(data))) {
      String body = response.readEntity(String.class);
      assertEquals(
          201, response.getStatus(), "Failed to create " + entityType + " directly: " + body);
      String locationHeader = response.getHeaderString("Location");
      int start = locationHeader.lastIndexOf('(');
      int end = locationHeader.lastIndexOf(')');
      String id = locationHeader.substring(start + 1, end);
      return id.replace("'", "");
    }
  }

  private JsonNode getEntityFromFrost(String entityType, String id) throws Exception {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(entityType + "(" + id + ")")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(200, response.getStatus(), "Entity not found: " + entityType + "(" + id + ")");
      String body = response.readEntity(String.class);
      return objectMapper.readTree(body);
    }
  }

  private void waitForFrostReady(String baseUrl) {
    await()
        .atMost(60, SECONDS)
        .pollInterval(2, SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (Response response =
                  httpClient.target(baseUrl).path("Projects").request().get()) {
                assertEquals(200, response.getStatus());
              }
            });
  }

  private ConfigEvent createConfigEvent(
      Operation operation, String targetResource, Map<String, Object> configValue) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "integration-test",
            UUID.randomUUID().toString(),
            "1.0",
            "test-result-topic");

    FrostConfigValue frostValue = FrostTestFixtures.buildFrostConfigValue(configValue);
    Payload payload = new Payload("frost", targetResource, operation, new Config(null, frostValue));
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

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for FrostAdapter using Testcontainers with a real FROST-Server and PostGIS
 * database. Tests FROST Projects extension: Project CRUD and project-scoped entity creation.
 */
class FrostProjectsIT extends AbstractFrostIT {

  private FrostAdapter adapter;
  private TestEventPublisher eventPublisher;
  private String frostBaseUrl;
  private OkHttpClient httpClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + FROST.getHost() + ":" + FROST.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = new OkHttpClient();
    objectMapper = new ObjectMapper();

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

    eventPublisher = new TestEventPublisher();
    adapter.setEventPublisher(eventPublisher);
  }

  @AfterEach
  void tearDown() {
    if (adapter != null) {
      adapter.close();
    }
    if (httpClient != null) {
      httpClient.dispatcher().executorService().shutdown();
      httpClient.connectionPool().evictAll();
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
  void deleteProjectReturnsSuccessAndEntityIsRemoved() throws Exception {
    String projectId =
        createEntityDirectly(
            "Projects", Map.of("name", "Project To Delete", "description", "Will be deleted"));

    ConfigEvent event = createConfigEvent(Operation.DELETE, "Projects/" + projectId, null);

    adapter.processConfigEvent(Topics.FROST_PROJECT_DELETED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());

    Request request = jsonRequest("Projects(" + projectId + ")").get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      assertEquals(404, response.code());
    }
  }

  @Test
  void createProjectTwiceWithSameNameReturnsSuccessForBoth() throws Exception {
    String projectName = "Idempotent-" + UUID.randomUUID();
    Map<String, Object> projectData = Map.of("name", projectName, "description", "");

    ConfigEvent firstEvent = createConfigEvent(Operation.CREATE, "Projects", projectData);
    adapter.processConfigEvent(Topics.FROST_PROJECT_CREATED.toString(), firstEvent);

    ConfigEvent secondEvent = createConfigEvent(Operation.CREATE, "Projects", projectData);
    adapter.processConfigEvent(Topics.FROST_PROJECT_CREATED.toString(), secondEvent);

    List<ConfigResultEvent> results = eventPublisher.getPublishedEvents();
    assertEquals(2, results.size());
    assertEquals(ConfigResultEvent.Status.SUCCESS, results.get(0).status());
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        results.get(1).status(),
        () -> "Second create (idempotent) failed: " + results.get(1).message());
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

  private String createEntityDirectly(String entityType, Map<String, Object> data)
      throws Exception {
    RequestBody body =
        RequestBody.create(
            objectMapper.writeValueAsString(data), MediaType.get("application/json"));
    Request request = jsonRequest(entityType).post(body).build();
    try (Response response = httpClient.newCall(request).execute()) {
      String responseBody = response.body().string();
      assertEquals(
          201, response.code(), "Failed to create " + entityType + " directly: " + responseBody);
      String locationHeader = response.header("Location");
      int start = locationHeader.lastIndexOf('(');
      int end = locationHeader.lastIndexOf(')');
      String id = locationHeader.substring(start + 1, end);
      return id.replace("'", "");
    }
  }

  private JsonNode getEntityFromFrost(String entityType, String id) throws Exception {
    Request request = jsonRequest(entityType + "(" + id + ")").get().build();
    try (Response response = httpClient.newCall(request).execute()) {
      assertEquals(200, response.code(), "Entity not found: " + entityType + "(" + id + ")");
      return objectMapper.readTree(response.body().string());
    }
  }

  private Request.Builder jsonRequest(String path) {
    HttpUrl url = HttpUrl.get(frostBaseUrl).newBuilder().addPathSegments(path).build();
    return new Request.Builder().url(url).header("Accept", "application/json");
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

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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.frost.FrostConfigValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for FrostAdapter using Testcontainers with a real FROST-Server and PostGIS
 * database. Tests standard FROST entity CRUD operations: Things, Locations, and Sensors.
 */
class FrostAdapterIT extends AbstractFrostIT {

  private FrostAdapter adapter;
  private TestEventPublisher eventPublisher;
  private String frostBaseUrl;
  private Client httpClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + FROST.getHost() + ":" + FROST.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();
    objectMapper = new ObjectMapper();

    Map<String, Object> props = new HashMap<>();
    props.put("frost.url", frostBaseUrl);
    props.put("frost.api.key", "test-api-key");
    props.put("frost.api.key.header", "X-API-Key");
    props.put(
        "frost.topics",
        String.join(
            ",",
            Topics.THING_CREATED.toString(),
            Topics.THING_UPDATED.toString(),
            Topics.THING_DELETED.toString(),
            Topics.LOCATION_CREATED.toString(),
            Topics.SENSOR_CREATED.toString()));
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
  void createThingReturnsSuccessAndEntityExists() throws Exception {
    Map<String, Object> thingData =
        Map.of(
            "name", "Integration Test Thing",
            "description", "A thing created by integration test");

    ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", thingData);

    adapter.processConfigEvent(Topics.THING_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertNotNull(result.resourceId());

    JsonNode entity = getEntityFromFrost("Things", result.resourceId());
    assertEquals("Integration Test Thing", entity.get("name").asText());
    assertEquals("A thing created by integration test", entity.get("description").asText());
  }

  @Test
  void createLocationReturnsSuccessAndEntityExists() throws Exception {
    Map<String, Object> locationData =
        Map.of(
            "name", "Integration Test Location",
            "description", "A test location with GeoJSON",
            "encodingType", "application/geo+json",
            "location", Map.of("type", "Point", "coordinates", List.of(8.4037, 49.0069)));

    ConfigEvent event = createConfigEvent(Operation.CREATE, "Locations", locationData);

    adapter.processConfigEvent(Topics.LOCATION_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertNotNull(result.resourceId());

    JsonNode entity = getEntityFromFrost("Locations", result.resourceId());
    assertEquals("Integration Test Location", entity.get("name").asText());
    assertEquals("application/geo+json", entity.get("encodingType").asText());
  }

  @Test
  void createSensorReturnsSuccessAndEntityExists() throws Exception {
    Map<String, Object> sensorData =
        Map.of(
            "name", "Integration Test Sensor",
            "description", "A test sensor",
            "encodingType", "text/html",
            "metadata", "https://example.com/sensor");

    ConfigEvent event = createConfigEvent(Operation.CREATE, "Sensors", sensorData);

    adapter.processConfigEvent(Topics.SENSOR_CREATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertNotNull(result.resourceId());

    JsonNode entity = getEntityFromFrost("Sensors", result.resourceId());
    assertEquals("Integration Test Sensor", entity.get("name").asText());
  }

  @Test
  void updateThingReturnsSuccessAndEntityIsModified() throws Exception {
    String thingId =
        createEntityDirectly(
            "Things", Map.of("name", "Original Thing", "description", "Original description"));

    Map<String, Object> updateData = Map.of("description", "Updated description");
    ConfigEvent event = createConfigEvent(Operation.UPDATE, "Things/" + thingId, updateData);

    adapter.processConfigEvent(Topics.THING_UPDATED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(
        ConfigResultEvent.Status.SUCCESS,
        result.status(),
        () -> "Update failed: " + result.message() + " (errorCode: " + result.errorCode() + ")");
    assertEquals(thingId, result.resourceId());

    JsonNode entity = getEntityFromFrost("Things", thingId);
    assertEquals("Updated description", entity.get("description").asText());
    assertEquals("Original Thing", entity.get("name").asText());
  }

  @Test
  void deleteThingReturnsSuccessAndEntityIsRemoved()
      throws FatalAdapterException, RetryableAdapterException {
    String thingId =
        createEntityDirectly(
            "Things", Map.of("name", "Thing To Delete", "description", "Will be deleted"));

    ConfigEvent event = createConfigEvent(Operation.DELETE, "Things/" + thingId, null);

    adapter.processConfigEvent(Topics.THING_DELETED.toString(), event);

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent result = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());

    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path("Things(" + thingId + ")")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(404, response.getStatus());
    }
  }

  @Test
  void createWithInvalidDataThrowsFatalException() {
    ConfigEvent event = createConfigEvent(Operation.CREATE, "Things", Map.of());

    assertThrows(
        FatalAdapterException.class,
        () -> adapter.processConfigEvent(Topics.THING_CREATED.toString(), event));
  }

  // --- Helper methods ---

  private String createEntityDirectly(String entityType, Map<String, Object> data) {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(entityType)
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(data))) {
      assertEquals(201, response.getStatus(), "Failed to create " + entityType + " directly");
      String locationHeader = response.getHeaderString("Location");
      int start = locationHeader.lastIndexOf('(');
      int end = locationHeader.lastIndexOf(')');
      return locationHeader.substring(start + 1, end);
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

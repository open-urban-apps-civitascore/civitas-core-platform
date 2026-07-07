/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.frost;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
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
 * Integration tests for {@link FrostSagaHandler} using a real FROST-Server in Docker.
 *
 * <p>These tests document a known FROST quirk: instead of returning HTTP 409 Conflict on a
 * duplicate project name, FROST returns HTTP 500 with body {@code {"message":"Failed to store
 * data."}}. The handler must treat this as an idempotent success.
 */
class FrostSagaHandlerIntegrationTest extends AbstractFrostIntegrationTest {

  private FrostSagaHandler handler;
  private Client httpClient;
  private String frostBaseUrl;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + FROST.getHost() + ":" + FROST.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();
    objectMapper = new ObjectMapper();

    waitForFrostReady(frostBaseUrl);

    Map<String, Object> props = new HashMap<>();
    props.put("frost.url", frostBaseUrl);
    props.put("frost.api.key", "test-api-key");
    props.put("frost.api.key.header", "X-API-Key");
    AppConfig config = new AppConfig(new MapConfiguration(props));

    handler = new FrostSagaHandler();
    handler.initialize(config);

    Client patchCapableClient =
        ClientBuilder.newBuilder()
            .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    handler.setTestClient(patchCapableClient);
  }

  @AfterEach
  void tearDown() {
    if (handler != null) {
      handler.close();
    }
    if (httpClient != null) {
      httpClient.close();
    }
  }

  /**
   * Documents that FROST returns HTTP 500 (not 409) when a project with the same name already
   * exists. This is a quirk of the FROST Projects plugin that the handler must work around.
   */
  @Test
  void frostReturnsHttp500WhenCreatingProjectWithDuplicateName() {
    String projectName = "Duplicate-" + UUID.randomUUID();
    Map<String, Object> projectBody = Map.of("name", projectName, "description", "");

    try (Response first =
        httpClient
            .target(frostBaseUrl)
            .path("Projects")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(projectBody))) {
      assertEquals(201, first.getStatus(), "First create should succeed");
    }

    try (Response second =
        httpClient
            .target(frostBaseUrl)
            .path("Projects")
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(projectBody))) {
      assertEquals(
          500,
          second.getStatus(),
          "FROST returns 500 (not 409) for duplicate project name — if this fails, FROST behaviour"
              + " changed and the idempotence workaround in FrostSagaHandler can be simplified");
    }
  }

  /**
   * CREATE_PROJECT must be idempotent: calling it a second time with the same name must succeed and
   * return the same projectId as the first call.
   *
   * <p>This test currently fails because FrostSagaHandler propagates the HTTP 500 from FROST as a
   * STEP_FAILED result instead of recognising it as a duplicate and returning the existing project.
   */
  @Test
  void createProjectIsIdempotentWhenProjectAlreadyExists() {
    String datasetName = "Idempotent-" + UUID.randomUUID();
    String datasetId = "ds-" + UUID.randomUUID();
    // Same datasetId on both calls → same unique FROST project name "name (datasetId)" → the second
    // call hits FROST's 500-duplicate and recovers to the SAME project (P1: recovery binds a
    // dataset
    // only to its OWN project).
    SagaCommandMessage command =
        new SagaCommandMessage(
            "EXECUTE_STEP",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "create-frost-project",
            "frost",
            "CREATE_PROJECT",
            Map.of(
                "datasetName",
                datasetName,
                "datasetId",
                datasetId,
                "description",
                "idempotence test"));

    SagaCommandResult firstResult = handler.handle(command);
    assertEquals(
        "STEP_COMPLETED",
        firstResult.type(),
        () -> "First CREATE_PROJECT failed: " + firstResult.error());

    String firstProjectId = (String) firstResult.resultData().get("projectId");

    SagaCommandResult secondResult = handler.handle(command);
    assertEquals(
        "STEP_COMPLETED",
        secondResult.type(),
        () -> "Second CREATE_PROJECT (idempotent retry) failed: " + secondResult.error());
    assertEquals(
        firstProjectId,
        secondResult.resultData().get("projectId"),
        "Idempotent retry must return the same projectId");
  }

  /**
   * FROST does not cascade project deletion: {@code DELETE /Projects(n)} alone leaves the member
   * Things, Datastreams and Observations at server root, unreachable through any project. The
   * handler must therefore delete the project's Things first (which FROST cascades to their
   * Datastreams and Observations) — and must not touch any other project's data while doing so.
   */
  @Test
  void deleteProjectRemovesItsThingsDatastreamsAndObservationsButSparesOtherProjects()
      throws Exception {
    String projectA = createProject("Cascade-A-" + UUID.randomUUID());
    String projectB = createProject("Cascade-B-" + UUID.randomUUID());

    String thingA1 = createThingWithDatastreamAndObservations(projectA, "cascade-thing-a1");
    String thingA2 = createPlainThing(projectA, "cascade-thing-a2");
    String thingB1 = createThingWithDatastreamAndObservations(projectB, "cascade-thing-b1");

    List<String> datastreamsA = collectIds("Things(" + thingA1 + ")/Datastreams");
    assertFalse(datastreamsA.isEmpty(), "seeding must have created a Datastream for project A");
    List<String> observationsA =
        collectIds("Datastreams(" + datastreamsA.getFirst() + ")/Observations");
    assertFalse(observationsA.isEmpty(), "seeding must have created Observations for project A");

    List<String> datastreamsB = collectIds("Things(" + thingB1 + ")/Datastreams");
    List<String> observationsB =
        collectIds("Datastreams(" + datastreamsB.getFirst() + ")/Observations");

    SagaCommandResult result = handler.handle(deleteProjectCommand("EXECUTE_STEP", projectA));
    assertEquals("STEP_COMPLETED", result.type(), () -> "DELETE_PROJECT failed: " + result.error());

    // Project A's data must be gone at server root, not merely unlinked from the project.
    assertRootEntityStatus(404, "Projects(" + projectA + ")");
    assertRootEntityStatus(404, "Things(" + thingA1 + ")");
    assertRootEntityStatus(404, "Things(" + thingA2 + ")");
    for (String datastreamId : datastreamsA) {
      assertRootEntityStatus(404, "Datastreams(" + datastreamId + ")");
    }
    for (String observationId : observationsA) {
      assertRootEntityStatus(404, "Observations(" + observationId + ")");
    }

    // Project B's data must be untouched.
    assertRootEntityStatus(200, "Projects(" + projectB + ")");
    assertRootEntityStatus(200, "Things(" + thingB1 + ")");
    for (String datastreamId : datastreamsB) {
      assertRootEntityStatus(200, "Datastreams(" + datastreamId + ")");
    }
    for (String observationId : observationsB) {
      assertRootEntityStatus(200, "Observations(" + observationId + ")");
    }
  }

  /**
   * A DELETE_PROJECT compensation must be re-runnable: a retry against a project that was already
   * removed (or already emptied) must still succeed, otherwise a saga rollback could never complete
   * after a partial earlier cleanup.
   */
  @Test
  void deleteProjectCompensationSucceedsWhenRerunAfterProjectAlreadyRemoved() throws Exception {
    String projectId = createProject("Rerun-" + UUID.randomUUID());
    String thingId = createThingWithDatastreamAndObservations(projectId, "rerun-thing");
    List<String> datastreamIds = collectIds("Things(" + thingId + ")/Datastreams");

    SagaCommandMessage command = deleteProjectCommand("COMPENSATE_STEP", projectId);

    SagaCommandResult first = handler.handle(command);
    assertEquals(
        "COMPENSATION_COMPLETED",
        first.type(),
        () -> "First DELETE_PROJECT compensation failed: " + first.error());
    // The rollback of a failed release must leave no data behind either.
    assertRootEntityStatus(404, "Projects(" + projectId + ")");
    assertRootEntityStatus(404, "Things(" + thingId + ")");
    for (String datastreamId : datastreamIds) {
      assertRootEntityStatus(404, "Datastreams(" + datastreamId + ")");
    }

    SagaCommandResult second = handler.handle(command);
    assertEquals(
        "COMPENSATION_COMPLETED",
        second.type(),
        () -> "Re-run DELETE_PROJECT compensation failed: " + second.error());
  }

  private SagaCommandMessage deleteProjectCommand(String type, String projectId) {
    return new SagaCommandMessage(
        type,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "delete-frost-project",
        "frost",
        "DELETE_PROJECT",
        Map.of("projectId", projectId));
  }

  private String createProject(String name) {
    return createEntity("Projects", Map.of("name", name, "description", "cascade delete test"));
  }

  private String createPlainThing(String projectId, String name) {
    return createEntity(
        "Projects(" + projectId + ")/Things",
        Map.of("name", name, "description", "thing without sensor data"));
  }

  /**
   * Seeds a Thing in the given project carrying a deep-inserted Datastream (with Sensor and
   * ObservedProperty) and two Observations. The Location is required so FROST can auto-generate the
   * FeatureOfInterest for the Observations.
   */
  private String createThingWithDatastreamAndObservations(String projectId, String name) {
    Map<String, Object> thing =
        Map.of(
            "name",
            name,
            "description",
            "thing with sensor data",
            "Locations",
            List.of(
                Map.of(
                    "name",
                    name + "-location",
                    "description",
                    "test location",
                    "encodingType",
                    "application/geo+json",
                    "location",
                    Map.of("type", "Point", "coordinates", List.of(8.4, 49.0)))),
            "Datastreams",
            List.of(
                Map.of(
                    "name", name + "-datastream",
                    "description", "test datastream",
                    "observationType",
                        "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
                    "unitOfMeasurement",
                        Map.of("name", "degree Celsius", "symbol", "°C", "definition", "ucum:Cel"),
                    "Sensor",
                        Map.of(
                            "name", name + "-sensor",
                            "description", "test sensor",
                            "encodingType", "application/pdf",
                            "metadata", "n/a"),
                    "ObservedProperty",
                        Map.of(
                            "name", name + "-temperature",
                            "definition", "http://example.org/temperature",
                            "description", "test observed property"),
                    "Observations",
                        List.of(
                            Map.of("phenomenonTime", "2026-07-07T00:00:00Z", "result", 20.5),
                            Map.of("phenomenonTime", "2026-07-07T00:15:00Z", "result", 21.0)))));
    return createEntity("Projects(" + projectId + ")/Things", thing);
  }

  private String createEntity(String path, Map<String, Object> body) {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(path)
            .request(MediaType.APPLICATION_JSON)
            .post(Entity.json(body))) {
      String responseBody = response.readEntity(String.class);
      assertEquals(201, response.getStatus(), "Failed to create " + path + ": " + responseBody);
      String location = response.getHeaderString("Location");
      return location.substring(location.lastIndexOf('(') + 1, location.lastIndexOf(')'));
    }
  }

  private List<String> collectIds(String collectionPath) throws Exception {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(collectionPath)
            .queryParam("$select", "@iot.id")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(200, response.getStatus(), "Failed to list " + collectionPath);
      JsonNode value = objectMapper.readTree(response.readEntity(String.class)).get("value");
      List<String> ids = new ArrayList<>();
      value.forEach(node -> ids.add(node.get("@iot.id").asText()));
      return ids;
    }
  }

  private void assertRootEntityStatus(int expectedStatus, String entityPath) {
    try (Response response =
        httpClient
            .target(frostBaseUrl)
            .path(entityPath)
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(
          expectedStatus,
          response.getStatus(),
          () -> entityPath + " expected HTTP " + expectedStatus + " at server root");
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
}

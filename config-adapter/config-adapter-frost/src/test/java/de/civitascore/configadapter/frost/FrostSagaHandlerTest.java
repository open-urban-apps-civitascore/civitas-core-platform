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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.util.PayloadConverter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrostSagaHandlerTest {

  private MockWebServer server;

  @BeforeEach
  void startServer() throws IOException {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws IOException {
    server.close();
  }

  @Test
  @DisplayName("adapter() returns 'frost'")
  void shouldReturnFrostAdapterName() {
    try (FrostSagaHandler handler = createHandler()) {
      assertEquals("frost", handler.adapter());
    }
  }

  @Test
  @DisplayName("initialize throws when no auth is configured")
  void shouldThrowWhenNoAuthIsConfigured() {
    try (FrostSagaHandler h = new FrostSagaHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://frost:8080/v1.1");
      when(config.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
    }
  }

  @Test
  @DisplayName("initialize succeeds with basic auth and no API key")
  void shouldInitializeWithBasicAuthAndNoApiKey() {
    try (FrostSagaHandler h = new FrostSagaHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://frost:8080/v1.1");
      when(config.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");
      when(config.getProperty("frost.basic.auth.username")).thenReturn("admin");
      when(config.getProperty("frost.basic.auth.password")).thenReturn("secret");

      h.initialize(config);

      assertEquals("frost", h.adapter());
    }
  }

  @Nested
  @DisplayName("CREATE_PROJECT")
  class CreateProject {

    @Test
    @DisplayName("returns success with projectId and baseUrl")
    void shouldCreateProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Test Dataset", "description", "A test dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());
        assertEquals("create-project", result.stepId());
        assertEquals("42", result.resultData().get("projectId"));
        assertTrue(((String) result.resultData().get("baseUrl")).endsWith("/Projects(42)"));
        assertEquals("42", result.compensationData().get("projectId"));
        // A freshly created project flags created=true so its compensation may safely delete it.
        assertEquals(true, result.compensationData().get("created"));
      }
    }

    @Test
    @DisplayName(
        "names the FROST project '{datasetName} ({datasetId})' so same-named datasets stay isolated")
    void shouldNameProjectUniquelyWithDatasetId() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Foo", "datasetId", "ds-1")));

        server.takeRequest(); // the up-front lookup GET
        RecordedRequest postRequest = server.takeRequest();
        Map<String, Object> body = requestBodyAsMap(postRequest);
        assertEquals(
            "Foo (ds-1)",
            body.get("name"),
            "FROST project name must include datasetId — two datasets with the same display name"
                + " must get separate FROST projects (P1 data-isolation)");
      }
    }

    @Test
    @DisplayName("uses publicUrl in baseUrl when configured differently from serverUrl")
    void shouldUsePublicUrlInBaseUrl() {
      try (FrostSagaHandler handler = createHandlerWithPublicUrl("http://public-frost:80/v1.1")) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Test Dataset", "description", "A test dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(
            "http://public-frost:80/v1.1/Projects(42)", result.resultData().get("baseUrl"));
      }
    }

    @Test
    @DisplayName("reuses an existing project found by name and skips the POST (find-or-create)")
    void shouldReuseExistingProjectAndSkipPost() {
      try (FrostSagaHandler handler = createHandler()) {
        // Up-front lookup finds the dataset's own project → reuse it, never POST.
        server.enqueue(projectLookupFound(42));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("42", result.compensationData().get("projectId"));
        // Reuse must flag created=false so its compensation skips the destructive delete.
        assertEquals(false, result.compensationData().get("created"));
        assertEquals(1, server.getRequestCount());
      }
    }

    @Test
    @DisplayName(
        "recovers via the 500 'Failed to store data.' race guard when a concurrent create won and"
            + " the second lookup finds the project")
    void shouldReturnSuccessWhenFrostSignalsDuplicateAndProjectExists() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(
            new MockResponse.Builder()
                .code(500)
                .body("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}")
                .build());
        server.enqueue(projectLookupFound(42));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("42", result.compensationData().get("projectId"));
      }
    }

    @Test
    @DisplayName(
        "recovers via the 409 race guard when a concurrent create won and the second lookup finds"
            + " the project")
    void shouldReturnSuccessWhenFrostReturns409AndProjectExists() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(
            new MockResponse.Builder()
                .code(409)
                .body(
                    "{\"code\":409,\"type\":\"error\",\"message\":\"Data violates constraints.\"}")
                .build());
        server.enqueue(projectLookupFound(42));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("42", result.compensationData().get("projectId"));
      }
    }

    @Test
    @DisplayName("returns failure when the POST fails with 409 but no project is found by name")
    void shouldReturnFailureWhenFrostReturns409ButProjectNotFound() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(
            new MockResponse.Builder()
                .code(409)
                .body(
                    "{\"code\":409,\"type\":\"error\",\"message\":\"Data violates constraints.\"}")
                .build());
        // The recovery lookup also finds nothing.
        server.enqueue(emptyProjectLookup());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Ghost Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName(
        "returns failure when the POST fails with 500 'Failed to store data.' but no project is"
            + " found by name")
    void shouldReturnFailureWhenFrostSignalsDuplicateButProjectNotFound() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(
            new MockResponse.Builder()
                .code(500)
                .body("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}")
                .build());
        server.enqueue(emptyProjectLookup());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Ghost Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns failure on HTTP 400")
    void shouldReturnFailureOnClientError() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(new MockResponse.Builder().code(400).body("Bad Request").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Bad"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns failure on HTTP 500")
    void shouldReturnFailureOnServerError() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Test"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns failure on network error")
    void shouldReturnFailureOnNetworkError() throws IOException {
      try (FrostSagaHandler handler = createHandler()) {
        // Torn down before any request is made: the connection attempt itself fails.
        server.close();

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Test"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("always creates the project private even when openDataAccess is true")
    void shouldAlwaysCreateProjectPrivate() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        // openDataAccess=true must NOT make the FROST project public: open data is an OPA
        // (ABAC) decision at request time, never FROST project visibility.
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Public Dataset", "openDataAccess", true));

        handler.handle(command);

        server.takeRequest();
        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertEquals(false, body.get("public"));
      }
    }

    @Test
    @DisplayName("creates the project private when openDataAccess missing or false")
    void shouldDefaultPublicToFalse() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Private Dataset"));

        handler.handle(command);

        server.takeRequest();
        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertEquals(false, body.get("public"));
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_PROJECT")
  class UpdateProject {

    @Test
    @DisplayName("provisions the project when the dataset has none yet")
    void shouldCreateProjectWhenNoneWasProvisionedYet() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyProjectLookup());
        server.enqueue(created("Projects(42)"));

        // A FROST sink added after a release that provisioned no project: the update carries no
        // projectId, so the step must provision instead of failing the whole saga.
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of("datasetName", "Updated Dataset", "description", "Updated"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        // created=true marks this as a provisioning step, so its compensation deletes rather than
        // restores.
        assertEquals(true, result.compensationData().get("created"));
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("returns success with projectId, baseUrl and previous state in compensationData")
    void shouldUpdateProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(
            jsonResponse(200, Map.of("name", "Old Name", "description", "Old Description")));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of(
                    "projectId", "42", "datasetName", "Updated Dataset", "description", "Updated"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("42", result.compensationData().get("projectId"));
        assertEquals("Old Name", result.compensationData().get("previousName"));
        assertEquals("Old Description", result.compensationData().get("previousDescription"));
      }
    }

    @Test
    @DisplayName("omits previousName from compensationData when FROST returns no name")
    void shouldOmitPreviousNameWhenFrostReturnsNone() {
      try (FrostSagaHandler handler = createHandler()) {
        // FROST body without a "name" — capturing "" would arm a later RESTORE to blank the name.
        server.enqueue(jsonResponse(200, Map.of("description", "Old Description")));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of(
                    "projectId", "42", "datasetName", "Updated Dataset", "description", "Updated"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertFalse(
            result.compensationData().containsKey("previousName"),
            "a blank captured name must be omitted so RESTORE_PROJECT keeps the current name");
      }
    }

    @Test
    @DisplayName("uses publicUrl in baseUrl when configured differently from serverUrl")
    void shouldUsePublicUrlInBaseUrl() {
      try (FrostSagaHandler handler = createHandlerWithPublicUrl("http://public-frost:80/v1.1")) {
        server.enqueue(
            jsonResponse(200, Map.of("name", "Old Name", "description", "Old Description")));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of(
                    "projectId", "42", "datasetName", "Updated Dataset", "description", "Updated"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(
            "http://public-frost:80/v1.1/Projects(42)", result.resultData().get("baseUrl"));
      }
    }

    @Test
    @DisplayName("forces the project private on update and captures no previousPublic")
    void shouldForcePrivateAndNotCapturePreviousPublic() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(
            jsonResponse(
                200, Map.of("name", "Old Name", "description", "Old Description", "public", true)));
        server.enqueue(new MockResponse.Builder().code(200).build());

        // Even a previously-public project (and openDataAccess=true) is forced private on update.
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of(
                    "projectId",
                    "42",
                    "datasetName",
                    "Updated Dataset",
                    "description",
                    "Updated",
                    "openDataAccess",
                    true));

        SagaCommandResult result = handler.handle(command);

        server.takeRequest(); // the GET of current state
        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertEquals(false, body.get("public"));
        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.compensationData().get("previousPublic"));
      }
    }
  }

  @Nested
  @DisplayName("DELETE_PROJECT")
  class DeleteProject {

    @Test
    @DisplayName("returns success on forward delete")
    void shouldDeleteProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    @DisplayName("succeeds without touching FROST when the dataset has no project")
    void shouldSucceedWhenNoProjectWasProvisioned() {
      try (FrostSagaHandler handler = createHandler()) {
        // Without this, the delete saga strands the dataset row and every retry repeats the
        // failure.
        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("datasetId", "ds-1"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("deletes the project's Things in one batch request before deleting the project")
    void shouldDeleteThingsBeforeProject() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7, 8));
        server.enqueue(batchResponse(200, 200));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        // Project delete last — once it is gone, its Things can no longer be enumerated.
        assertEquals("/v1.1/Projects(42)/Things", server.takeRequest().getUrl().encodedPath());
        RecordedRequest batchRequest = server.takeRequest();
        assertEquals("/v1.1/$batch", batchRequest.getUrl().encodedPath());
        assertEquals("/v1.1/Projects(42)", server.takeRequest().getUrl().encodedPath());
        assertEquals(List.of("Things(7)", "Things(8)"), batchRequestUrls(batchRequest));
      }
    }

    @Test
    @DisplayName("follows pagination and deletes the Things of every page")
    void shouldDeleteThingsFromAllPages() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage("http://frost:8080/v1.1/Projects(42)/Things?$skip=2", 7, 8));
        server.enqueue(thingsPage(null, 9));
        server.enqueue(batchResponse(200, 200, 200));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        RecordedRequest page1 = server.takeRequest();
        RecordedRequest page2 = server.takeRequest();
        RecordedRequest batchRequest = server.takeRequest();
        server.takeRequest(); // project delete
        assertEquals("/v1.1/Projects(42)/Things", page1.getUrl().encodedPath());
        assertEquals("/v1.1/Projects(42)/Things", page2.getUrl().encodedPath());
        assertEquals(
            List.of("Things(7)", "Things(8)", "Things(9)"), batchRequestUrls(batchRequest));
        // A stuck $skip would refetch page 1 forever (the nextLink keeps the loop alive).
        assertEquals("0", page1.getUrl().queryParameter("$skip"));
        assertEquals("2", page2.getUrl().queryParameter("$skip"));
      }
    }

    @Test
    @DisplayName("splits the Thing deletes into multiple batch requests beyond the chunk size")
    void shouldSplitThingDeletesIntoChunkedBatches() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(
            thingsPage(
                "http://frost:8080/v1.1/Projects(42)/Things?$skip=100",
                java.util.stream.IntStream.range(0, 100).toArray()));
        server.enqueue(thingsPage(null, java.util.stream.IntStream.range(100, 150).toArray()));
        int[] fullChunk = new int[100];
        java.util.Arrays.fill(fullChunk, 200);
        int[] restChunk = new int[50];
        java.util.Arrays.fill(restChunk, 200);
        server.enqueue(batchResponse(fullChunk));
        server.enqueue(batchResponse(restChunk));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        RecordedRequest page1 = server.takeRequest();
        RecordedRequest page2 = server.takeRequest();
        RecordedRequest firstBatch = server.takeRequest();
        RecordedRequest secondBatch = server.takeRequest();
        assertEquals(100, batchRequestUrls(firstBatch).size());
        assertEquals(50, batchRequestUrls(secondBatch).size());
        assertEquals("0", page1.getUrl().queryParameter("$skip"));
        assertEquals("100", page2.getUrl().queryParameter("$skip"));
      }
    }

    @Test
    @DisplayName(
        "continues with the remaining Things and the project when one Thing is already gone")
    void shouldContinueWhenSingleThingAlreadyAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7, 8));
        server.enqueue(batchResponse(404, 200));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A Thing that is already gone is the goal state — the cleanup must not stop there.
        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(3, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step when a Thing delete returns a genuine error (500)")
    void shouldFailStepWhenThingDeleteFails() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7, 8));
        server.enqueue(batchResponse(500, 200));

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A genuine error must fail the step — no silent skip that would strand Things.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("deletes provisioned entities by identity after the Things, before the project")
    void shouldDeleteProvisionedEntitiesAfterThings() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7));
        server.enqueue(batchResponse(200));
        server.enqueue(batchResponse(200));
        server.enqueue(batchResponse(200));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "DELETE_PROJECT",
                    Map.of(
                        "projectId",
                        "42",
                        "provisionedEntities",
                        Map.of("Sensors", List.of("5"), "Datastreams", List.of("3")))));

        assertEquals("STEP_COMPLETED", result.type());
        server.takeRequest(); // the Things enumeration GET
        RecordedRequest thingsBatch = server.takeRequest();
        RecordedRequest datastreamsBatch = server.takeRequest();
        RecordedRequest sensorsBatch = server.takeRequest();
        RecordedRequest projectDelete = server.takeRequest();
        // Datastreams before Sensors: a Sensor delete cascades into still-linked Datastreams.
        assertEquals(List.of("Things(7)"), batchRequestUrls(thingsBatch));
        assertEquals(List.of("Datastreams(3)"), batchRequestUrls(datastreamsBatch));
        assertEquals(List.of("Sensors(5)"), batchRequestUrls(sensorsBatch));
        assertEquals("/v1.1/Projects(42)", projectDelete.getUrl().encodedPath());
      }
    }

    @Test
    @DisplayName("ignores unknown provisioned entity sets instead of building delete requests")
    void shouldIgnoreUnknownProvisionedEntitySets() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "DELETE_PROJECT",
                    Map.of(
                        "projectId",
                        "42",
                        "provisionedEntities",
                        Map.of("Projects", List.of("9")))));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step when the batch response repeats a sub-response id")
    void shouldFailStepOnDuplicateBatchSubResponseId() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7, 8));
        server.enqueue(
            jsonResponse(
                200,
                Map.of(
                    "responses",
                    List.of(Map.of("id", "0", "status", 200), Map.of("id", "0", "status", 200)))));

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A duplicate id with a matching size leaves one Thing's outcome unconfirmed — it must
        // not slip through into the project delete.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step when the batch response carries a malformed sub-response id")
    void shouldFailStepOnMalformedBatchSubResponseId() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7));
        server.enqueue(
            jsonResponse(200, Map.of("responses", List.of(Map.of("id", "x", "status", 200)))));

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step before any deletion when provisionedEntities has a non-scalar id")
    void shouldFailStepOnNonScalarProvisionedId() {
      try (FrostSagaHandler handler = createHandler()) {
        // An object instead of a scalar id (e.g. a serialized entity) is a broken producer —
        // silently skipping it would silently retain the entity in FROST.
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "DELETE_PROJECT",
                    Map.of(
                        "projectId",
                        "42",
                        "provisionedEntities",
                        Map.of("Sensors", List.of(Map.of("@iot.id", "5"))))));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        // Validation must run before any deletion side effect.
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step before any deletion when a provisionedEntities value is no list")
    void shouldFailStepOnNonListProvisionedValue() {
      try (FrostSagaHandler handler = createHandler()) {
        // "Datastreams": "5,7" — a string instead of a list is a broken producer; silently
        // dropping it would silently retain the entities in FROST.
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "DELETE_PROJECT",
                    Map.of(
                        "projectId", "42", "provisionedEntities", Map.of("Datastreams", "5,7"))));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step before any deletion when provisionedEntities itself is no map")
    void shouldFailStepOnNonMapProvisionedEntities() {
      try (FrostSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "DELETE_PROJECT",
                    Map.of("projectId", "42", "provisionedEntities", "Sensors")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step when the batch response omits sub-responses")
    void shouldFailStepWhenBatchResponseIncomplete() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(thingsPage(null, 7, 8));
        server.enqueue(batchResponse(200));

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A truncated batch response must not let unconfirmed Things slip through.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails a forward delete already at a 404 Thing enumeration, before any delete")
    void shouldSucceedForwardDeleteWhenProjectAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(404).body("Not Found").build());
        server.enqueue(new MockResponse.Builder().code(404).body("Not Found").build());

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        // A 404 is the goal state of a delete in both directions: a forward delete of a project a
        // prior run already removed (re-delete / re-release of a preserved sink) must be
        // idempotent, not fail the delete saga on the missing project.
        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on compensate delete")
    void shouldReturnCompensationSuccessOnCompensateDelete() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "42"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName(
        "compensating a REUSED project (created=false) preserves it — no Things enumeration, no"
            + " delete")
    void shouldNotDeleteReusedProjectOnCompensation() {
      try (FrostSagaHandler handler = createHandler()) {
        // The CREATE_PROJECT reuse path stamps created=false into compensationData; on compensation
        // that map becomes the command payload. Deleting here would destroy the data re-release is
        // meant to reuse — the whole point of #1923's sink-preserving unrelease.
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "42", "created", false));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        // Neither the Things enumeration GET nor the project DELETE may be issued.
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("compensating a freshly CREATED project (created=true) still deletes it")
    void shouldDeleteCreatedProjectOnCompensation() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "42", "created", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        server.takeRequest();
        assertEquals("DELETE", server.takeRequest().getMethod());
      }
    }

    @Test
    @DisplayName("treats 404 as success on compensation — 'project already gone' is the goal state")
    void shouldTreat404AsSuccessOnCompensation() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(404).body("Not Found").build());
        server.enqueue(new MockResponse.Builder().code(404).body("Not Found").build());

        SagaCommandResult result =
            handler.handle(
                createCommand("COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        // "Already gone" is the compensation goal state; the enumeration 404 must fall through.
        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on a genuine error (500) during compensation")
    void shouldReturnCompensationFailureOnGenuineError() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandResult result =
            handler.handle(
                createCommand("COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }
  }

  @Nested
  @DisplayName("RESTORE_PROJECT")
  class RestoreProject {

    @Test
    @DisplayName("deletes instead of restoring when the update provisioned the project")
    void shouldDeleteProjectWhenTheUpdateCreatedIt() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        // The project has no Things, so the teardown enumeration returns a single empty page.
        server.enqueue(emptyThingsPage());
        server.enqueue(new MockResponse.Builder().code(200).build());

        // There is no previous state to restore: the forward step created the project, so the exact
        // inverse is the delete. Patching would blank the description of a project meant to go
        // away.
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP", "RESTORE_PROJECT", Map.of("projectId", "42", "created", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        server.takeRequest();
        assertEquals("DELETE", server.takeRequest().getMethod());
      }
    }

    @Test
    @DisplayName("preserves a project the update merely adopted (created=false)")
    void shouldPreserveProjectWhenTheUpdateReusedIt() {
      try (FrostSagaHandler handler = createHandler()) {
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP", "RESTORE_PROJECT", Map.of("projectId", "42", "created", false));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on successful restore")
    void shouldRestoreProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());
      }
    }

    @Test
    @DisplayName("omits name from body when previousName missing (no blanking, MR !547 finding 6)")
    void shouldOmitNameWhenPreviousNameMissing() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of("projectId", "42", "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        // PATCHing name="" would blank the project identity and break the unique-name
        // duplicate-recovery lookup — the field must be left out so FROST keeps the current name.
        assertEquals("COMPENSATION_COMPLETED", result.type());
        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertNull(body.get("name"));
      }
    }

    @Test
    @DisplayName("treats a blank previousName like a missing one (legacy \"\" capture)")
    void shouldOmitNameWhenPreviousNameBlank() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "",
                    "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertNull(body.get("name"));
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on HTTP error")
    void shouldReturnCompensationFailureOnError() {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on network error")
    void shouldReturnCompensationFailureOnNetworkError() throws IOException {
      try (FrostSagaHandler handler = createHandler()) {
        server.close();

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("forces the project private on restore, ignoring any previousPublic in payload")
    void shouldForcePrivateOnRestoreIgnoringPreviousPublic() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());

        // A stale previousPublic=true from an old saga must NOT resurrect a public project.
        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description",
                    "previousPublic", true));

        handler.handle(command);

        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertEquals(false, body.get("public"));
      }
    }

    @Test
    @DisplayName("forces the project private on restore when no previousPublic is present")
    void shouldForcePrivateOnRestoreWhenPreviousPublicMissing() throws InterruptedException {
      try (FrostSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description"));

        handler.handle(command);

        Map<String, Object> body = requestBodyAsMap(server.takeRequest());
        assertEquals(false, body.get("public"));
      }
    }
  }

  @Nested
  @DisplayName("Unknown operation")
  class UnknownOperation {

    @Test
    @DisplayName("returns STEP_FAILED for unknown forward operation")
    void shouldReturnFailureForUnknownOperation() {
      try (FrostSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED for unknown compensate operation")
    void shouldReturnCompensationFailureForUnknownOperation() {
      try (FrostSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  // ============== HELPERS ==============

  private FrostSagaHandler createHandler() {
    return createHandlerWithPublicUrl(server.url("/v1.1").toString());
  }

  private FrostSagaHandler createHandlerWithPublicUrl(String publicUrl) {
    FrostSagaHandler handler = new FrostSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn(server.url("/v1.1").toString());
    when(mockConfig.getProperty(eq("frost.public.url"), any())).thenReturn(publicUrl);
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");
    handler.initialize(mockConfig);
    return handler;
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    // CREATE_PROJECT / UPDATE_PROJECT require datasetId (it makes the FROST project name globally
    // unique — P1). Default it here so individual tests only set it when they assert on it.
    Map<String, Object> effective = new HashMap<>(payload);
    effective.putIfAbsent("datasetId", "ds-default");
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-project", "frost", operation, effective);
  }

  private static MockResponse jsonResponse(int code, Map<String, Object> body) {
    try {
      return new MockResponse.Builder()
          .code(code)
          .body(PayloadConverter.objectMapper().writeValueAsString(body))
          .build();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static MockResponse emptyProjectLookup() {
    return jsonResponse(200, Map.of("value", List.of()));
  }

  private static MockResponse projectLookupFound(int id) {
    return jsonResponse(200, Map.of("value", List.of(Map.of("@iot.id", id))));
  }

  private static MockResponse created(String entityPath) {
    return new MockResponse.Builder()
        .code(201)
        .addHeader("Location", "http://frost:8080/v1.1/" + entityPath)
        .build();
  }

  /** Mocked enumeration page: one {@code @iot.id} entry per id, plus the nextLink if given. */
  private static MockResponse thingsPage(String nextLink, int... thingIds) {
    Map<String, Object> body = new HashMap<>();
    List<Map<String, Object>> value = new java.util.ArrayList<>();
    for (int thingId : thingIds) {
      value.add(Map.of("@iot.id", thingId));
    }
    body.put("value", value);
    if (nextLink != null) {
      body.put("@iot.nextLink", nextLink);
    }
    return jsonResponse(200, body);
  }

  private static MockResponse emptyThingsPage() {
    return thingsPage(null);
  }

  /** Mocked JSON batch response: one sub-response per given status, ids "0", "1", … in order. */
  private static MockResponse batchResponse(int... statuses) {
    List<Map<String, Object>> subResponses = new java.util.ArrayList<>();
    for (int i = 0; i < statuses.length; i++) {
      subResponses.add(Map.of("id", String.valueOf(i), "status", statuses[i]));
    }
    return jsonResponse(200, Map.of("responses", subResponses));
  }

  private static Map<String, Object> requestBodyAsMap(RecordedRequest request) {
    try {
      return PayloadConverter.readMap(request.getBody().toByteArray());
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /** Extracts the sub-request urls from a captured JSON batch request body, in order. */
  private static List<String> batchRequestUrls(RecordedRequest request) {
    Map<String, Object> body = requestBodyAsMap(request);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> requests = (List<Map<String, Object>>) body.get("requests");
    return requests.stream().map(r -> (String) r.get("url")).toList();
  }
}

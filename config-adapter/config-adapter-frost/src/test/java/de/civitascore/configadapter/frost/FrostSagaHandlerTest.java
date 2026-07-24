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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FrostSagaHandlerTest {

  private WebTarget mockTarget;
  private WebTarget mockPathTarget;
  private Invocation.Builder mockBuilder;

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
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://frost:8080/v1.1/Projects(42)");
        when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

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
        assertEquals("http://frost:8080/v1.1/Projects(42)", result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
        // A freshly created project flags created=true so its compensation may safely delete it.
        assertEquals(true, result.compensationData().get("created"));
      }
    }

    @Test
    @DisplayName(
        "names the FROST project '{datasetName} ({datasetId})' so same-named datasets stay isolated")
    void shouldNameProjectUniquelyWithDatasetId() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://frost:8080/v1.1/Projects(42)");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(captor.capture())).thenReturn(mockResponse);

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Foo", "datasetId", "ds-1")));

        Map<String, Object> body = captor.getValue().getEntity();
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
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://frost:8080/v1.1/Projects(42)");
        when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

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
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(Map.of("value", List.of(Map.of("@iot.id", 42))));
        when(mockBuilder.get()).thenReturn(getResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("http://frost:8080/v1.1/Projects(42)", result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
        // Reuse must flag created=false so its compensation skips the destructive delete.
        assertEquals(false, result.compensationData().get("created"));
        verify(mockBuilder, never()).post(any(Entity.class));
      }
    }

    @Test
    @DisplayName(
        "recovers via the 500 'Failed to store data.' race guard when a concurrent create won and"
            + " the second lookup finds the project")
    void shouldReturnSuccessWhenFrostSignalsDuplicateAndProjectExists() {
      try (FrostSagaHandler handler = createHandler()) {
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(500);
        when(postResponse.readEntity(String.class))
            .thenReturn("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

        // First lookup: empty (POST is attempted). After the POST's 500, the recovery lookup finds
        // the project a concurrent create inserted in between.
        Response emptyLookup = mock(Response.class);
        when(emptyLookup.getStatus()).thenReturn(200);
        when(emptyLookup.readEntity(Map.class)).thenReturn(Map.of("value", List.of()));
        Response foundLookup = mock(Response.class);
        when(foundLookup.getStatus()).thenReturn(200);
        when(foundLookup.readEntity(Map.class))
            .thenReturn(Map.of("value", List.of(Map.of("@iot.id", 42))));
        when(mockBuilder.get()).thenReturn(emptyLookup, foundLookup);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("http://frost:8080/v1.1/Projects(42)", result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
      }
    }

    @Test
    @DisplayName(
        "recovers via the 409 race guard when a concurrent create won and the second lookup finds"
            + " the project")
    void shouldReturnSuccessWhenFrostReturns409AndProjectExists() {
      try (FrostSagaHandler handler = createHandler()) {
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(409);
        when(postResponse.readEntity(String.class))
            .thenReturn(
                "{\"code\":409,\"type\":\"error\",\"message\":\"Data violates constraints.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

        // First lookup: empty (POST is attempted). After the POST's 409, the recovery lookup finds
        // the project a concurrent create inserted in between.
        Response emptyLookup = mock(Response.class);
        when(emptyLookup.getStatus()).thenReturn(200);
        when(emptyLookup.readEntity(Map.class)).thenReturn(Map.of("value", List.of()));
        Response foundLookup = mock(Response.class);
        when(foundLookup.getStatus()).thenReturn(200);
        when(foundLookup.readEntity(Map.class))
            .thenReturn(Map.of("value", List.of(Map.of("@iot.id", 42))));
        when(mockBuilder.get()).thenReturn(emptyLookup, foundLookup);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Existing Dataset"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("http://frost:8080/v1.1/Projects(42)", result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
      }
    }

    @Test
    @DisplayName("returns failure when the POST fails with 409 but no project is found by name")
    void shouldReturnFailureWhenFrostReturns409ButProjectNotFound() {
      try (FrostSagaHandler handler = createHandler()) {
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(409);
        when(postResponse.readEntity(String.class))
            .thenReturn(
                "{\"code\":409,\"type\":\"error\",\"message\":\"Data violates constraints.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

        // Both the up-front and the recovery lookup return empty (default from setup).
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
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(500);
        when(postResponse.readEntity(String.class))
            .thenReturn("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

        // Both the up-front and the recovery lookup return empty (default from setup).
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
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(400);
        when(mockResponse.readEntity(String.class)).thenReturn("Bad Request");
        when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

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
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.post(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Test"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns failure on network error")
    void shouldReturnFailureOnNetworkError() {
      try (FrostSagaHandler handler = createHandler()) {
        when(mockBuilder.post(any(Entity.class)))
            .thenThrow(new ProcessingException("Connection refused"));

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Test"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("always creates the project private even when openDataAccess is true")
    void shouldAlwaysCreateProjectPrivate() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://frost:8080/v1.1/Projects(42)");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(captor.capture())).thenReturn(mockResponse);

        // openDataAccess=true must NOT make the FROST project public: open data is an OPA
        // (ABAC) decision at request time, never FROST project visibility.
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Public Dataset", "openDataAccess", true));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertEquals(false, body.get("public"));
      }
    }

    @Test
    @DisplayName("creates the project private when openDataAccess missing or false")
    void shouldDefaultPublicToFalse() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockResponse.getHeaderString("Location"))
            .thenReturn("http://frost:8080/v1.1/Projects(42)");
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(captor.capture())).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "CREATE_PROJECT", Map.of("datasetName", "Private Dataset"));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertEquals(false, body.get("public"));
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_PROJECT")
  class UpdateProject {

    @Test
    @DisplayName("returns success with projectId, baseUrl and previous state in compensationData")
    void shouldUpdateProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        // Mock GET for reading current state
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(Map.of("name", "Old Name", "description", "Old Description"));
        when(mockBuilder.get()).thenReturn(getResponse);

        // Mock PATCH for the update
        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(patchResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_PROJECT",
                Map.of(
                    "projectId", "42", "datasetName", "Updated Dataset", "description", "Updated"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("42", result.resultData().get("projectId"));
        assertEquals("http://frost:8080/v1.1/Projects(42)", result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
        assertEquals("Old Name", result.compensationData().get("previousName"));
        assertEquals("Old Description", result.compensationData().get("previousDescription"));
      }
    }

    @Test
    @DisplayName("omits previousName from compensationData when FROST returns no name")
    void shouldOmitPreviousNameWhenFrostReturnsNone() {
      try (FrostSagaHandler handler = createHandler()) {
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        // FROST body without a "name" — capturing "" would arm a later RESTORE to blank the name.
        when(getResponse.readEntity(Map.class))
            .thenReturn(Map.of("description", "Old Description"));
        when(mockBuilder.get()).thenReturn(getResponse);

        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(patchResponse);

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
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(Map.of("name", "Old Name", "description", "Old Description"));
        when(mockBuilder.get()).thenReturn(getResponse);

        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(patchResponse);

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
    void shouldForcePrivateAndNotCapturePreviousPublic() {
      try (FrostSagaHandler handler = createHandler()) {
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(
                Map.of("name", "Old Name", "description", "Old Description", "public", true));
        when(mockBuilder.get()).thenReturn(getResponse);

        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(patchResponse);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
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
        Response emptyThingsPage = thingsPage(null);
        when(mockBuilder.get()).thenReturn(emptyThingsPage);
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    @DisplayName("deletes the project's Things in one batch request before deleting the project")
    void shouldDeleteThingsBeforeProject() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = batchResponse(200, 200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> batchCaptor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(batchCaptor.capture())).thenReturn(batch);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        // Project delete last — once it is gone, its Things can no longer be enumerated.
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, times(3)).path(paths.capture());
        assertEquals(
            List.of("Projects(42)/Things", "$batch", "Projects(42)"), paths.getAllValues());
        assertEquals(List.of("Things(7)", "Things(8)"), batchRequestUrls(batchCaptor.getValue()));
      }
    }

    @Test
    @DisplayName("follows pagination and deletes the Things of every page")
    void shouldDeleteThingsFromAllPages() {
      try (FrostSagaHandler handler = createHandler()) {
        Response firstPage = thingsPage("http://frost:8080/v1.1/Projects(42)/Things?$skip=2", 7, 8);
        Response lastPage = thingsPage(null, 9);
        when(mockBuilder.get()).thenReturn(firstPage, lastPage);
        Response batch = batchResponse(200, 200, 200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> batchCaptor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(batchCaptor.capture())).thenReturn(batch);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, times(4)).path(paths.capture());
        assertEquals(
            List.of("Projects(42)/Things", "Projects(42)/Things", "$batch", "Projects(42)"),
            paths.getAllValues());
        assertEquals(
            List.of("Things(7)", "Things(8)", "Things(9)"),
            batchRequestUrls(batchCaptor.getValue()));
        // A stuck $skip would refetch page 1 forever (the nextLink keeps the loop alive).
        ArgumentCaptor<Object> skips = ArgumentCaptor.forClass(Object.class);
        verify(mockPathTarget, times(2)).queryParam(eq("$skip"), skips.capture());
        assertEquals(List.of("0", "2"), skips.getAllValues());
      }
    }

    @Test
    @DisplayName("splits the Thing deletes into multiple batch requests beyond the chunk size")
    void shouldSplitThingDeletesIntoChunkedBatches() {
      try (FrostSagaHandler handler = createHandler()) {
        Response firstPage =
            thingsPage(
                "http://frost:8080/v1.1/Projects(42)/Things?$skip=100",
                IntStream.range(0, 100).toArray());
        Response lastPage = thingsPage(null, IntStream.range(100, 150).toArray());
        when(mockBuilder.get()).thenReturn(firstPage, lastPage);
        int[] fullChunk = new int[100];
        Arrays.fill(fullChunk, 200);
        int[] restChunk = new int[50];
        Arrays.fill(restChunk, 200);
        Response firstBatch = batchResponse(fullChunk);
        Response secondBatch = batchResponse(restChunk);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> batchCaptor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(batchCaptor.capture())).thenReturn(firstBatch, secondBatch);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(2)).post(any(Entity.class));
        assertEquals(100, batchRequestUrls(batchCaptor.getAllValues().get(0)).size());
        assertEquals(50, batchRequestUrls(batchCaptor.getAllValues().get(1)).size());
        ArgumentCaptor<Object> skips = ArgumentCaptor.forClass(Object.class);
        verify(mockPathTarget, times(2)).queryParam(eq("$skip"), skips.capture());
        assertEquals(List.of("0", "100"), skips.getAllValues());
      }
    }

    @Test
    @DisplayName(
        "continues with the remaining Things and the project when one Thing is already gone")
    void shouldContinueWhenSingleThingAlreadyAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = batchResponse(404, 200);
        when(mockBuilder.post(any(Entity.class))).thenReturn(batch);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A Thing that is already gone is the goal state — the cleanup must not stop there.
        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(1)).delete();
      }
    }

    @Test
    @DisplayName("fails the step when a Thing delete returns a genuine error (500)")
    void shouldFailStepWhenThingDeleteFails() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = batchResponse(500, 200);
        when(mockBuilder.post(any(Entity.class))).thenReturn(batch);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A genuine error must fail the step — no silent skip that would strand Things.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockBuilder, never()).delete();
      }
    }

    @Test
    @DisplayName("deletes provisioned entities by identity after the Things, before the project")
    void shouldDeleteProvisionedEntitiesAfterThings() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response thingsBatch = batchResponse(200);
        Response datastreamsBatch = batchResponse(200);
        Response sensorsBatch = batchResponse(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> batchCaptor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.post(batchCaptor.capture()))
            .thenReturn(thingsBatch, datastreamsBatch, sensorsBatch);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

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
        // Datastreams before Sensors: a Sensor delete cascades into still-linked Datastreams.
        assertEquals(List.of("Things(7)"), batchRequestUrls(batchCaptor.getAllValues().get(0)));
        assertEquals(
            List.of("Datastreams(3)"), batchRequestUrls(batchCaptor.getAllValues().get(1)));
        assertEquals(List.of("Sensors(5)"), batchRequestUrls(batchCaptor.getAllValues().get(2)));
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, times(5)).path(paths.capture());
        assertEquals(
            List.of("Projects(42)/Things", "$batch", "$batch", "$batch", "Projects(42)"),
            paths.getAllValues());
      }
    }

    @Test
    @DisplayName("ignores unknown provisioned entity sets instead of building delete requests")
    void shouldIgnoreUnknownProvisionedEntitySets() {
      try (FrostSagaHandler handler = createHandler()) {
        Response emptyThingsPage = thingsPage(null);
        when(mockBuilder.get()).thenReturn(emptyThingsPage);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

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
        verify(mockBuilder, never()).post(any(Entity.class));
      }
    }

    @Test
    @DisplayName("fails the step when the batch response repeats a sub-response id")
    void shouldFailStepOnDuplicateBatchSubResponseId() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = mock(Response.class);
        when(batch.getStatus()).thenReturn(200);
        when(batch.readEntity(Map.class))
            .thenReturn(
                Map.of(
                    "responses",
                    List.of(Map.of("id", "0", "status", 200), Map.of("id", "0", "status", 200))));
        when(mockBuilder.post(any(Entity.class))).thenReturn(batch);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A duplicate id with a matching size leaves one Thing's outcome unconfirmed — it must
        // not slip through into the project delete.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockBuilder, never()).delete();
      }
    }

    @Test
    @DisplayName("fails the step when the batch response carries a malformed sub-response id")
    void shouldFailStepOnMalformedBatchSubResponseId() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = mock(Response.class);
        when(batch.getStatus()).thenReturn(200);
        when(batch.readEntity(Map.class))
            .thenReturn(Map.of("responses", List.of(Map.of("id", "x", "status", 200))));
        when(mockBuilder.post(any(Entity.class))).thenReturn(batch);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockBuilder, never()).delete();
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
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).post(any(Entity.class));
        verify(mockBuilder, never()).delete();
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
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).delete();
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
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).delete();
      }
    }

    @Test
    @DisplayName("fails the step when the batch response omits sub-responses")
    void shouldFailStepWhenBatchResponseIncomplete() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response batch = batchResponse(200);
        when(mockBuilder.post(any(Entity.class))).thenReturn(batch);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A truncated batch response must not let unconfirmed Things slip through.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockBuilder, never()).delete();
      }
    }

    @Test
    @DisplayName("fails a forward delete already at a 404 Thing enumeration, before any delete")
    void shouldSucceedForwardDeleteWhenProjectAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(String.class)).thenReturn("Not Found");
        when(mockBuilder.get()).thenReturn(notFound);
        when(mockBuilder.delete()).thenReturn(notFound);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        // A 404 is the goal state of a delete in both directions: a forward delete of a project a
        // prior run already removed (re-delete / re-release of a preserved sink) must be
        // idempotent,
        // not fail the delete saga on the missing project.
        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on compensate delete")
    void shouldReturnCompensationSuccessOnCompensateDelete() {
      try (FrostSagaHandler handler = createHandler()) {
        Response emptyThingsPage = thingsPage(null);
        when(mockBuilder.get()).thenReturn(emptyThingsPage);
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(mockResponse);

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
        verify(mockBuilder, never()).delete();
        verify(mockTarget, never()).path("Projects(42)/Things");
      }
    }

    @Test
    @DisplayName("compensating a freshly CREATED project (created=true) still deletes it")
    void shouldDeleteCreatedProjectOnCompensation() {
      try (FrostSagaHandler handler = createHandler()) {
        Response emptyThingsPage = thingsPage(null);
        when(mockBuilder.get()).thenReturn(emptyThingsPage);
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "42", "created", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockBuilder, times(1)).delete();
      }
    }

    @Test
    @DisplayName("treats 404 as success on compensation — 'project already gone' is the goal state")
    void shouldTreat404AsSuccessOnCompensation() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(404);
        when(mockResponse.readEntity(String.class)).thenReturn("Not Found");
        when(mockBuilder.get()).thenReturn(mockResponse);
        when(mockBuilder.delete()).thenReturn(mockResponse);

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
        Response emptyThingsPage = thingsPage(null);
        when(mockBuilder.get()).thenReturn(emptyThingsPage);
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    /** Mocked enumeration page: one {@code @iot.id} entry per id, plus the nextLink if given. */
    private Response thingsPage(String nextLink, int... thingIds) {
      Response page = mock(Response.class);
      when(page.getStatus()).thenReturn(200);
      List<Map<String, Object>> value = new ArrayList<>();
      for (int thingId : thingIds) {
        value.add(Map.of("@iot.id", thingId));
      }
      Map<String, Object> body = new HashMap<>();
      body.put("value", value);
      if (nextLink != null) {
        body.put("@iot.nextLink", nextLink);
      }
      when(page.readEntity(Map.class)).thenReturn(body);
      return page;
    }

    /** Mocked JSON batch response: one sub-response per given status, ids "0", "1", … in order. */
    private Response batchResponse(int... statuses) {
      Response response = mock(Response.class);
      when(response.getStatus()).thenReturn(200);
      List<Map<String, Object>> subResponses = new ArrayList<>();
      for (int i = 0; i < statuses.length; i++) {
        subResponses.add(Map.of("id", String.valueOf(i), "status", statuses[i]));
      }
      when(response.readEntity(Map.class)).thenReturn(Map.of("responses", subResponses));
      return response;
    }

    /** Extracts the sub-request urls from a captured JSON batch request entity, in order. */
    private List<String> batchRequestUrls(Entity<?> entity) {
      @SuppressWarnings("unchecked")
      Map<String, Object> body = (Map<String, Object>) entity.getEntity();
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> requests = (List<Map<String, Object>>) body.get("requests");
      return requests.stream().map(request -> (String) request.get("url")).toList();
    }
  }

  @Nested
  @DisplayName("RESTORE_PROJECT")
  class RestoreProject {

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on successful restore")
    void shouldRestoreProjectSuccessfully() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(mockResponse);

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
    void shouldOmitNameWhenPreviousNameMissing() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of("projectId", "42", "previousDescription", "Old Description"));

        SagaCommandResult result = handler.handle(command);

        // PATCHing name="" would blank the project identity and break the unique-name
        // duplicate-recovery lookup — the field must be left out so FROST keeps the current name.
        assertEquals("COMPENSATION_COMPLETED", result.type());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertNull(body.get("name"));
      }
    }

    @Test
    @DisplayName("treats a blank previousName like a missing one (legacy \"\" capture)")
    void shouldOmitNameWhenPreviousNameBlank() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(mockResponse);

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
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertNull(body.get("name"));
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on HTTP error")
    void shouldReturnCompensationFailureOnError() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.method(eq("PATCH"), any(Entity.class))).thenReturn(mockResponse);

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
    void shouldReturnCompensationFailureOnNetworkError() {
      try (FrostSagaHandler handler = createHandler()) {
        when(mockBuilder.method(eq("PATCH"), any(Entity.class)))
            .thenThrow(new ProcessingException("Connection refused"));

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
    void shouldForcePrivateOnRestoreIgnoringPreviousPublic() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(mockResponse);

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

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertEquals(false, body.get("public"));
      }
    }

    @Test
    @DisplayName("forces the project private on restore when no previousPublic is present")
    void shouldForcePrivateOnRestoreWhenPreviousPublicMissing() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_PROJECT",
                Map.of(
                    "projectId", "42",
                    "previousName", "Old Name",
                    "previousDescription", "Old Description"));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
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

  private FrostSagaHandler createHandler() {
    return createHandlerWithPublicUrl("http://frost:8080/v1.1");
  }

  private FrostSagaHandler createHandlerWithPublicUrl(String publicUrl) {
    FrostSagaHandler handler = new FrostSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://frost:8080/v1.1");
    when(mockConfig.getProperty("frost.public.url", "http://frost:8080/v1.1"))
        .thenReturn(publicUrl);
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    mockTarget = mock(WebTarget.class);
    mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

    // CREATE_PROJECT is now find-or-create: it always issues a name-lookup GET before the POST.
    // Default that lookup to "no existing project" so the POST path is exercised; tests that assert
    // reuse override mockBuilder.get() with a non-empty result.
    Response emptyLookup = mock(Response.class);
    when(emptyLookup.getStatus()).thenReturn(200);
    when(emptyLookup.readEntity(Map.class)).thenReturn(Map.of("value", List.of()));
    when(mockBuilder.get()).thenReturn(emptyLookup);

    handler.setTestClient(mockClient);
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
}

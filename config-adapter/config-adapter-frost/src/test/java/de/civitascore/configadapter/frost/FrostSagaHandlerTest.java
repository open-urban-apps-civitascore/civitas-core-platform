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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FrostSagaHandlerTest {

  private WebTarget mockTarget;
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
    @DisplayName(
        "returns success when FROST signals duplicate via 500 'Failed to store data.' and project exists")
    void shouldReturnSuccessWhenFrostSignalsDuplicateAndProjectExists() {
      try (FrostSagaHandler handler = createHandler()) {
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(500);
        when(postResponse.readEntity(String.class))
            .thenReturn("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

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
      }
    }

    @Test
    @DisplayName(
        "returns failure when FROST signals duplicate via 500 'Failed to store data.' but no project found by name")
    void shouldReturnFailureWhenFrostSignalsDuplicateButProjectNotFound() {
      try (FrostSagaHandler handler = createHandler()) {
        Response postResponse = mock(Response.class);
        when(postResponse.getStatus()).thenReturn(500);
        when(postResponse.readEntity(String.class))
            .thenReturn("{\"code\":500,\"type\":\"error\",\"message\":\"Failed to store data.\"}");
        when(mockBuilder.post(any(Entity.class))).thenReturn(postResponse);

        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class)).thenReturn(Map.of("value", List.of()));
        when(mockBuilder.get()).thenReturn(getResponse);

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
    @DisplayName("deletes the project's Things before deleting the project itself")
    void shouldDeleteThingsBeforeProject() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        // FROST does not cascade project deletion, so the project delete must come last —
        // otherwise the membership links are gone and the Things are stranded at server root.
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, times(4)).path(paths.capture());
        assertEquals(
            List.of("Projects(42)/Things", "Things(7)", "Things(8)", "Projects(42)"),
            paths.getAllValues());
      }
    }

    @Test
    @DisplayName("follows pagination and deletes the Things of every page")
    void shouldDeleteThingsFromAllPages() {
      try (FrostSagaHandler handler = createHandler()) {
        Response firstPage = thingsPage("http://frost:8080/v1.1/Projects(42)/Things?$skip=2", 7, 8);
        Response lastPage = thingsPage(null, 9);
        when(mockBuilder.get()).thenReturn(firstPage, lastPage);
        Response deleteResponse = mock(Response.class);
        when(deleteResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(deleteResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        assertEquals("STEP_COMPLETED", result.type());
        ArgumentCaptor<String> paths = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, times(6)).path(paths.capture());
        assertEquals(
            List.of(
                "Projects(42)/Things",
                "Projects(42)/Things",
                "Things(7)",
                "Things(8)",
                "Things(9)",
                "Projects(42)"),
            paths.getAllValues());
      }
    }

    @Test
    @DisplayName(
        "continues with the remaining Things and the project when one Thing is already gone")
    void shouldContinueWhenSingleThingAlreadyAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(notFound, ok, ok);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A Thing that is already gone is the goal state — the cleanup must not stop there.
        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(3)).delete();
      }
    }

    @Test
    @DisplayName("fails the step when a Thing delete returns a genuine error (500)")
    void shouldFailStepWhenThingDeleteFails() {
      try (FrostSagaHandler handler = createHandler()) {
        Response singleThingsPage = thingsPage(null, 7, 8);
        when(mockBuilder.get()).thenReturn(singleThingsPage);
        Response error = mock(Response.class);
        when(error.getStatus()).thenReturn(500);
        when(error.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.delete()).thenReturn(error);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "42")));

        // A genuine error must fail the step (no silent skip) — otherwise the project would be
        // deleted while its Things stay behind, stranded at server root.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        verify(mockBuilder, times(1)).delete();
      }
    }

    @Test
    @DisplayName("still fails a forward delete when the project is absent (404)")
    void shouldFailForwardDeleteWhenProjectAbsent() {
      try (FrostSagaHandler handler = createHandler()) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(String.class)).thenReturn("Not Found");
        when(mockBuilder.get()).thenReturn(notFound);
        when(mockBuilder.delete()).thenReturn(notFound);

        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "DELETE_PROJECT", Map.of("projectId", "999")));

        // The Thing cleanup skips silently on 404, but a forward delete of a missing project is
        // genuine drift and must stay visible.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
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

        // Idempotent compensation: the project no longer existing IS the desired end state, so a
        // retried/already-cleaned-up DELETE_PROJECT compensation must not fail the saga rollback.
        // The Thing enumeration hits the 404 first and must fall through to this rule.
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

    /**
     * Mocked enumeration page of {@code Projects(n)/Things}: a {@code value} array with one {@code
     * @iot.id} entry per given id, plus an {@code @iot.nextLink} when more pages follow.
     */
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
    WebTarget mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.queryParam(any(String.class), any())).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

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

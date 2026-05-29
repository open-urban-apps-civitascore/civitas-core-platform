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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FrostSagaHandlerTest {

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
    @DisplayName("sets public=true on body when openDataAccess is true")
    void shouldSetPublicTrueWhenOpenDataAccess() {
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
                "EXECUTE_STEP",
                "CREATE_PROJECT",
                Map.of("datasetName", "Public Dataset", "openDataAccess", true));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertEquals(true, body.get("public"));
      }
    }

    @Test
    @DisplayName("defaults public to false when openDataAccess missing or false")
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
    @DisplayName("sets public on body and captures previousPublic in compensationData")
    void shouldSetPublicAndCapturePreviousPublic() {
      try (FrostSagaHandler handler = createHandler()) {
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(
                Map.of("name", "Old Name", "description", "Old Description", "public", false));
        when(mockBuilder.get()).thenReturn(getResponse);

        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Entity> captor = ArgumentCaptor.forClass(Entity.class);
        when(mockBuilder.method(eq("PATCH"), captor.capture())).thenReturn(patchResponse);

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
        assertEquals(true, body.get("public"));
        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(false, result.compensationData().get("previousPublic"));
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
    @DisplayName("returns COMPENSATION_COMPLETED on compensate delete")
    void shouldReturnCompensationSuccessOnCompensateDelete() {
      try (FrostSagaHandler handler = createHandler()) {
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
    @DisplayName("returns COMPENSATION_FAILED on compensate error")
    void shouldReturnCompensationFailureOnError() {
      try (FrostSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(404);
        when(mockResponse.readEntity(String.class)).thenReturn("Not Found");
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand("COMPENSATE_STEP", "DELETE_PROJECT", Map.of("projectId", "999"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
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
    @DisplayName("restores previousPublic in body when present in payload")
    void shouldRestorePreviousPublicWhenPresent() {
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
                    "previousDescription", "Old Description",
                    "previousPublic", true));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) captor.getValue().getEntity();
        assertEquals(true, body.get("public"));
      }
    }

    @Test
    @DisplayName("omits public from body when previousPublic missing (back-compat)")
    void shouldOmitPublicWhenPreviousPublicMissing() {
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
        assertNull(body.get("public"));
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
    WebTarget mockTarget = mock(WebTarget.class);
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
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-project", "frost", operation, payload);
  }
}

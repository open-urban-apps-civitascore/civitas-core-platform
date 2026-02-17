/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.adapter.SagaCommandMessage;
import com.civitas.configadapter.adapter.SagaCommandResult;
import com.civitas.configadapter.configuration.AdapterConfig;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
  @DisplayName("initialize throws when API key is missing")
  void shouldThrowWhenApiKeyMissing() {
    try (FrostSagaHandler h = new FrostSagaHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty("frost.url", "http://localhost:8080/v1.1"))
          .thenReturn("http://frost:8080/v1.1");
      when(config.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");

      assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
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
        assertNotNull(result.resultData().get("baseUrl"));
        assertEquals("42", result.compensationData().get("projectId"));
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
        assertEquals("42", result.compensationData().get("projectId"));
        assertEquals("Old Name", result.compensationData().get("previousName"));
        assertEquals("Old Description", result.compensationData().get("previousDescription"));
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
    FrostSagaHandler handler = new FrostSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("frost.api.key")).thenReturn("test-api-key");
    when(mockConfig.getProperty("frost.url", "http://localhost:8080/v1.1"))
        .thenReturn("http://frost:8080/v1.1");
    when(mockConfig.getProperty("frost.api.key.header", "X-API-Key")).thenReturn("X-API-Key");
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    WebTarget mockTarget = mock(WebTarget.class);
    WebTarget mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
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

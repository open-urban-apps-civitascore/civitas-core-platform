/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

class ApisixSagaHandlerTest {

  private Invocation.Builder mockBuilder;

  @Test
  @DisplayName("adapter() returns 'apisix'")
  void shouldReturnApisixAdapterName() {
    try (ApisixSagaHandler handler = createHandler()) {
      assertEquals("apisix", handler.adapter());
    }
  }

  @Test
  @DisplayName("initialize throws when admin key is missing")
  void shouldThrowWhenAdminKeyMissing() {
    try (ApisixSagaHandler h = new ApisixSagaHandler()) {
      AdapterConfig config = mock(AdapterConfig.class);
      when(config.getProperty("apisix.admin.url", "http://localhost:9180"))
          .thenReturn("http://apisix:9180");

      assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
    }
  }

  @Nested
  @DisplayName("CREATE_ROUTE")
  class CreateRoute {

    @Test
    @DisplayName("creates upstream and route, returns routeId and serviceId")
    void shouldCreateRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of("datasetId", "ds-001", "upstreamUrl", "frost:8080", "openDataAccess", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds-001", result.resultData().get("routeId"));
        assertEquals("ds-001", result.resultData().get("serviceId"));
        assertNotNull(result.resultData().get("publicUrl"));
        assertEquals("ds-001", result.compensationData().get("routeId"));
        assertEquals("ds-001", result.compensationData().get("serviceId"));
      }
    }

    @Test
    @DisplayName("sets plugin_config_id when openDataAccess is false")
    void shouldSetPluginConfigIdForNonOpenData() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of(
                    "datasetId", "ds-001", "upstreamUrl", "frost:8080", "openDataAccess", false));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("returns failure on HTTP error")
    void shouldReturnFailureOnHttpError() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(400);
        when(mockResponse.readEntity(String.class)).thenReturn("Bad Request");
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of("datasetId", "ds-001", "upstreamUrl", "frost:8080"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns failure on network error")
    void shouldReturnFailureOnNetworkError() {
      try (ApisixSagaHandler handler = createHandler()) {
        when(mockBuilder.put(any(Entity.class)))
            .thenThrow(new ProcessingException("Connection refused"));

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of("datasetId", "ds-001", "upstreamUrl", "frost:8080"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_ROUTE")
  class UpdateRoute {

    @Test
    @DisplayName("updates route and returns routeId and serviceId")
    void shouldUpdateRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds-001", result.resultData().get("routeId"));
        assertEquals("ds-001", result.resultData().get("serviceId"));
      }
    }
  }

  @Nested
  @DisplayName("DELETE_ROUTE")
  class DeleteRoute {

    @Test
    @DisplayName("deletes route and upstream on forward delete")
    void shouldDeleteRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP", "DELETE_ROUTE", Map.of("routeId", "ds-001", "serviceId", "ds-001"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on compensate delete")
    void shouldReturnCompensationSuccessOnCompensateDelete() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "DELETE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on compensate error")
    void shouldReturnCompensationFailureOnError() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(404);
        when(mockResponse.readEntity(String.class)).thenReturn("Not Found");
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "DELETE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("Unknown operation")
  class UnknownOperation {

    @Test
    @DisplayName("returns STEP_FAILED for unknown forward operation")
    void shouldReturnFailureForUnknownOperation() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("EXECUTE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED for unknown compensate operation")
    void shouldReturnCompensationFailureForUnknownOperation() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandMessage command = createCommand("COMPENSATE_STEP", "UNKNOWN_OP", Map.of());

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }
  }

  private ApisixSagaHandler createHandler() {
    return createHandlerWithPluginConfig(null);
  }

  private ApisixSagaHandler createHandlerWithPluginConfig(String pluginConfig) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://apisix:9180");
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn(pluginConfig);
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    WebTarget mockTarget = mock(WebTarget.class);
    WebTarget mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);

    handler.setClient(mockClient);
    return handler;
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-route", "apisix", operation, payload);
  }
}

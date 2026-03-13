/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)",
                    "openDataAccess",
                    true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds-001", result.resultData().get("routeId"));
        assertEquals("ds-001", result.resultData().get("serviceId"));
        assertEquals("http://gateway:9080/datasets/ds-001", result.resultData().get("publicUrl"));
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
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)",
                    "openDataAccess",
                    false));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("includes service_id in route body when configured")
    void shouldIncludeServiceIdInRouteBody() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)",
                    "openDataAccess",
                    true));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(entityCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> routeBody = entityCaptor.getAllValues().get(1).getEntity();
        assertEquals("svc-frost-server", routeBody.get("service_id"));
      }
    }

    @Test
    @DisplayName("omits service_id from route body when not configured")
    void shouldOmitServiceIdWhenNotConfigured() {
      try (ApisixSagaHandler handler = createHandlerWithConfig(null, null)) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)",
                    "openDataAccess",
                    true));

        handler.handle(command);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(entityCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> routeBody = entityCaptor.getAllValues().get(1).getEntity();
        assertFalse(routeBody.containsKey("service_id"));
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
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)"));

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
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost:8080/FROST-Server/v1.1/Projects(1)"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns failure for invalid upstream URL")
    void shouldReturnFailureForInvalidUpstreamUrl() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of("datasetId", "ds-001", "upstreamUrl", "://not a valid uri"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("handles upstream URL without port")
    void shouldHandleUpstreamUrlWithoutPort() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of(
                    "datasetId",
                    "ds-001",
                    "upstreamUrl",
                    "http://frost/FROST-Server/v1.1/Projects(1)"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("handles upstream URL without path")
    void shouldHandleUpstreamUrlWithoutPath() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of("datasetId", "ds-001", "upstreamUrl", "http://frost:8080"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_ROUTE")
  class UpdateRoute {

    @Test
    @DisplayName("updates route and returns routeId, serviceId and previous state")
    void shouldUpdateRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Mock GET for reading current route state (route has plugin_config_id → not open data)
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class))
            .thenReturn(
                Map.of(
                    "value",
                    Map.of(
                        "uri", "/datasets/ds-001/*",
                        "plugin_config_id", "auth-plugin-1")));
        when(mockBuilder.get()).thenReturn(getResponse);

        // Mock PATCH for the update
        Response patchResponse = mock(Response.class);
        when(patchResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(any(String.class), any(Entity.class))).thenReturn(patchResponse);

        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals("ds-001", result.resultData().get("routeId"));
        assertEquals("ds-001", result.resultData().get("serviceId"));
        assertEquals(false, result.compensationData().get("previousOpenDataAccess"));
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
  @DisplayName("RESTORE_ROUTE")
  class RestoreRoute {

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED on successful restore")
    void shouldRestoreRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockBuilder.method(any(String.class), any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_ROUTE",
                Map.of(
                    "routeId", "ds-001",
                    "serviceId", "ds-001",
                    "previousOpenDataAccess", false));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on HTTP error")
    void shouldReturnCompensationFailureOnError() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.method(any(String.class), any(Entity.class))).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_ROUTE",
                Map.of(
                    "routeId", "ds-001",
                    "serviceId", "ds-001",
                    "previousOpenDataAccess", true));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on network error")
    void shouldReturnCompensationFailureOnNetworkError() {
      try (ApisixSagaHandler handler = createHandler()) {
        when(mockBuilder.method(any(String.class), any(Entity.class)))
            .thenThrow(new ProcessingException("Connection refused"));

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "RESTORE_ROUTE",
                Map.of(
                    "routeId", "ds-001",
                    "serviceId", "ds-001",
                    "previousOpenDataAccess", false));

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

  @Nested
  @DisplayName("Proxy-rewrite regex pattern")
  class ProxyRewriteRegex {

    /**
     * Evaluates the regex pattern used in buildCreateRouteBody: {@code ^/datasets/{id}(/.*)?$} →
     * {@code {upstreamPath}$1}
     */
    private String applyRewrite(String datasetId, String upstreamPath, String requestPath) {
      String regex = "^/datasets/" + datasetId + "(/.*)?$";
      String replacement = upstreamPath + "$1";
      Matcher matcher = Pattern.compile(regex).matcher(requestPath);
      if (!matcher.matches()) {
        return null;
      }
      return matcher.replaceFirst(replacement);
    }

    @Test
    @DisplayName("rewrites sub-path to upstream path")
    void shouldRewriteSubPath() {
      String result =
          applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", "/datasets/ds-001/Things");
      assertEquals("/FROST-Server/v1.1/Projects(1)/Things", result);
    }

    @Test
    @DisplayName("rewrites nested sub-path")
    void shouldRewriteNestedSubPath() {
      String result =
          applyRewrite(
              "ds-001",
              "/FROST-Server/v1.1/Projects(1)",
              "/datasets/ds-001/Things(42)/Datastreams");
      assertEquals("/FROST-Server/v1.1/Projects(1)/Things(42)/Datastreams", result);
    }

    @Test
    @DisplayName("rewrites base path without trailing slash")
    void shouldRewriteBasePathWithoutTrailingSlash() {
      String result = applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", "/datasets/ds-001");
      assertEquals("/FROST-Server/v1.1/Projects(1)", result);
    }

    @Test
    @DisplayName("rewrites base path with trailing slash")
    void shouldRewriteBasePathWithTrailingSlash() {
      String result = applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", "/datasets/ds-001/");
      assertEquals("/FROST-Server/v1.1/Projects(1)/", result);
    }

    @Test
    @DisplayName("does not match different dataset ID")
    void shouldNotMatchDifferentDatasetId() {
      String result =
          applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", "/datasets/ds-002/Things");
      assertNull(result);
    }

    @Test
    @DisplayName("does not match unrelated path")
    void shouldNotMatchUnrelatedPath() {
      String result = applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", "/api/v1/users");
      assertNull(result);
    }

    @Test
    @DisplayName("rewrites with query string in path")
    void shouldRewriteWithQueryString() {
      String result =
          applyRewrite(
              "ds-001",
              "/FROST-Server/v1.1/Projects(1)",
              "/datasets/ds-001/Things?$top=10&$skip=0");
      assertEquals("/FROST-Server/v1.1/Projects(1)/Things?$top=10&$skip=0", result);
    }
  }

  private ApisixSagaHandler createHandler() {
    return createHandlerWithConfig(null, "svc-frost-server");
  }

  private ApisixSagaHandler createHandlerWithPluginConfig(String pluginConfig) {
    return createHandlerWithConfig(pluginConfig, "svc-frost-server");
  }

  private ApisixSagaHandler createHandlerWithConfig(String pluginConfig, String serviceId) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://apisix:9180");
    when(mockConfig.getProperty("apisix.gateway.url", "http://localhost:9080"))
        .thenReturn("http://gateway:9080");
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn(pluginConfig);
    when(mockConfig.getProperty("apisix.service.id")).thenReturn(serviceId);
    handler.initialize(mockConfig);

    Client mockClient = mock(Client.class);
    WebTarget mockTarget = mock(WebTarget.class);
    WebTarget mockPathTarget = mock(WebTarget.class);
    mockBuilder = mock(Invocation.Builder.class);

    when(mockClient.target(any(String.class))).thenReturn(mockTarget);
    when(mockTarget.path(any(String.class))).thenReturn(mockPathTarget);
    when(mockPathTarget.request(MediaType.APPLICATION_JSON)).thenReturn(mockBuilder);
    when(mockBuilder.header(any(String.class), any())).thenReturn(mockBuilder);
    when(mockBuilder.method(any(String.class), any(Entity.class))).thenReturn(mock(Response.class));

    handler.setTestClient(mockClient);
    return handler;
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-route", "apisix", operation, payload);
  }
}

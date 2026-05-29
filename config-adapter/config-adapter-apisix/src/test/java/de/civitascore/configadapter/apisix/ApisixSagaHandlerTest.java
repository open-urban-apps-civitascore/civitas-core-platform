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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

  @Nested
  @DisplayName("Initialization")
  class Initialization {

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

    @ParameterizedTest(name = "initialize fails fast when {0} is missing")
    @ValueSource(
        strings = {
          "apisix.api.host",
          "apisix.api.public.url",
          "apisix.plugin.config.id",
          "apisix.frost.basic.auth.username",
          "apisix.frost.basic.auth.password"
        })
    void shouldFailInitializeWhenRequiredPropertyMissing(String missingProperty) {
      try (ApisixSagaHandler h = new ApisixSagaHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
        when(config.getProperty("apisix.admin.url", "http://localhost:9180"))
            .thenReturn("http://apisix:9180");
        when(config.getProperty("apisix.frost.api.key.header", "X-API-Key"))
            .thenReturn("X-API-Key");
        if (!"apisix.api.host".equals(missingProperty)) {
          when(config.getProperty("apisix.api.host")).thenReturn("api.example.test");
        }
        if (!"apisix.api.public.url".equals(missingProperty)) {
          when(config.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
        }
        if (!"apisix.plugin.config.id".equals(missingProperty)) {
          when(config.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
        }
        if (!"apisix.frost.basic.auth.username".equals(missingProperty)) {
          when(config.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
        }
        if (!"apisix.frost.basic.auth.password".equals(missingProperty)) {
          when(config.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
        }

        assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
      }
    }

    @Test
    @DisplayName("accepts API key auth as alternative to Basic Auth")
    void shouldAcceptApiKeyAuthInsteadOfBasicAuth() {
      try (ApisixSagaHandler h = new ApisixSagaHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
        when(config.getProperty("apisix.admin.url", "http://localhost:9180"))
            .thenReturn("http://apisix:9180");
        when(config.getProperty("apisix.api.host")).thenReturn("api.example.test");
        when(config.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
        when(config.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
        when(config.getProperty("apisix.frost.api.key")).thenReturn("test-api-key");
        when(config.getProperty("apisix.frost.api.key.header", "X-API-Key"))
            .thenReturn("X-API-Key");

        h.initialize(config);
      }
    }

    @Test
    @DisplayName("fails fast when API key auth is configured with a blank header name")
    void shouldFailWhenApiKeyHeaderIsBlank() {
      try (ApisixSagaHandler h = new ApisixSagaHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
        when(config.getProperty("apisix.admin.url", "http://localhost:9180"))
            .thenReturn("http://apisix:9180");
        when(config.getProperty("apisix.api.host")).thenReturn("api.example.test");
        when(config.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
        when(config.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
        when(config.getProperty("apisix.frost.api.key")).thenReturn("frost-key");
        // Header name explicitly blank — would otherwise produce headers.set[""]: "<key>".
        when(config.getProperty("apisix.frost.api.key.header", "X-API-Key")).thenReturn("");

        assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
      }
    }

    @Test
    @DisplayName("fails fast when neither Basic Auth nor API key is configured")
    void shouldFailWhenNoUpstreamAuthConfigured() {
      try (ApisixSagaHandler h = new ApisixSagaHandler()) {
        AdapterConfig config = mock(AdapterConfig.class);
        when(config.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
        when(config.getProperty("apisix.admin.url", "http://localhost:9180"))
            .thenReturn("http://apisix:9180");
        when(config.getProperty("apisix.api.host")).thenReturn("api.example.test");
        when(config.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
        when(config.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
        when(config.getProperty("apisix.frost.api.key.header", "X-API-Key"))
            .thenReturn("X-API-Key");

        assertThrows(IllegalArgumentException.class, () -> h.initialize(config));
      }
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
        assertEquals(
            "https://api.example.test/v1/datasets/ds-001", result.resultData().get("publicUrl"));
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
        Map<String, Object> routeBody = entityCaptor.getValue().getEntity();
        assertEquals("svc-frost-server", routeBody.get("service_id"));
      }
    }

    @Test
    @DisplayName("omits service_id from route body when not configured")
    void shouldOmitServiceIdWhenNotConfigured() {
      try (ApisixSagaHandler handler = createHandlerWithConfig("auth-plugin-default", null)) {
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
        Map<String, Object> routeBody = entityCaptor.getValue().getEntity();
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
  @DisplayName("CREATE_ROUTE pins API host")
  class CreateRouteHostPinning {

    private SagaCommandMessage createRouteCommand() {
      return createCommand(
          "EXECUTE_STEP",
          "CREATE_ROUTE",
          Map.of(
              "datasetId",
              "ds-001",
              "upstreamUrl",
              "http://frost:8080/FROST-Server/v1.1/Projects(1)",
              "openDataAccess",
              true));
    }

    private void stubPutCreated() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);
    }

    @Test
    @DisplayName(
        "pins api host, uses /v1/datasets/{id} layout, rewrites to FROST upstream and returns publicUrl")
    void shouldBuildRouteBodyForApiHost() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        SagaCommandResult result = handler.handle(createRouteCommand());

        Map<String, Object> routeBody = captureRouteBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite =
            (Map<String, Object>)
                ((Map<String, Object>) routeBody.get("plugins")).get("proxy-rewrite");

        assertAll(
            () ->
                assertEquals(
                    "[api.example.test]",
                    Arrays.toString((String[]) routeBody.get("hosts")),
                    "route body must carry hosts to pin saga route to configured API host"),
            () ->
                assertEquals(
                    "[/v1/datasets/ds-001, /v1/datasets/ds-001/*]",
                    Arrays.toString((String[]) routeBody.get("uris"))),
            () ->
                assertEquals(
                    "[^/v1/datasets/ds-001(/.*)?$, /FROST-Server/v1.1/Projects(1)$1]",
                    Arrays.toString((String[]) proxyRewrite.get("regex_uri"))),
            () ->
                assertEquals(
                    "https://api.example.test/v1/datasets/ds-001",
                    result.resultData().get("publicUrl")));
      }
    }

    private Map<String, Object> captureRouteBody() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
          ArgumentCaptor.forClass(Entity.class);
      verify(mockBuilder, times(2)).put(entityCaptor.capture());
      return entityCaptor.getValue().getEntity();
    }
  }

  @Nested
  @DisplayName("CREATE_ROUTE injects FROST upstream Basic Auth header")
  class CreateRouteFrostUpstreamAuth {

    private void stubPutCreated() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);
    }

    private SagaCommandMessage createRouteCommand(boolean openDataAccess) {
      return createCommand(
          "EXECUTE_STEP",
          "CREATE_ROUTE",
          Map.of(
              "datasetId",
              "ds-001",
              "upstreamUrl",
              "http://frost:8080/FROST-Server/v1.1/Projects(1)",
              "openDataAccess",
              openDataAccess));
    }

    private Map<String, Object> captureProxyRewrite() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
          ArgumentCaptor.forClass(Entity.class);
      verify(mockBuilder, times(2)).put(entityCaptor.capture());
      Map<String, Object> routeBody = entityCaptor.getValue().getEntity();
      @SuppressWarnings("unchecked")
      Map<String, Object> plugins = (Map<String, Object>) routeBody.get("plugins");
      @SuppressWarnings("unchecked")
      Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
      return proxyRewrite;
    }

    @Test
    @DisplayName("injects Basic Auth header into proxy-rewrite when openDataAccess=false")
    void shouldInjectAuthHeaderForPrivateProject() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        handler.handle(createRouteCommand(false));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) headers.get("set");
        String expected =
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("frost-user:frost-pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, set.get("Authorization"));
      }
    }

    @Test
    @DisplayName("does not inject Basic Auth header when openDataAccess=true")
    void shouldNotInjectAuthHeaderForPublicProject() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        handler.handle(createRouteCommand(true));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        assertFalse(proxyRewrite.containsKey("headers"));
      }
    }

    @Test
    @DisplayName("merges configured proxy-rewrite headers.remove into route-level plugin")
    void shouldMergeProxyRewriteHeadersRemoveIntoRoute() {
      try (ApisixSagaHandler handler =
          createHandlerWithHeadersRemove("X-Allowed-Scope-Ids,X-Some-Other")) {
        stubPutCreated();

        handler.handle(createRouteCommand(false));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        Object remove = headers.get("remove");
        assertEquals(
            List.of("X-Allowed-Scope-Ids", "X-Some-Other"),
            remove instanceof String[] arr ? Arrays.asList(arr) : remove,
            "route-level proxy-rewrite must strip the configured headers (Finding P1 — plugin"
                + " config's proxy-rewrite is overridden by route precedence)");
      }
    }

    @Test
    @DisplayName("strips configured headers for PUBLIC routes too (no FROST auth header set)")
    void shouldStripHeadersForPublicRoutesEvenWithoutAuth() {
      try (ApisixSagaHandler handler = createHandlerWithHeadersRemove("X-Allowed-Scope-Ids")) {
        stubPutCreated();

        handler.handle(createRouteCommand(true));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        assertNotNull(
            headers,
            "public routes must still strip client-supplied internal headers (Finding —"
                + " strip is a general saga-route protection, not auth-specific)");
        Object remove = headers.get("remove");
        assertEquals(
            List.of("X-Allowed-Scope-Ids"),
            remove instanceof String[] arr ? Arrays.asList(arr) : remove);
        assertFalse(
            headers.containsKey("set"),
            "public routes must NOT carry headers.set (no FROST upstream auth)");
      }
    }

    @Test
    @DisplayName("injects API key header (not Authorization) when configured with API key auth")
    void shouldInjectApiKeyHeaderWhenConfigured() {
      try (ApisixSagaHandler handler =
          createHandlerWithApiKeyAuth("apisix-test-key", "X-API-Key")) {
        stubPutCreated();

        handler.handle(createRouteCommand(false));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) headers.get("set");
        assertEquals("apisix-test-key", set.get("X-API-Key"));
        assertFalse(set.containsKey("Authorization"));
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_ROUTE")
  class UpdateRoute {

    private void stubGetReturning(Map<String, Object> route) {
      Map<String, Object> wrapper = new HashMap<>();
      wrapper.put("value", route);
      Response getResponse = mock(Response.class);
      when(getResponse.getStatus()).thenReturn(200);
      when(getResponse.readEntity(Map.class)).thenReturn(wrapper);
      when(mockBuilder.get()).thenReturn(getResponse);
    }

    private void stubPutOk() {
      Response putResponse = mock(Response.class);
      when(putResponse.getStatus()).thenReturn(200);
      when(mockBuilder.put(any(Entity.class))).thenReturn(putResponse);
    }

    private Map<String, Object> capturePutBody() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
      verify(mockBuilder).put(captor.capture());
      return captor.getValue().getEntity();
    }

    @Test
    @DisplayName("switching private→public removes plugin_config_id and Authorization header")
    void shouldSwitchFromPrivateToPublic() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(true));
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(false, result.compensationData().get("previousOpenDataAccess"));

        Map<String, Object> body = capturePutBody();
        assertFalse(body.containsKey("plugin_config_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        assertFalse(proxyRewrite.containsKey("headers"));
      }
    }

    @Test
    @DisplayName("switching public→private sets plugin_config_id and Authorization header")
    void shouldSwitchFromPublicToPrivate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(false));
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", false)));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(true, result.compensationData().get("previousOpenDataAccess"));

        Map<String, Object> body = capturePutBody();
        assertEquals("auth-plugin-1", body.get("plugin_config_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) headers.get("set");
        String expected =
            "Basic "
                + Base64.getEncoder()
                    .encodeToString("frost-user:frost-pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, set.get("Authorization"));
      }
    }

    @Test
    @DisplayName("strips read-only fields (create_time, update_time) from the PUT body")
    void shouldStripReadOnlyFields() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(true));
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        assertFalse(body.containsKey("create_time"));
        assertFalse(body.containsKey("update_time"));
      }
    }

    @Test
    @DisplayName("detects legacy private route (plugin_config_id without headers) as private")
    void shouldDetectLegacyPrivateRouteAsPreviouslyPrivate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Legacy route: pre-PR layout — plugin_config_id is set but no upstream headers yet.
        Map<String, Object> legacy = existingRoute(false);
        legacy.put("plugin_config_id", "auth-plugin-1");
        stubGetReturning(legacy);
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        assertEquals(
            false,
            result.compensationData().get("previousOpenDataAccess"),
            "legacy private route (plugin_config_id only) must be detected as previously private");
      }
    }

    @Test
    @DisplayName("does not misclassify public route with foreign headers.set entries as private")
    void shouldNotMisclassifyPublicRouteWithForeignHeadersSetAsPrivate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Public route (no plugin_config_id, no adapter label) but with an operator-added
        // headers.set entry. The old heuristic flagged any headers.set as private and produced
        // wrong compensation data on the next flip.
        Map<String, Object> publicRoute = existingRoute(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) publicRoute.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        Map<String, Object> foreignSet = new HashMap<>();
        foreignSet.put("X-Trace-Id", "trace-123");
        Map<String, Object> foreignHeaders = new HashMap<>();
        foreignHeaders.put("set", foreignSet);
        proxyRewrite.put("headers", foreignHeaders);
        stubGetReturning(publicRoute);
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        assertEquals(
            true,
            result.compensationData().get("previousOpenDataAccess"),
            "public route must stay classified as public regardless of foreign headers.set"
                + " entries (Finding P2 — only plugin_config_id is authoritative)");
      }
    }

    @Test
    @DisplayName("cleans custom API-key header on public flip when adapter is currently using it")
    void shouldCleanCurrentlyConfiguredCustomHeaderOnPublicFlip() {
      // Migration-positive: pre-label route used the custom header X-Frost-Key, and the adapter
      // is STILL configured with that custom header. The standard
      // `set.remove(frostUpstreamAuthHeaderName)` covers this case end-to-end. Operators with
      // historical custom headers who want a clean migration: trigger an UPDATE while still on
      // the historical configuration (then optionally rotate to a different header afterwards).
      // See LEGACY_ADAPTER_AUTH_HEADERS javadoc for the documented limitation.
      try (ApisixSagaHandler handler =
          createHandlerWithApiKeyAuth("frost-key-val", "X-Frost-Key")) {
        Map<String, Object> legacy = existingRoute(false);
        legacy.put("plugin_config_id", "auth-plugin-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) legacy.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        Map<String, Object> set = new HashMap<>();
        set.put("X-Frost-Key", "stale-key-value");
        set.put("X-Trace-Id", "trace-123");
        Map<String, Object> headers = new HashMap<>();
        headers.put("set", set);
        proxyRewrite.put("headers", headers);
        // No label — represents pre-label adapter version.
        stubGetReturning(legacy);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertFalse(
            resultSet.containsKey("X-Frost-Key"),
            "currently-configured custom API-key header must be cleaned on public flip"
                + " — got headers.set: "
                + resultSet);
        assertEquals("trace-123", resultSet.get("X-Trace-Id"), "foreign entries survive");
      }
    }

    @Test
    @DisplayName("strips legacy Authorization on already-public/inconsistent route")
    void shouldStripLegacyAuthHeaderEvenOnAlreadyPublicRoute() {
      // Defense in depth: route has no plugin_config_id (so already "public" by our authoritative
      // signal) but still carries a stale Authorization in headers.set — left over from a buggy
      // earlier write or external mutation. An UPDATE to public must scrub it regardless.
      try (ApisixSagaHandler handler = createHandlerWithApiKeyAuth("apikey", "X-API-Key")) {
        Map<String, Object> route = existingRoute(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) route.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        Map<String, Object> set = new HashMap<>();
        set.put("Authorization", "Basic stale-creds");
        set.put("X-Trace-Id", "trace-123");
        Map<String, Object> headers = new HashMap<>();
        headers.put("set", set);
        proxyRewrite.put("headers", headers);
        stubGetReturning(route);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertFalse(
            resultSet.containsKey("Authorization"),
            "public routes must never carry FROST credentials, even from inconsistent prior state");
        assertEquals(
            "trace-123",
            resultSet.get("X-Trace-Id"),
            "non-auth foreign entries are still preserved");
      }
    }

    @Test
    @DisplayName("removes unlabeled legacy Authorization header on public flip (migration)")
    void shouldRemoveUnlabeledLegacyAuthHeaderOnGoingPublic() {
      // Migration scenario: route was provisioned by a pre-label version of this adapter, so it
      // carries plugin_config_id + headers.set.Authorization but no civitas-frost-upstream-auth-
      // header label. Adapter is now configured with API key scheme. Without a fallback list of
      // well-known adapter-managed header names, the stale Authorization would survive the flip.
      try (ApisixSagaHandler handler = createHandlerWithApiKeyAuth("apikey-value", "X-API-Key")) {
        Map<String, Object> legacy = existingRoute(false);
        legacy.put("plugin_config_id", "auth-plugin-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) legacy.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        Map<String, Object> set = new HashMap<>();
        set.put("Authorization", "Basic stale-creds");
        set.put("X-Trace-Id", "trace-123");
        Map<String, Object> headers = new HashMap<>();
        headers.put("set", set);
        proxyRewrite.put("headers", headers);
        // No labels block at all — represents pre-label adapter version.
        stubGetReturning(legacy);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertFalse(
            resultSet.containsKey("Authorization"),
            "legacy unlabeled Authorization must be wiped on public flip — got headers.set: "
                + resultSet);
        assertEquals(
            "trace-123",
            resultSet.get("X-Trace-Id"),
            "foreign entries must still survive the migration cleanup");
      }
    }

    @Test
    @DisplayName("removes stale adapter-managed auth header from previous scheme on public flip")
    void shouldRemoveStaleAuthHeaderFromPreviousSchemeOnGoingPublic() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Route was provisioned with API-Key scheme earlier (label says "X-Old-Key"), the
        // operator then reconfigured the adapter to Basic Auth ("Authorization"). On public
        // flip the stale X-Old-Key entry would have remained without label-driven cleanup.
        Map<String, Object> existing = existingRoute(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) existing.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> innerSet = (Map<String, Object>) set.get("set");
        innerSet.clear();
        innerSet.put("X-Old-Key", "stale-key-value");
        innerSet.put("X-Trace-Id", "trace-123");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) existing.get("labels");
        labels.put("civitas-frost-upstream-auth-header", "X-Old-Key");
        stubGetReturning(existing);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertFalse(
            resultSet.containsKey("X-Old-Key"),
            "stale adapter-managed header (label-tracked) must be removed on public flip"
                + " — got headers.set: "
                + resultSet);
        assertEquals("trace-123", resultSet.get("X-Trace-Id"), "foreign entries stay");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultLabels = (Map<String, Object>) body.get("labels");
        if (resultLabels != null) {
          assertFalse(
              resultLabels.containsKey("civitas-frost-upstream-auth-header"),
              "label tracking the adapter-managed header must be cleared on public flip");
        }
      }
    }

    @Test
    @DisplayName("rotates labeled auth header when scheme changes while staying private")
    void shouldRotateLabeledAuthHeaderOnSchemeChangeStayingPrivate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Same scheme-change scenario but the UPDATE keeps the route private. Adapter must
        // drop the stale header from the old scheme and set the new one in lockstep.
        Map<String, Object> existing = existingRoute(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) existing.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> headersMap = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> innerSet = (Map<String, Object>) headersMap.get("set");
        innerSet.clear();
        innerSet.put("X-Old-Key", "stale-key-value");
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) existing.get("labels");
        labels.put("civitas-frost-upstream-auth-header", "X-Old-Key");
        stubGetReturning(existing);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", false)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertFalse(
            resultSet.containsKey("X-Old-Key"), "stale header from previous scheme must be gone");
        assertTrue(
            resultSet.containsKey("Authorization"),
            "new scheme's header must be set — got headers.set: " + resultSet);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultLabels = (Map<String, Object>) body.get("labels");
        assertEquals(
            "Authorization",
            resultLabels.get("civitas-frost-upstream-auth-header"),
            "label must follow the currently configured header name");
      }
    }

    @Test
    @DisplayName("detects private route with numeric plugin_config_id as private")
    void shouldDetectNumericPluginConfigIdAsPrivate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // APISIX returns plugin_config_id as Integer when it was originally PUT with a numeric
        // literal (allowed by RouteConfigValue). The detection must not be String-only.
        Map<String, Object> legacy = existingRoute(false);
        legacy.put("plugin_config_id", 1);
        stubGetReturning(legacy);
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        assertEquals(
            false,
            result.compensationData().get("previousOpenDataAccess"),
            "numeric plugin_config_id must also count as private (Finding P2 — Integer values"
                + " allowed per RouteConfigValue.java)");
      }
    }

    @Test
    @DisplayName("preserves foreign headers.set/add entries and merges adapter-managed ones")
    void shouldMergeHeadersInsteadOfReplacingBlock() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Existing route with user-defined header rules unrelated to FROST auth or scope strip.
        Map<String, Object> existing = existingRoute(false);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) existing.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        Map<String, Object> userSet = new HashMap<>();
        userSet.put("X-Trace-Id", "trace-123");
        Map<String, Object> userAdd = new HashMap<>();
        userAdd.put("X-Forwarded-For", "$remote_addr");
        Map<String, Object> userHeaders = new HashMap<>();
        userHeaders.put("set", userSet);
        userHeaders.put("add", userAdd);
        userHeaders.put("remove", new ArrayList<>(List.of("X-User-Strip")));
        proxyRewrite.put("headers", userHeaders);
        stubGetReturning(existing);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", false)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertEquals(
            "trace-123",
            resultSet.get("X-Trace-Id"),
            "foreign headers.set entries must survive UPDATE flip");
        assertNotNull(
            resultSet.get("Authorization"), "adapter-managed Authorization header is still set");

        @SuppressWarnings("unchecked")
        Map<String, Object> resultAdd = (Map<String, Object>) resultHeaders.get("add");
        assertEquals(
            "$remote_addr",
            resultAdd.get("X-Forwarded-For"),
            "foreign headers.add entries are not touched by the adapter");

        Object resultRemove = resultHeaders.get("remove");
        List<?> removeList =
            resultRemove instanceof String[] arr ? Arrays.asList(arr) : (List<?>) resultRemove;
        assertTrue(
            removeList.contains("X-User-Strip"),
            "user-defined headers.remove entries must survive the merge");
      }
    }

    @Test
    @DisplayName("removes only the adapter-managed Authorization header when going public")
    void shouldOnlyRemoveAdapterManagedHeaderOnGoingPublic() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Map<String, Object> existing = existingRoute(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) existing.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> set = (Map<String, Object>) headers.get("set");
        set.put("X-Trace-Id", "trace-123");
        stubGetReturning(existing);
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPlugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultPr = (Map<String, Object>) resultPlugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultHeaders = (Map<String, Object>) resultPr.get("headers");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultSet = (Map<String, Object>) resultHeaders.get("set");
        assertEquals(
            "trace-123",
            resultSet.get("X-Trace-Id"),
            "foreign headers.set entries are kept even when stripping the adapter header");
        assertFalse(
            resultSet.containsKey("Authorization"),
            "adapter-managed Authorization header must be removed when going public");
      }
    }

    @Test
    @DisplayName("preserves existing route fields like uris, hosts, upstream_id")
    void shouldPreserveExistingRouteFields() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(true));
        stubPutOk();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of("routeId", "ds-001", "serviceId", "ds-001", "openDataAccess", true)));

        Map<String, Object> body = capturePutBody();
        assertEquals("ds-001", body.get("upstream_id"));
        assertEquals(1, body.get("status"));
        assertNotNull(body.get("uris"));
        assertNotNull(body.get("hosts"));
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

    private void stubGetReturning(Map<String, Object> route) {
      Map<String, Object> wrapper = new HashMap<>();
      wrapper.put("value", route);
      Response getResponse = mock(Response.class);
      when(getResponse.getStatus()).thenReturn(200);
      when(getResponse.readEntity(Map.class)).thenReturn(wrapper);
      when(mockBuilder.get()).thenReturn(getResponse);
    }

    private void stubPutOk() {
      Response putResponse = mock(Response.class);
      when(putResponse.getStatus()).thenReturn(200);
      when(mockBuilder.put(any(Entity.class))).thenReturn(putResponse);
    }

    private Map<String, Object> capturePutBody() {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
      verify(mockBuilder).put(captor.capture());
      return captor.getValue().getEntity();
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED and restores previous private state")
    void shouldRestorePreviousPrivateState() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(false));
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of(
                        "routeId", "ds-001",
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", false)));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());

        Map<String, Object> body = capturePutBody();
        assertEquals("auth-plugin-1", body.get("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED and restores previous public state")
    void shouldRestorePreviousPublicState() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(true));
        stubPutOk();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of(
                        "routeId", "ds-001",
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", true)));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        Map<String, Object> body = capturePutBody();
        assertFalse(body.containsKey("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on HTTP error")
    void shouldReturnCompensationFailureOnError() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        stubGetReturning(existingRoute(false));
        Response putResponse = mock(Response.class);
        when(putResponse.getStatus()).thenReturn(500);
        when(putResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of(
                        "routeId", "ds-001",
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", true)));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on network error")
    void shouldReturnCompensationFailureOnNetworkError() {
      try (ApisixSagaHandler handler = createHandler()) {
        when(mockBuilder.get()).thenThrow(new ProcessingException("Connection refused"));

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of(
                        "routeId", "ds-001",
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", false)));

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
     * Evaluates the regex pattern used in buildCreateRouteBody: {@code ^/v1/datasets/{id}(/.*)?$} →
     * {@code {upstreamPath}$1}
     */
    private String applyRewrite(String datasetId, String upstreamPath, String requestPath) {
      String regex = "^/v1/datasets/" + datasetId + "(/.*)?$";
      String replacement = upstreamPath + "$1";
      Matcher matcher = Pattern.compile(regex).matcher(requestPath);
      if (!matcher.matches()) {
        return null;
      }
      return matcher.replaceFirst(replacement);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        nullValues = "NULL",
        value = {
          "rewrites sub-path to upstream path | /v1/datasets/ds-001/Things | /FROST-Server/v1.1/Projects(1)/Things",
          "rewrites nested sub-path | /v1/datasets/ds-001/Things(42)/Datastreams | /FROST-Server/v1.1/Projects(1)/Things(42)/Datastreams",
          "rewrites base path without trailing slash | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1)",
          "rewrites base path with trailing slash | /v1/datasets/ds-001/ | /FROST-Server/v1.1/Projects(1)/",
          "rewrites with query string in path | /v1/datasets/ds-001/Things?$top=10&$skip=0 | /FROST-Server/v1.1/Projects(1)/Things?$top=10&$skip=0",
          "does not match different dataset ID | /v1/datasets/ds-002/Things | NULL",
          "does not match unrelated path | /api/v1/users | NULL",
          "does not match legacy /datasets path without /v1 prefix | /datasets/ds-001/Things | NULL",
        })
    void shouldApplyRewriteRegex(String description, String requestPath, String expected) {
      String result = applyRewrite("ds-001", "/FROST-Server/v1.1/Projects(1)", requestPath);
      assertEquals(expected, result);
    }
  }

  /**
   * Builds a mutable, JSON-shaped APISIX route response approximating what {@code readEntity(Map)}
   * would deliver — used by UPDATE_ROUTE/RESTORE_ROUTE tests to drive the GET→mutate→PUT flow.
   */
  private static Map<String, Object> existingRoute(boolean withAuth) {
    Map<String, Object> route = new HashMap<>();
    route.put("uris", new ArrayList<>(List.of("/v1/datasets/ds-001", "/v1/datasets/ds-001/*")));
    route.put("hosts", new ArrayList<>(List.of("api.example.test")));
    route.put("upstream_id", "ds-001");
    route.put("status", 1);

    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put(
        "regex_uri",
        new ArrayList<>(
            List.of("^/v1/datasets/ds-001(/.*)?$", "/FROST-Server/v1.1/Projects(1)$1")));
    if (withAuth) {
      Map<String, Object> set = new HashMap<>();
      set.put("Authorization", "Basic stale-value");
      Map<String, Object> headers = new HashMap<>();
      headers.put("set", set);
      proxyRewrite.put("headers", headers);
    }
    Map<String, Object> plugins = new HashMap<>();
    plugins.put("proxy-rewrite", proxyRewrite);
    route.put("plugins", plugins);

    if (withAuth) {
      route.put("plugin_config_id", "auth-plugin-1");
      Map<String, Object> labels = new HashMap<>();
      labels.put("civitas-frost-upstream-auth-header", "Authorization");
      route.put("labels", labels);
    }
    route.put("create_time", 1_700_000_000L);
    route.put("update_time", 1_700_000_001L);
    return route;
  }

  private ApisixSagaHandler createHandler() {
    return createHandlerWithConfig("auth-plugin-default", "svc-frost-server");
  }

  private ApisixSagaHandler createHandlerWithPluginConfig(String pluginConfig) {
    return createHandlerWithConfig(pluginConfig, "svc-frost-server");
  }

  private ApisixSagaHandler createHandlerWithHeadersRemove(String csvHeadersToRemove) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://apisix:9180");
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
    when(mockConfig.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
    when(mockConfig.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn("X-API-Key");
    when(mockConfig.getProperty("apisix.proxy.rewrite.headers.remove"))
        .thenReturn(csvHeadersToRemove);
    handler.initialize(mockConfig);
    wireMockClient(handler);
    return handler;
  }

  private ApisixSagaHandler createHandlerWithApiKeyAuth(String apiKey, String apiKeyHeader) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://apisix:9180");
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
    when(mockConfig.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.api.key")).thenReturn(apiKey);
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn(apiKeyHeader);
    handler.initialize(mockConfig);

    wireMockClient(handler);
    return handler;
  }

  private ApisixSagaHandler createHandlerWithConfig(String pluginConfig, String serviceId) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn("http://apisix:9180");
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn(pluginConfig);
    when(mockConfig.getProperty("apisix.service.id")).thenReturn(serviceId);
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
    when(mockConfig.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn("X-API-Key");
    handler.initialize(mockConfig);

    wireMockClient(handler);
    return handler;
  }

  private void wireMockClient(ApisixSagaHandler handler) {
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
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-route", "apisix", operation, payload);
  }
}

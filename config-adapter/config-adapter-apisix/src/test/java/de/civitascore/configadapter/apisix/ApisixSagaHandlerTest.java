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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
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
import java.util.LinkedHashMap;
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

  /**
   * The path-bearing {@link WebTarget} from {@link #wireMockClient}. Exposed so per-named-API tests
   * can capture every {@code .path(...)} argument and assert one APISIX route is provisioned per
   * slug (issue #1368, per-NamedApi route model).
   */
  private WebTarget mockTarget;

  @Test
  @DisplayName("adapter() returns 'apisix'")
  void shouldReturnApisixAdapterName() {
    try (ApisixSagaHandler handler = createHandler()) {
      assertEquals("apisix", handler.adapter());
    }
  }

  @Test
  @DisplayName("fieldAliases() declares baseUrl→upstreamUrl so the saga forwards FROST's baseUrl")
  void shouldDeclareBaseUrlAlias() {
    try (ApisixSagaHandler handler = createHandler()) {
      assertEquals(Map.of("baseUrl", "upstreamUrl"), handler.fieldAliases());
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

    // Migrated from the removed legacy single dataset-level route to the per-named-API model: each
    // command now carries one namedApi slug, so these tests exercise the same buildRouteBody /
    // upstream / error mechanics through the production per-slug path. A single-entry namedApis
    // list
    // keeps the assertions focused.
    private SagaCommandMessage createRouteCommand(Map<String, Object> extra) {
      Map<String, Object> payload = new HashMap<>(extra);
      payload.put("datasetId", "ds-001");
      payload.putIfAbsent("upstreamUrl", "http://frost:8080/FROST-Server/v1.1/Projects(1)");
      payload.put("namedApis", List.of(Map.of("slug", "data", "standard", "STA")));
      return createCommand("EXECUTE_STEP", "CREATE_ROUTE", payload);
    }

    @Test
    @DisplayName("creates upstream and one slug route, returns slug-keyed routeIds and serviceId")
    void shouldCreateRouteSuccessfully() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("openDataAccess", true)));

        assertEquals("STEP_COMPLETED", result.type());
        Map<String, String> expectedRouteIds =
            Map.of("data", NamedApiHelper.derive("ds-001", "data"));
        assertEquals(expectedRouteIds, result.resultData().get("routeIds"));
        assertEquals("ds-001", result.resultData().get("serviceId"));
        assertEquals(
            "https://api.example.test/v1/datasets/ds-001", result.resultData().get("publicUrl"));
        assertEquals(expectedRouteIds, result.compensationData().get("routeIds"));
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

        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("openDataAccess", false)));

        assertEquals("STEP_COMPLETED", result.type());
        // Verify the security-relevant effect, not just the step status: the route body must carry
        // plugin_config_id so the gateway enforces auth on the protected dataset.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(entityCaptor.capture()); // upstream + route
        Map<String, Object> routeBody = entityCaptor.getAllValues().get(1).getEntity();
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("includes service_id in route body when configured")
    void shouldIncludeServiceIdInRouteBody() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);

        handler.handle(createRouteCommand(Map.of("openDataAccess", true)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(entityCaptor.capture());
        Map<String, Object> routeBody = entityCaptor.getAllValues().get(1).getEntity();
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

        handler.handle(createRouteCommand(Map.of("openDataAccess", true)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(entityCaptor.capture());
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
        when(mockResponse.readEntity(Map.class)).thenReturn(Map.of());
        when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandResult result = handler.handle(createRouteCommand(Map.of()));

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
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(Map.class)).thenReturn(Map.of());
        when(mockBuilder.delete()).thenReturn(notFound);

        SagaCommandResult result = handler.handle(createRouteCommand(Map.of()));

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

        SagaCommandResult result =
            handler.handle(
                createRouteCommand(
                    Map.of("upstreamUrl", "http://frost/FROST-Server/v1.1/Projects(1)")));

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

        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("upstreamUrl", "http://frost:8080")));

        assertEquals("STEP_COMPLETED", result.type());
      }
    }
  }

  @Nested
  @DisplayName("CREATE_ROUTE per named API (slug-keyed route model, issue #1368)")
  class CreateRoutePerNamedApi {

    private SagaCommandMessage createPerApiCommand(
        boolean openDataAccess, List<Map<String, Object>> namedApis) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("datasetId", "ds-001");
      payload.put("upstreamUrl", "http://frost:8080/FROST-Server/v1.1/Projects(1)");
      payload.put("openDataAccess", openDataAccess);
      payload.put("namedApis", namedApis);
      return new SagaCommandMessage(
          "EXECUTE_STEP", "msg-001", "saga-001", "create-route", "apisix", "CREATE_ROUTE", payload);
    }

    @Test
    @DisplayName(
        "fails (STEP_FAILED) for a non-STA standard — WFS/WMS not yet routable (GeoServer)")
    void shouldFailForNonStaStandard() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);
        when(mockBuilder.delete()).thenReturn(ok);

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(true, List.of(Map.of("slug", "map", "standard", "WFS"))));

        // Non-STA fails fast rather than provisioning a FROST route behind a WFS public URL; the
        // error names the offending standard so the cause is attributable.
        assertEquals("STEP_FAILED", result.type());
        assertTrue(
            result.error().contains("WFS") || result.error().contains("GeoServer"),
            "error should name the unsupported standard / GeoServer gap");

        // Standards are validated BEFORE provisioning, so neither a route nor the shared upstream
        // is ever created — no gateway state, no cleanup needed.
        verify(mockBuilder, never()).put(any(Entity.class));
        verify(mockBuilder, never()).delete();
      }
    }

    @Test
    @DisplayName("creates one shared upstream and one route per slug, returns slug-keyed routeIds")
    void shouldCreateOneRoutePerSlug() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    true,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA", "version", "1.1"),
                        Map.of("slug", "weather", "standard", "STA", "version", "1.1"))));

        assertEquals("STEP_COMPLETED", result.type());
        // One shared dataset upstream + one route per slug.
        verify(mockBuilder, times(3)).put(any(Entity.class));

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        List<String> paths = pathCaptor.getAllValues();
        assertTrue(
            paths.contains("/apisix/admin/upstreams/ds-001"), "one shared upstream per dataset");
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "traffic")),
            "deterministic route id for the traffic slug");
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "weather")),
            "deterministic route id for the weather slug");

        @SuppressWarnings("unchecked")
        Map<String, String> routeIds = (Map<String, String>) result.resultData().get("routeIds");
        assertEquals(
            Map.of(
                "traffic", NamedApiHelper.derive("ds-001", "traffic"),
                "weather", NamedApiHelper.derive("ds-001", "weather")),
            routeIds);
        assertEquals("ds-001", result.resultData().get("serviceId"));
      }
    }

    @Test
    @DisplayName(
        "binds each slug route to /v1/datasets/{id}/{slug} and the shared dataset upstream")
    void shouldBindSlugRouteToDatasetSlugPath() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);

        handler.handle(
            createPerApiCommand(true, List.of(Map.of("slug", "traffic", "standard", "STA"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
            ArgumentCaptor.forClass(Entity.class);
        // PUT order: shared upstream first, then the slug route.
        verify(mockBuilder, times(2)).put(entityCaptor.capture());
        Map<String, Object> routeBody = entityCaptor.getAllValues().get(1).getEntity();

        assertArrayEquals(
            new String[] {"/v1/datasets/ds-001/traffic", "/v1/datasets/ds-001/traffic/*"},
            (String[]) routeBody.get("uris"));
        assertEquals("ds-001", routeBody.get("upstream_id"));
      }
    }

    @Test
    @DisplayName(
        "compensation data carries the slug-keyed routeIds and shared upstream for rollback")
    void shouldReturnSlugKeyedCompensationData() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(true, List.of(Map.of("slug", "traffic", "standard", "STA"))));

        @SuppressWarnings("unchecked")
        Map<String, String> compRouteIds =
            (Map<String, String>) result.compensationData().get("routeIds");
        assertEquals(Map.of("traffic", NamedApiHelper.derive("ds-001", "traffic")), compRouteIds);
        assertEquals("ds-001", result.compensationData().get("serviceId"));
      }
    }

    @Test
    @DisplayName("ignores a namedApi entry without a usable slug (only valid slugs get a route)")
    void shouldIgnoreNamedApiEntriesWithoutSlug() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(201);
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok);

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    true,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA"),
                        Map.of("standard", "STA")))); // malformed: no slug → dropped

        assertEquals("STEP_COMPLETED", result.type());
        // One shared upstream + exactly one route (the valid slug only).
        verify(mockBuilder, times(2)).put(any(Entity.class));
        @SuppressWarnings("unchecked")
        Map<String, String> routeIds = (Map<String, String>) result.resultData().get("routeIds");
        assertEquals(Map.of("traffic", NamedApiHelper.derive("ds-001", "traffic")), routeIds);
      }
    }

    @Test
    @DisplayName("cleans up already-created routes + the upstream when a later route PUT fails")
    void shouldCleanUpPartialStateOnCreateFailure() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(201);
        Response err = mock(Response.class);
        when(err.getStatus()).thenReturn(500);
        when(err.readEntity(String.class)).thenReturn("boom");
        // PUT order: shared upstream, then one route per slug. Upstream + first slug succeed,
        // the second slug route fails — leaving the upstream and the first route to clean up.
        when(mockBuilder.put(any(Entity.class))).thenReturn(ok, ok, err);
        Response delOk = mock(Response.class);
        when(delOk.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(delOk);

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    false,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA"),
                        Map.of("slug", "weather", "standard", "STA"))));

        assertEquals("STEP_FAILED", result.type());
        // Best-effort rollback: the created route (traffic) and the shared upstream are deleted.
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        List<String> paths = pathCaptor.getAllValues();
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "traffic")),
            "created route should be cleaned up");
        assertTrue(
            paths.contains("/apisix/admin/upstreams/ds-001"),
            "shared upstream should be cleaned up");
        verify(mockBuilder, atLeastOnce()).delete();
      }
    }
  }

  @Nested
  @DisplayName("UPDATE_ROUTE per named API (slug-keyed route model, issue #1368)")
  class UpdateRoutePerNamedApi {

    @Test
    @DisplayName("updates every route in the slug-keyed routeIds map")
    void shouldUpdateEachRouteInMap() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = mock(Response.class);
        when(getResp.getStatus()).thenReturn(200);
        // Mutable route value — the handler mutates it in place (GET → mutate → PUT).
        when(getResp.readEntity(Map.class))
            .thenAnswer(
                inv -> {
                  Map<String, Object> value = new HashMap<>();
                  value.put("uri", "/v1/datasets/ds-001/traffic");
                  value.put("plugin_config_id", "auth-plugin-1");
                  Map<String, Object> envelope = new HashMap<>();
                  envelope.put("value", value);
                  return envelope;
                });
        when(mockBuilder.get()).thenReturn(getResp);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", true);
        SagaCommandMessage command =
            new SagaCommandMessage(
                "EXECUTE_STEP", "m", "saga-001", "update-route", "apisix", "UPDATE_ROUTE", payload);

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(2)).get();
        verify(mockBuilder, times(2)).put(any(Entity.class));
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        List<String> paths = pathCaptor.getAllValues();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(paths.contains("/apisix/admin/routes/rid-weather"));

        @SuppressWarnings("unchecked")
        Map<String, String> routeIds = (Map<String, String>) result.resultData().get("routeIds");
        assertEquals(Map.of("traffic", "rid-traffic", "weather", "rid-weather"), routeIds);

        // Compensation must capture each slug's previous open/protected state as a slug-keyed map
        // (the exact contract per-slug RESTORE_ROUTE consumes). Both routes were private
        // (plugin_config_id present) so each slug's previous openDataAccess is false.
        @SuppressWarnings("unchecked")
        Map<String, Object> previousOpen =
            (Map<String, Object>) result.compensationData().get("previousOpenDataAccess");
        assertEquals(Map.of("traffic", false, "weather", false), previousOpen);
        assertEquals(
            Map.of("traffic", "rid-traffic", "weather", "rid-weather"),
            result.compensationData().get("routeIds"));
      }
    }

    @Test
    @DisplayName("fails (STEP_FAILED) without mutating any route when a slug route is absent")
    void shouldFailWhenSlugRouteAbsentOnForwardUpdate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(Map.class)).thenReturn(Map.of());
        Response present = mock(Response.class);
        when(present.getStatus()).thenReturn(200);
        when(present.readEntity(Map.class))
            .thenAnswer(
                inv -> {
                  Map<String, Object> value = new HashMap<>();
                  value.put("uri", "/v1/datasets/ds-001/weather");
                  value.put("plugin_config_id", "auth-plugin-1");
                  Map<String, Object> envelope = new HashMap<>();
                  envelope.put("value", value);
                  return envelope;
                });
        // Phase 1 loads both routes (deterministic order): the first slug (traffic) is gone (404),
        // the second (weather) is present.
        when(mockBuilder.get()).thenReturn(notFound, present);

        Map<String, String> routeIds = new LinkedHashMap<>();
        routeIds.put("traffic", "rid-traffic");
        routeIds.put("weather", "rid-weather");
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", routeIds);
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", true);
        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "update-route",
                    "apisix",
                    "UPDATE_ROUTE",
                    payload));

        // All-or-nothing: a forward UPDATE whose target route is missing fails BEFORE any PUT, so
        // the
        // dataset is never left in a mixed auth state. (A failed step contributes no compensation
        // data — SagaStepDelegate only records it for completed steps — so there must be nothing to
        // roll back.) Both routes are GET-validated in phase 1; neither is PUT.
        assertEquals("STEP_FAILED", result.type());
        verify(mockBuilder, times(2)).get();
        verify(mockBuilder, never()).put(any(Entity.class));
        assertTrue(result.error().contains("traffic"));
      }
    }

    @Test
    @DisplayName(
        "compensation re-run tolerates an absent slug route (no fail) and updates the rest")
    void shouldTolerateAbsentSlugRouteOnUpdateCompensation() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(Map.class)).thenReturn(Map.of());
        Response present = mock(Response.class);
        when(present.getStatus()).thenReturn(200);
        when(present.readEntity(Map.class))
            .thenAnswer(
                inv -> {
                  Map<String, Object> value = new HashMap<>();
                  value.put("uri", "/v1/datasets/ds-001/weather");
                  value.put("plugin_config_id", "auth-plugin-1");
                  Map<String, Object> envelope = new HashMap<>();
                  envelope.put("value", value);
                  return envelope;
                });
        when(mockBuilder.get()).thenReturn(notFound, present);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, String> routeIds = new LinkedHashMap<>();
        routeIds.put("traffic", "rid-traffic");
        routeIds.put("weather", "rid-weather");
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", routeIds);
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", true);
        // COMPENSATE_STEP: an absent route during rollback is expected, so the step must NOT fail —
        // it applies to the present route and records the missing slug as a per-slug no-op.
        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "update-route",
                    "apisix",
                    "UPDATE_ROUTE",
                    payload));

        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(2)).get();
        verify(mockBuilder, times(1)).put(any(Entity.class));

        @SuppressWarnings("unchecked")
        Map<String, Object> previousOpen =
            (Map<String, Object>) result.compensationData().get("previousOpenDataAccess");
        assertEquals(true, previousOpen.get("traffic")); // absent → requested state as no-op
        assertEquals(false, previousOpen.get("weather")); // present private route
      }
    }

    @Test
    @DisplayName("fails (STEP_FAILED) when a per-slug UPDATE command omits serviceId")
    void shouldFailWhenPerSlugUpdateMissingServiceId() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // routeIds present (per-named-API branch) but serviceId absent — the branch requires it and
        // must fail fast before touching any route, separately from the legacy single-route path.
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic"));
        payload.put("openDataAccess", true);

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "update-route",
                    "apisix",
                    "UPDATE_ROUTE",
                    payload));

        assertEquals("STEP_FAILED", result.type());
        // Fails before any route I/O.
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).put(any(Entity.class));
      }
    }
  }

  @Nested
  @DisplayName("DELETE_ROUTE per named API (slug-keyed route model, issue #1368)")
  class DeleteRoutePerNamedApi {

    @Test
    @DisplayName("deletes every route in the map and the shared dataset upstream")
    void shouldDeleteEachRouteAndUpstream() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(ok);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        SagaCommandMessage command =
            new SagaCommandMessage(
                "EXECUTE_STEP", "m", "saga-001", "delete-route", "apisix", "DELETE_ROUTE", payload);

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Two slug routes + one shared upstream.
        verify(mockBuilder, times(3)).delete();
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        List<String> paths = pathCaptor.getAllValues();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(paths.contains("/apisix/admin/routes/rid-weather"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"));
      }
    }

    @Test
    @DisplayName("tolerates an already-gone slug route (404) and still deletes the rest + upstream")
    void shouldTolerate404OnIndividualSlugRoute() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(String.class)).thenReturn("Key not found");
        // First delete is an already-gone route (404), the remaining route + upstream are present.
        when(mockBuilder.delete()).thenReturn(notFound, ok, ok);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "delete-route",
                    "apisix",
                    "DELETE_ROUTE",
                    payload));

        // Idempotent: a 404 on one slug route must not abort the per-slug teardown.
        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(3)).delete();
      }
    }

    @Test
    @DisplayName(
        "DELETE_ROUTE is idempotent: a 404 on every expected route-id counts as success (no"
            + " STEP_FAILED); state drift is surfaced via WARN log, not a failure")
    void shouldCompleteWhenNoTargetRoutesExist() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(String.class)).thenReturn("Key not found");
        when(mockBuilder.delete()).thenReturn(notFound);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "delete-route",
                    "apisix",
                    "DELETE_ROUTE",
                    payload));

        // Contract: DELETE_ROUTE is idempotent — the goal state is "route absent", so a 404 on an
        // expected route-id counts as success. A forward delete that matched NONE of its target
        // routes therefore still completes (STEP_FAILED is deliberately NOT raised). The wholesale
        // miss is surfaced via a WARN log for drift visibility, not turned into a step failure.
        // (Hardening this to fail on drift is a separate operational-policy decision; see the
        // DELETE-tolerance discussion in the handler.)
        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, times(3)).delete();
      }
    }

    @Test
    @DisplayName("drops a routeIds entry with a null value and still deletes the valid route(s)")
    void shouldDropNullValuedRouteIdEntry() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(ok);

        Map<String, String> routeIds = new HashMap<>();
        routeIds.put("traffic", "rid-traffic");
        routeIds.put("weather", null); // null value → dropped (logged), must not abort the delete
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", routeIds);
        payload.put("serviceId", "ds-001");

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "delete-route",
                    "apisix",
                    "DELETE_ROUTE",
                    payload));

        assertEquals("STEP_COMPLETED", result.type());
        // Only the valid 'traffic' route + the shared upstream are deleted; the null-valued
        // 'weather' entry is dropped rather than NPE-ing or aborting the step.
        verify(mockBuilder, times(2)).delete();
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        assertTrue(pathCaptor.getAllValues().contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(pathCaptor.getAllValues().contains("/apisix/admin/upstreams/ds-001"));
      }
    }
  }

  @Nested
  @DisplayName("RESTORE_ROUTE per named API (slug-keyed route model, issue #1368)")
  class RestoreRoutePerNamedApi {

    private Response privateRouteGet() {
      Response getResp = mock(Response.class);
      when(getResp.getStatus()).thenReturn(200);
      // Fresh map per GET so per-slug in-place mutations don't bleed across iterations.
      when(getResp.readEntity(Map.class))
          .thenAnswer(
              inv -> {
                Map<String, Object> value = new HashMap<>();
                value.put("uri", "/v1/datasets/ds-001/x");
                value.put("plugin_config_id", "auth-plugin-1");
                Map<String, Object> envelope = new HashMap<>();
                envelope.put("value", value);
                return envelope;
              });
      return getResp;
    }

    @Test
    @DisplayName("restores each slug route to its captured previous open/protected state")
    void shouldRestoreEachSlugToCapturedState() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = privateRouteGet();
        when(mockBuilder.get()).thenReturn(getResp);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        // traffic was previously public (→ drop plugin_config_id), weather private (→ keep it).
        payload.put("previousOpenDataAccess", Map.of("traffic", true, "weather", false));

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "restore-route",
                    "apisix",
                    "RESTORE_ROUTE",
                    payload));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockBuilder, times(2)).get();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(captor.capture());
        List<Map<String, Object>> bodies =
            captor.getAllValues().stream().map(Entity::getEntity).toList();
        // Order-agnostic (map iteration): exactly one route restored public, one private.
        long publicBodies = bodies.stream().filter(b -> !b.containsKey("plugin_config_id")).count();
        long privateBodies = bodies.stream().filter(b -> b.containsKey("plugin_config_id")).count();
        assertEquals(1, publicBodies);
        assertEquals(1, privateBodies);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        assertTrue(pathCaptor.getAllValues().contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(pathCaptor.getAllValues().contains("/apisix/admin/routes/rid-weather"));
      }
    }

    @Test
    @DisplayName("skips a slug route that is already gone (404) and restores the rest")
    void shouldSkipGoneSlugAndRestoreOthers() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(Map.class)).thenReturn(Map.of());
        Response present = privateRouteGet();
        // One slug route is already gone (404 on GET), the other is present and restorable.
        when(mockBuilder.get()).thenReturn(notFound, present);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        payload.put("previousOpenDataAccess", Map.of("traffic", false, "weather", false));

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "restore-route",
                    "apisix",
                    "RESTORE_ROUTE",
                    payload));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockBuilder, times(2)).get();
        // The gone route is skipped — only the present route is PUT back.
        verify(mockBuilder, times(1)).put(any(Entity.class));
      }
    }

    @Test
    @DisplayName("restores a slug absent from previousOpenDataAccess as protected (default false)")
    void shouldRestoreSlugMissingFromPreviousMapAsProtected() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = privateRouteGet();
        when(mockBuilder.get()).thenReturn(getResp);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic"));
        payload.put("serviceId", "ds-001");
        // Empty map: the slug is missing → getOrDefault(false) → restored protected, not crashing.
        payload.put("previousOpenDataAccess", Map.of());

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "restore-route",
                    "apisix",
                    "RESTORE_ROUTE",
                    payload));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder).put(captor.capture());
        // Default false → protected → plugin_config_id retained.
        assertTrue(captor.getValue().getEntity().containsKey("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("coerces string previousOpenDataAccess values (\"true\"/\"false\") to booleans")
    void shouldCoerceStringPreviousOpenValues() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = privateRouteGet();
        when(mockBuilder.get()).thenReturn(getResp);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        // Values arrive as JSON strings (e.g. after a serialize/deserialize round-trip), not
        // booleans.
        payload.put("previousOpenDataAccess", Map.of("traffic", "true", "weather", "false"));

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "restore-route",
                    "apisix",
                    "RESTORE_ROUTE",
                    payload));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder, times(2)).put(captor.capture());
        List<Map<String, Object>> bodies =
            captor.getAllValues().stream().map(Entity::getEntity).toList();
        // "true" → public (plugin_config_id dropped); "false" → protected (retained).
        long publicBodies = bodies.stream().filter(b -> !b.containsKey("plugin_config_id")).count();
        long privateBodies = bodies.stream().filter(b -> b.containsKey("plugin_config_id")).count();
        assertEquals(1, publicBodies);
        assertEquals(1, privateBodies);
      }
    }

    @Test
    @DisplayName("drops a null-keyed previousOpenDataAccess entry and still restores valid slugs")
    void shouldDropNullKeyedPreviousOpenEntry() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = privateRouteGet();
        when(mockBuilder.get()).thenReturn(getResp);
        Response putResp = mock(Response.class);
        when(putResp.getStatus()).thenReturn(200);
        when(mockBuilder.put(any(Entity.class))).thenReturn(putResp);

        Map<String, Object> previous = new HashMap<>();
        previous.put("traffic", true);
        previous.put(null, true); // null key → dropped (logged), must not abort the restore
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic"));
        payload.put("serviceId", "ds-001");
        payload.put("previousOpenDataAccess", previous);

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "COMPENSATE_STEP",
                    "m",
                    "saga-001",
                    "restore-route",
                    "apisix",
                    "RESTORE_ROUTE",
                    payload));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Entity<Map<String, Object>>> captor = ArgumentCaptor.forClass(Entity.class);
        verify(mockBuilder).put(captor.capture());
        // The valid "traffic"=true entry still applies → restored public.
        assertFalse(captor.getValue().getEntity().containsKey("plugin_config_id"));
      }
    }
  }

  @Nested
  @DisplayName("Open-data method gate + routeIds drift handling (MR !547 findings 1, 2, 5)")
  class MethodGateAndDriftHandling {

    private static final List<String> OPEN_DATA_METHODS = List.of("GET", "HEAD", "OPTIONS");

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
              openDataAccess,
              "namedApis",
              List.of(Map.of("slug", "data", "standard", "STA"))));
    }

    private Map<String, Object> captureRouteBody(int expectedPuts) {
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Entity<Map<String, Object>>> entityCaptor =
          ArgumentCaptor.forClass(Entity.class);
      verify(mockBuilder, times(expectedPuts)).put(entityCaptor.capture());
      return entityCaptor.getValue().getEntity();
    }

    @Test
    @DisplayName("open-data routes restrict methods to GET/HEAD/OPTIONS (no OPA on the route)")
    void shouldRestrictMethodsOnOpenDataRoute() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        handler.handle(createRouteCommand(true));

        // Without plugin_config_id (no OIDC/OPA), the method gate is the only thing keeping
        // anonymous writes away from FROST — "open" means anonymous READ (finding 1).
        assertEquals(OPEN_DATA_METHODS, captureRouteBody(2).get("methods"));
      }
    }

    @Test
    @DisplayName("protected routes carry no methods filter (OPA gates per request)")
    void shouldNotRestrictMethodsOnProtectedRoute() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        handler.handle(createRouteCommand(false));

        assertFalse(captureRouteBody(2).containsKey("methods"));
      }
    }

    @Test
    @DisplayName("UPDATE toggle protected→open adds the method gate to the existing route")
    void shouldAddMethodGateWhenTogglingToOpen() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = mock(Response.class);
        when(getResp.getStatus()).thenReturn(200);
        when(getResp.readEntity(Map.class))
            .thenAnswer(
                inv -> {
                  Map<String, Object> value = new HashMap<>();
                  value.put("uri", "/v1/datasets/ds-001/data");
                  value.put("plugin_config_id", "auth-plugin-1");
                  Map<String, Object> envelope = new HashMap<>();
                  envelope.put("value", value);
                  return envelope;
                });
        when(mockBuilder.get()).thenReturn(getResp);
        stubPutCreated();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of(
                    "routeIds",
                    Map.of("data", "rid-data"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

        assertEquals(OPEN_DATA_METHODS, captureRouteBody(1).get("methods"));
      }
    }

    @Test
    @DisplayName("UPDATE toggle open→protected removes the method gate (OPA takes over)")
    void shouldRemoveMethodGateWhenTogglingToProtected() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response getResp = mock(Response.class);
        when(getResp.getStatus()).thenReturn(200);
        when(getResp.readEntity(Map.class))
            .thenAnswer(
                inv -> {
                  Map<String, Object> value = new HashMap<>();
                  value.put("uri", "/v1/datasets/ds-001/data");
                  value.put("methods", new ArrayList<>(OPEN_DATA_METHODS));
                  Map<String, Object> envelope = new HashMap<>();
                  envelope.put("value", value);
                  return envelope;
                });
        when(mockBuilder.get()).thenReturn(getResp);
        stubPutCreated();

        handler.handle(
            createCommand(
                "EXECUTE_STEP",
                "UPDATE_ROUTE",
                Map.of(
                    "routeIds",
                    Map.of("data", "rid-data"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    false)));

        Map<String, Object> body = captureRouteBody(1);
        assertFalse(
            body.containsKey("methods"),
            "protected routes must not keep a stale read-only gate — OPA decides per request");
        assertEquals("auth-plugin-1", body.get("plugin_config_id"));
      }
    }

    @Test
    @DisplayName(
        "UPDATE fails loud when a named API has no persisted routeId (no silent no-op, finding 2)")
    void shouldFailUpdateWhenNamedApiHasNoRouteId() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of());
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", false);
        payload.put("namedApis", List.of(Map.of("slug", "data", "standard", "STA")));

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "update-route",
                    "apisix",
                    "UPDATE_ROUTE",
                    payload));

        // A protect-toggle must never report success without acting: an empty/stale routeIds map
        // with live named APIs is drift, not a no-op — the dataset could silently stay public.
        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("data"), "error should name the drifted slug");
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).put(any(Entity.class));
      }
    }

    @Test
    @DisplayName("UPDATE without namedApis and without routeIds stays a clean no-op")
    void shouldNoOpUpdateWithoutNamedApisAndRouteIds() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of());
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", true);

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "update-route",
                    "apisix",
                    "UPDATE_ROUTE",
                    payload));

        // A dataset without named APIs has no data-plane routes — nothing to update, by design.
        assertEquals("STEP_COMPLETED", result.type());
        verify(mockBuilder, never()).get();
        verify(mockBuilder, never()).put(any(Entity.class));
      }
    }

    @Test
    @DisplayName(
        "DELETE derives the deterministic routeId for slugs missing from the map (finding 5)")
    void shouldDeleteDerivedRouteIdForSlugWithoutPersistedId() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response ok = mock(Response.class);
        when(ok.getStatus()).thenReturn(200);
        when(mockBuilder.delete()).thenReturn(ok);

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic"));
        payload.put("serviceId", "ds-001");
        // 'weather' lost its persisted routeId (drift) but may still have a live gateway route
        // under the deterministic id — teardown must not leave it behind.
        payload.put(
            "namedApis",
            List.of(
                Map.of("slug", "traffic", "standard", "STA"),
                Map.of("slug", "weather", "standard", "STA")));

        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "m",
                    "saga-001",
                    "delete-route",
                    "apisix",
                    "DELETE_ROUTE",
                    payload));

        assertEquals("STEP_COMPLETED", result.type());
        // Persisted route + derived route + shared upstream.
        verify(mockBuilder, times(3)).delete();
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockTarget, atLeastOnce()).path(pathCaptor.capture());
        List<String> paths = pathCaptor.getAllValues();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "weather")),
            "the drifted slug's route must be deleted under its derived deterministic id");
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"));
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
              true,
              "namedApis",
              List.of(Map.of("slug", "data", "standard", "STA"))));
    }

    private void stubPutCreated() {
      Response mockResponse = mock(Response.class);
      when(mockResponse.getStatus()).thenReturn(201);
      when(mockBuilder.put(any(Entity.class))).thenReturn(mockResponse);
    }

    @Test
    @DisplayName(
        "pins api host, uses /v1/datasets/{id}/{slug} layout, rewrites to FROST upstream and returns"
            + " publicUrl")
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
                    "[/v1/datasets/ds-001/data, /v1/datasets/ds-001/data/*]",
                    Arrays.toString((String[]) routeBody.get("uris"))),
            () ->
                assertEquals(
                    "[^/v1/datasets/ds-001/data(/.*)?$, /FROST-Server/v1.1/Projects(1)$1]",
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
              openDataAccess,
              "namedApis",
              List.of(Map.of("slug", "data", "standard", "STA"))));
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
    @DisplayName(
        "does not inject Basic Auth header when openDataAccess=true (but keeps the default strip)")
    void shouldNotInjectAuthHeaderForPublicProject() {
      try (ApisixSagaHandler handler = createHandler()) {
        stubPutCreated();

        handler.handle(createRouteCommand(true));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        assertNotNull(
            headers,
            "even without configured headers.remove, the hard-coded trust-header strip applies"
                + " (MR !547 finding 3 — secure-by-default)");
        assertFalse(
            headers.containsKey("set"), "public routes must NOT carry FROST upstream credentials");
        assertEquals(List.of("X-Allowed-Scope-Ids"), headers.get("remove"));
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
    @DisplayName("fails clearly when the route GET returns an unexpected body shape")
    void shouldFailClearlyOnMalformedRouteBody() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("value", "not-an-object"); // value should be a route object, not a string
        Response getResponse = mock(Response.class);
        when(getResponse.getStatus()).thenReturn(200);
        when(getResponse.readEntity(Map.class)).thenReturn(wrapper);
        when(mockBuilder.get()).thenReturn(getResponse);

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "EXECUTE_STEP",
                    "UPDATE_ROUTE",
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        false)));

        // No opaque ClassCastException — a clear, classified failure instead.
        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertTrue(result.error().toLowerCase().contains("unexpected body shape"));
      }
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
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        true)));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(
            false,
            ((Map<?, ?>) result.compensationData().get("previousOpenDataAccess")).get("data"));

        Map<String, Object> body = capturePutBody();
        assertFalse(body.containsKey("plugin_config_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plugins = (Map<String, Object>) body.get("plugins");
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite = (Map<String, Object>) plugins.get("proxy-rewrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        assertFalse(
            headers.containsKey("set"), "the FROST credential must be removed on the public flip");
        assertEquals(
            List.of("X-Allowed-Scope-Ids"),
            headers.get("remove"),
            "the hard-coded trust-header strip stays in place on public routes");
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
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        false)));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(
            true,
            ((Map<?, ?>) result.compensationData().get("previousOpenDataAccess")).get("data"));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        true)));

        assertEquals(
            false,
            ((Map<?, ?>) result.compensationData().get("previousOpenDataAccess")).get("data"),
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
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        true)));

        assertEquals(
            true,
            ((Map<?, ?>) result.compensationData().get("previousOpenDataAccess")).get("data"),
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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    false)));

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
                    Map.of(
                        "routeIds",
                        Map.of("data", "ds-001"),
                        "serviceId",
                        "ds-001",
                        "openDataAccess",
                        true)));

        assertEquals(
            false,
            ((Map<?, ?>) result.compensationData().get("previousOpenDataAccess")).get("data"),
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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    false)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                Map.of(
                    "routeIds",
                    Map.of("data", "ds-001"),
                    "serviceId",
                    "ds-001",
                    "openDataAccess",
                    true)));

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
                "EXECUTE_STEP",
                "DELETE_ROUTE",
                Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001"));

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
                Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_COMPLETED", result.type());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on compensate error")
    void shouldReturnCompensationFailureOnError() {
      try (ApisixSagaHandler handler = createHandler()) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(mockResponse.readEntity(String.class)).thenReturn("Internal Server Error");
        when(mockBuilder.delete()).thenReturn(mockResponse);

        SagaCommandMessage command =
            createCommand(
                "COMPENSATE_STEP",
                "DELETE_ROUTE",
                Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001"));

        SagaCommandResult result = handler.handle(command);

        assertEquals("COMPENSATION_FAILED", result.type());
      }
    }

    @Test
    @DisplayName(
        "treats an already-deleted route/upstream (404) as success — idempotent compensation")
    void shouldTreatMissingResourceAsAlreadyDeleted() {
      try (ApisixSagaHandler handler = createHandler()) {
        // A re-run delete (or a route the create step never finished provisioning) returns 404.
        // Deleting an already-absent resource has reached the desired end state, so the saga
        // compensation must succeed rather than fail and stall the rollback.
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(String.class)).thenReturn("Key not found");
        when(mockBuilder.delete()).thenReturn(notFound);

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "DELETE_ROUTE",
                    Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001")));

        assertEquals("COMPENSATION_COMPLETED", result.type());
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
                        "routeIds", Map.of("data", "ds-001"),
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", Map.of("data", false))));

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
                        "routeIds", Map.of("data", "ds-001"),
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", Map.of("data", true))));

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
                        "routeIds", Map.of("data", "ds-001"),
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", Map.of("data", true))));

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
                        "routeIds", Map.of("data", "ds-001"),
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", Map.of("data", false))));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("skips restore when the route is already gone (404) — idempotent compensation")
    void shouldSkipRestoreWhenRouteAlreadyGone() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Response notFound = mock(Response.class);
        when(notFound.getStatus()).thenReturn(404);
        when(notFound.readEntity(Map.class)).thenReturn(Map.of());
        when(mockBuilder.get()).thenReturn(notFound);

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of(
                        "routeIds", Map.of("data", "ds-001"),
                        "serviceId", "ds-001",
                        "previousOpenDataAccess", Map.of("data", false))));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        verify(mockBuilder, never()).put(any(Entity.class));
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
  @DisplayName("Proxy-rewrite regex pattern (documents the expected regex SHAPE)")
  class ProxyRewriteRegex {

    /**
     * Documents/pins the SHAPE of the path-rewrite regex the handler is expected to emit — it
     * re-implements the pattern locally and is NOT wired to {@code buildRouteBody}'s actual output.
     * Treat it as executable documentation of the rewrite contract; the genuine end-to-end coverage
     * that the produced route really rewrites correctly lives in {@code
     * ApisixSagaHandlerRoutingTest} (real APISIX via Testcontainers). If the production regex
     * changes, update both.
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
    mockTarget = mock(WebTarget.class);
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

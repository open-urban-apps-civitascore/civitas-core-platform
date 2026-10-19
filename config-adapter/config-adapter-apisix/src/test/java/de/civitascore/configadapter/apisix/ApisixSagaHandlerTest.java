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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import de.civitascore.configadapter.util.PayloadConverter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ApisixSagaHandlerTest {

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
          "apisix.service.id",
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
        if (!"apisix.service.id".equals(missingProperty)) {
          when(config.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
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
        when(config.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
        when(config.getProperty("apisix.frost.api.key")).thenReturn("test-api-key");
        when(config.getProperty("apisix.frost.api.key.header", "X-API-Key"))
            .thenReturn("X-API-Key");
        when(config.getProperty("apisix.geoserver.url", "http://localhost:8080/geoserver"))
            .thenReturn("http://civitas-geoserver:8080/geoserver");

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
        when(config.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
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
        when(config.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
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
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

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
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("openDataAccess", false)));

        assertEquals("STEP_COMPLETED", result.type());
        // Verify the security-relevant effect, not just the step status: the route body must carry
        // plugin_config_id so the gateway enforces auth on the protected dataset.
        assertEquals(2, server.getRequestCount());
        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
      }
    }

    @Test
    @DisplayName(
        "always protects the route (plugin_config_id, no methods gate) even when openDataAccess=true")
    void shouldAlwaysProtectRouteEvenWhenOpenDataAccessTrue() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        // openDataAccess=true must NOT bypass auth anymore: open-data access is decided by OPA per
        // request (ABAC), so the route is provisioned protected exactly like any other — it keeps
        // plugin_config_id (OIDC + OPA) and carries no read-only methods gate.
        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("openDataAccess", true)));

        assertEquals("STEP_COMPLETED", result.type());
        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
        assertNull(routeBody.get("methods"), "no read-only method gate — OPA gates per request");
      }
    }

    @Test
    @DisplayName("includes service_id in route body when configured")
    void shouldIncludeServiceIdInRouteBody() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        handler.handle(createRouteCommand(Map.of("openDataAccess", true)));

        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());
        assertEquals("svc-frost-server", routeBody.get("service_id"));
      }
    }

    @Test
    @DisplayName("returns failure on HTTP error")
    void shouldReturnFailureOnHttpError() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(400).body("Bad Request").build());

        SagaCommandResult result = handler.handle(createRouteCommand(Map.of()));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns failure on network error")
    void shouldReturnFailureOnNetworkError() throws IOException {
      try (ApisixSagaHandler handler = createHandler()) {
        // Torn down before any request is made: the connection attempt itself fails.
        server.close();

        SagaCommandResult result = handler.handle(createRouteCommand(Map.of()));

        assertEquals("STEP_FAILED", result.type());
      }
    }

    @Test
    @DisplayName("returns failure for invalid upstream URL")
    void shouldReturnFailureForInvalidUpstreamUrl() {
      try (ApisixSagaHandler handler = createHandler()) {
        // Goes through the helper so the command carries an STA slug: the upstream URL is only
        // required — and therefore only parsed — when a slug actually routes to FROST.
        SagaCommandResult result =
            handler.handle(createRouteCommand(Map.of("upstreamUrl", "://not a valid uri")));

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        // Upstream targets resolve before any write, so a bad URL touches no gateway state.
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails when an STA route has no upstream URL")
    void shouldFailWhenStaRouteHasNoUpstreamUrl() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandMessage command =
            createCommand(
                "EXECUTE_STEP",
                "CREATE_ROUTE",
                Map.of(
                    "datasetId",
                    "ds-001",
                    "namedApis",
                    List.of(Map.of("slug", "data", "standard", "STA"))));

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_FAILED", result.type());
        assertNotNull(result.error());
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("no-ops without named APIs even when the upstream URL is absent")
    void shouldNoOpWithoutNamedApisWhenUpstreamUrlIsAbsent() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createCommand("EXECUTE_STEP", "CREATE_ROUTE", Map.of("datasetId", "ds-001")));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(Map.of(), result.resultData().get("routeIds"));
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("handles upstream URL without port")
    void shouldHandleUpstreamUrlWithoutPort() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

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
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

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
    @DisplayName("fails (STEP_FAILED) for a non-routable standard (CUSTOM/unknown)")
    void shouldFailForNonRoutableStandard() {
      try (ApisixSagaHandler handler = createHandler()) {
        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(true, List.of(Map.of("slug", "custom", "standard", "CUSTOM"))));

        // Only STA (FROST) and OWS (GeoServer) are routable; a non-routable standard fails fast and
        // the error names the offending standard so the cause is attributable.
        assertEquals("STEP_FAILED", result.type());
        assertTrue(result.error().contains("CUSTOM"), "error should name the unsupported standard");

        // Standards are validated BEFORE provisioning, so neither a route nor an upstream is ever
        // created — no gateway state, no cleanup needed.
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("creates one shared upstream and one route per slug, returns slug-keyed routeIds")
    void shouldCreateOneRoutePerSlug() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // shared upstream
        server.enqueue(created()); // traffic route
        server.enqueue(created()); // weather route

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    true,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA", "version", "1.1"),
                        Map.of("slug", "weather", "standard", "STA", "version", "1.1"))));

        assertEquals("STEP_COMPLETED", result.type());
        // One shared dataset upstream + one route per slug.
        assertEquals(3, server.getRequestCount());

        List<String> paths = recordedPaths();
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
        server.enqueue(created()); // shared upstream
        server.enqueue(created()); // traffic route

        handler.handle(
            createPerApiCommand(true, List.of(Map.of("slug", "traffic", "standard", "STA"))));

        // PUT order: shared upstream first, then the slug route.
        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());

        assertEquals(
            List.of("/v1/datasets/ds-001/traffic", "/v1/datasets/ds-001/traffic/*"),
            routeBody.get("uris"));
        assertEquals("ds-001", routeBody.get("upstream_id"));
      }
    }

    @Test
    @DisplayName("rewrites FROST's @iot.* links onto the slug route's external endpoint")
    void shouldRewriteStaLinksToExternalEndpoint() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // shared upstream
        server.enqueue(created()); // traffic route

        handler.handle(
            createPerApiCommand(true, List.of(Map.of("slug", "traffic", "standard", "STA"))));

        // PUT order: shared upstream first, then the slug route.
        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());

        // Without the rewrite, a client following one of FROST's @iot.* links leaves the
        // gateway entirely (issue #336).
        @SuppressWarnings("unchecked")
        Map<String, Object> responseRewrite =
            (Map<String, Object>) pluginsOf(routeBody).get("response-rewrite");
        assertNotNull(responseRewrite, "STA route rewrites FROST's self-referential links");
        @SuppressWarnings("unchecked")
        List<Object> filters = (List<Object>) responseRewrite.get("filters");
        assertEquals(2, filters.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> projectScoped = (Map<String, Object>) filters.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> canonical = (Map<String, Object>) filters.get(1);
        // The nextLink form repeats the project segment the regex_uri adds back, so it is
        // dropped here — before the canonical filter, which would otherwise double it.
        assertEquals("https?://[^/]+/v1\\.1/Projects\\(1\\)", projectScoped.get("regex"));
        assertEquals(
            "https://api.example.test/v1/datasets/ds-001/traffic", projectScoped.get("replace"));
        assertEquals("https?://[^/]+/v1\\.1/", canonical.get("regex"));
        assertEquals(
            "https://api.example.test/v1/datasets/ds-001/traffic/", canonical.get("replace"));

        @SuppressWarnings("unchecked")
        Map<String, Object> headers =
            (Map<String, Object>) proxyRewriteOf(routeBody).get("headers");
        @SuppressWarnings("unchecked")
        List<String> remove = (List<String>) headers.get("remove");
        assertTrue(
            remove.contains("Accept-Encoding"),
            "STA route strips Accept-Encoding so the response-rewrite filters see a plain body");
      }
    }

    @Test
    @DisplayName(
        "compensation data carries the slug-keyed routeIds and shared upstream for rollback")
    void shouldReturnSlugKeyedCompensationData() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // shared upstream
        server.enqueue(created()); // traffic route

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
        server.enqueue(created()); // shared upstream
        server.enqueue(created()); // traffic route

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    true,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA"),
                        Map.of("standard", "STA")))); // malformed: no slug → dropped

        assertEquals("STEP_COMPLETED", result.type());
        // One shared upstream + exactly one route (the valid slug only).
        assertEquals(2, server.getRequestCount());
        @SuppressWarnings("unchecked")
        Map<String, String> routeIds = (Map<String, String>) result.resultData().get("routeIds");
        assertEquals(Map.of("traffic", NamedApiHelper.derive("ds-001", "traffic")), routeIds);
      }
    }

    @Test
    @DisplayName("cleans up already-created routes + the upstream when a later route PUT fails")
    void shouldCleanUpPartialStateOnCreateFailure() {
      try (ApisixSagaHandler handler = createHandler()) {
        // PUT order: shared upstream, then one route per slug. Upstream + first slug succeed,
        // the second slug route fails — leaving the upstream and the first route to clean up.
        server.enqueue(created()); // upstream
        server.enqueue(created()); // traffic route
        server.enqueue(new MockResponse.Builder().code(500).body("boom").build()); // weather route
        server.enqueue(new MockResponse.Builder().code(200).build()); // cleanup: delete traffic
        server.enqueue(new MockResponse.Builder().code(200).build()); // cleanup: delete upstream

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    false,
                    List.of(
                        Map.of("slug", "traffic", "standard", "STA"),
                        Map.of("slug", "weather", "standard", "STA"))));

        assertEquals("STEP_FAILED", result.type());
        // Best-effort rollback: the created route (traffic) and the shared upstream are deleted.
        // 3 PUTs (upstream, traffic route, failed weather route) + 2 cleanup DELETEs.
        assertEquals(5, server.getRequestCount());
        List<RecordedRequest> requests = new ArrayList<>();
        for (int i = 0; i < server.getRequestCount(); i++) {
          requests.add(takeRequest());
        }
        List<RecordedRequest> deletes =
            requests.stream().filter(r -> "DELETE".equals(r.getMethod())).toList();
        assertEquals(2, deletes.size(), "exactly the traffic route and the shared upstream");
        List<String> deletedPaths = deletes.stream().map(r -> r.getUrl().encodedPath()).toList();
        assertTrue(
            deletedPaths.contains(
                "/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "traffic")),
            "created route should be cleaned up");
        assertTrue(
            deletedPaths.contains("/apisix/admin/upstreams/ds-001"),
            "shared upstream should be cleaned up");
      }
    }

    @Test
    @DisplayName(
        "OWS named API routes to the per-dataset map-server upstream and the workspace /ows path")
    void shouldRouteOwsToMapUpstream() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(created()); // map upstream
        server.enqueue(created()); // route

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(false, List.of(Map.of("slug", "map", "standard", "OWS"))));

        assertEquals("STEP_COMPLETED", result.type());
        // OWS-only dataset: one map-server upstream + one route, and NO FROST upstream.
        List<String> paths = recordedPaths();
        assertTrue(
            paths.contains("/apisix/admin/upstreams/ds-001-ows"),
            "per-dataset map-server upstream");
        assertFalse(
            paths.contains("/apisix/admin/upstreams/ds-001"),
            "no FROST upstream for an OWS-only dataset");
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "map")));
      }
    }

    @Test
    @DisplayName(
        "OWS named API route body binds the map upstream, rewrites to /ows and strips headers")
    void shouldRouteOwsToMapUpstreamBody() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(created()); // map upstream
        server.enqueue(created()); // route

        handler.handle(
            createPerApiCommand(false, List.of(Map.of("slug", "map", "standard", "OWS"))));

        Map<String, Object> upstreamBody = requestBodyAsMap(takeRequest());
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());

        // Upstream points at the configured GeoServer host:port.
        assertEquals(Map.of("civitas-geoserver:8080", 1), upstreamBody.get("nodes"));

        // Route binds to the map upstream and rewrites to /geoserver/{workspace}/ows. The dataset
        // id "ds-001" normalizes to the workspace "ds_001".
        assertEquals("ds-001-ows", routeBody.get("upstream_id"));
        assertEquals(
            List.of("^/v1/datasets/ds-001/map/?$", "/geoserver/ds_001/ows"),
            proxyRewriteOf(routeBody).get("regex_uri"));
        // An OWS endpoint takes its parameters in the query string, so the route matches the bare
        // address and the trailing-slash form only — a sub-path would reach the unthrottled
        // service.
        assertEquals(
            List.of("/v1/datasets/ds-001/map", "/v1/datasets/ds-001/map/"), routeBody.get("uris"));
        // Protected → gateway gate present.
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
        // The route self-describes its standard but carries NO FROST upstream credential.
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) routeBody.get("labels");
        assertEquals("OWS", labels.get("civitas-named-api-standard"));
        assertFalse(
            labels.containsKey("civitas-frost-upstream-auth-header"),
            "OWS route must not carry the FROST credential tracking label");
        assertNull(authSetHeadersOf(routeBody), "OWS route must not inject a FROST credential");
        // Internal trust header still stripped on the map-service route.
        @SuppressWarnings("unchecked")
        Map<String, Object> headers =
            (Map<String, Object>) proxyRewriteOf(routeBody).get("headers");
        assertNotNull(headers, "map route keeps a proxy-rewrite headers block for the strip list");
        @SuppressWarnings("unchecked")
        List<String> remove = (List<String>) headers.get("remove");
        assertTrue(remove.contains("X-Allowed-Scope-Ids"));
        // Accept-Encoding is stripped so GeoServer returns an uncompressed capabilities body the
        // response-rewrite filter below can match.
        assertTrue(
            remove.contains("Accept-Encoding"),
            "OWS route strips Accept-Encoding so the response-rewrite filter sees a plain body");

        // GeoServer advertises the internal /geoserver/{ws}/{service} path in its capabilities; a
        // response-rewrite filter maps it back to this route's external endpoint so map clients can
        // follow the advertised GetMap/GetFeature URLs through the gateway.
        @SuppressWarnings("unchecked")
        Map<String, Object> responseRewrite =
            (Map<String, Object>) pluginsOf(routeBody).get("response-rewrite");
        assertNotNull(responseRewrite, "OWS route rewrites GeoServer's self-referential URLs");
        @SuppressWarnings("unchecked")
        List<Object> filters = (List<Object>) responseRewrite.get("filters");
        assertEquals(1, filters.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) filters.get(0);
        assertEquals("global", filter.get("scope"));
        assertEquals(
            "https?://[^/]+/geoserver/ds_001/(wfs|wms|wcs|wps|wmts|ows|gwc)", filter.get("regex"));
        assertEquals("https://api.example.test/v1/datasets/ds-001/map", filter.get("replace"));
      }
    }

    @Test
    @DisplayName("provisions an OWS-only dataset that carries no FROST upstream URL at all")
    void shouldCreateOwsOnlyRouteWithoutUpstreamUrl() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // map upstream
        server.enqueue(created()); // route

        // A dataset with no FROST data sink gets no FROST project, so the saga carries no
        // upstreamUrl. Its OWS surface must still be published.
        Map<String, Object> payload = new HashMap<>();
        payload.put("datasetId", "ds-001");
        payload.put("openDataAccess", false);
        payload.put("namedApis", List.of(Map.of("slug", "map", "standard", "OWS")));
        SagaCommandResult result =
            handler.handle(
                new SagaCommandMessage(
                    "EXECUTE_STEP",
                    "msg-001",
                    "saga-001",
                    "create-route",
                    "apisix",
                    "CREATE_ROUTE",
                    payload));

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(
            Map.of("map", NamedApiHelper.derive("ds-001", "map")),
            result.resultData().get("routeIds"));

        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001-ows"), "map-server upstream");
        assertFalse(paths.contains("/apisix/admin/upstreams/ds-001"), "no FROST upstream");
      }
    }

    @Test
    @DisplayName(
        "a dataset with both STA and OWS named APIs provisions a FROST and a map-server upstream")
    void shouldProvisionBothUpstreamsForMixedStandards() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(created()); // FROST upstream
        server.enqueue(created()); // map upstream
        server.enqueue(created()); // data route
        server.enqueue(created()); // map route

        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(
                    false,
                    List.of(
                        Map.of("slug", "data", "standard", "STA"),
                        Map.of("slug", "map", "standard", "OWS"))));

        assertEquals("STEP_COMPLETED", result.type());
        // FROST upstream + map upstream + one route per slug.
        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"), "FROST upstream for STA slug");
        assertTrue(
            paths.contains("/apisix/admin/upstreams/ds-001-ows"), "map upstream for OWS slug");
      }
    }

    @Test
    @DisplayName(
        "a dataset with both STA and OWS named APIs binds each route to the right upstream/uris")
    void shouldProvisionBothUpstreamsForMixedStandardsBodies() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(created()); // FROST upstream
        server.enqueue(created()); // map upstream
        server.enqueue(created()); // data route
        server.enqueue(created()); // map route

        handler.handle(
            createPerApiCommand(
                false,
                List.of(
                    Map.of("slug", "data", "standard", "STA"),
                    Map.of("slug", "map", "standard", "OWS"))));

        // PUT order: FROST upstream, map upstream, then routes in slug order (data, map).
        takeRequest(); // FROST upstream
        takeRequest(); // map upstream
        Map<String, Object> staRoute = requestBodyAsMap(takeRequest());
        Map<String, Object> owsRoute = requestBodyAsMap(takeRequest());

        // The two standards match differently: STA addresses entities by sub-path, while an OWS
        // endpoint takes its parameters in the query string and matches its address exactly.
        assertEquals(
            List.of("/v1/datasets/ds-001/data", "/v1/datasets/ds-001/data/*"),
            staRoute.get("uris"));
        assertEquals(
            List.of("/v1/datasets/ds-001/map", "/v1/datasets/ds-001/map/"), owsRoute.get("uris"));
        assertEquals("ds-001", staRoute.get("upstream_id"));
        assertEquals("ds-001-ows", owsRoute.get("upstream_id"));
        // The STA route injects the FROST credential; the OWS route never does.
        assertNotNull(
            authSetHeadersOf(staRoute), "STA protected route injects the FROST credential");
        assertNull(authSetHeadersOf(owsRoute), "OWS route never carries a FROST credential");
      }
    }

    @Test
    @DisplayName("fails (STEP_FAILED) when apisix.geoserver.url is blank and an OWS route is asked")
    void shouldFailWhenGeoserverUrlBlankForOws() {
      try (ApisixSagaHandler handler = createHandlerWithGeoserverUrl("")) {
        SagaCommandResult result =
            handler.handle(
                createPerApiCommand(true, List.of(Map.of("slug", "map", "standard", "OWS"))));

        assertEquals("STEP_FAILED", result.type());
        assertTrue(
            result.error().contains("apisix.geoserver.url"),
            "error should name the missing map-server configuration");
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
        server.enqueue(routeGet("/v1/datasets/ds-001/traffic", "auth-plugin-1"));
        server.enqueue(routeGet("/v1/datasets/ds-001/weather", "auth-plugin-1"));
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", true);
        SagaCommandMessage command =
            new SagaCommandMessage(
                "EXECUTE_STEP", "m", "saga-001", "update-route", "apisix", "UPDATE_ROUTE", payload);

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        assertEquals(4, server.getRequestCount());
        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(paths.contains("/apisix/admin/routes/rid-weather"));

        @SuppressWarnings("unchecked")
        Map<String, String> routeIds = (Map<String, String>) result.resultData().get("routeIds");
        assertEquals(Map.of("traffic", "rid-traffic", "weather", "rid-weather"), routeIds);

        // Routes are always protected (no open/protected toggle), so compensation carries only the
        // routeIds + serviceId RESTORE_ROUTE needs to re-apply the protected state — no
        // previousOpenDataAccess capture anymore.
        assertEquals(
            Map.of("traffic", "rid-traffic", "weather", "rid-weather"),
            result.compensationData().get("routeIds"));
        assertNull(result.compensationData().get("previousOpenDataAccess"));
      }
    }

    @Test
    @DisplayName("fails (STEP_FAILED) without mutating any route when a slug route is absent")
    void shouldFailWhenSlugRouteAbsentOnForwardUpdate() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // Phase 1 loads both routes (deterministic order): the first slug (traffic) is gone (404),
        // the second (weather) is present.
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        server.enqueue(routeGet("/v1/datasets/ds-001/weather", "auth-plugin-1"));

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
        assertEquals(2, server.getRequestCount());
        assertTrue(result.error().contains("traffic"));
      }
    }

    @Test
    @DisplayName(
        "compensation re-run tolerates an absent slug route (no fail) and updates the rest")
    void shouldTolerateAbsentSlugRouteOnUpdateCompensation() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        server.enqueue(routeGet("/v1/datasets/ds-001/weather", "auth-plugin-1"));
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        assertEquals(3, server.getRequestCount());
        // Routes are always protected — no previousOpenDataAccess capture.
        assertNull(result.compensationData().get("previousOpenDataAccess"));
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
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("toggling an OWS route applies the gateway gate but injects no FROST credential")
    void shouldNotInjectFrostCredentialWhenTogglingOwsRoute() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // The route read back from APISIX carries the OWS standard marker the handler wrote at
        // CREATE, so the toggle knows it is a map-service route.
        Map<String, Object> value = new HashMap<>();
        value.put("uri", "/v1/datasets/ds-001/map");
        value.put("upstream_id", "ds-001-ows");
        Map<String, Object> labels = new HashMap<>();
        labels.put("civitas-named-api-standard", "OWS");
        value.put("labels", labels);
        Map<String, Object> proxyRewrite = new HashMap<>();
        proxyRewrite.put(
            "regex_uri", List.of("^/v1/datasets/ds-001/map(/.*)?$", "/geoserver/ds_001/ows$1"));
        Map<String, Object> plugins = new HashMap<>();
        plugins.put("proxy-rewrite", proxyRewrite);
        value.put("plugins", plugins);
        server.enqueue(jsonResponse(200, Map.of("value", value)));
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("map", "rid-map"));
        payload.put("serviceId", "ds-001");
        payload.put("openDataAccess", false); // toggle to protected
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

        assertEquals("STEP_COMPLETED", result.type());
        takeRequest(); // GET
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());

        // Protected → the gateway gate applies to OWS too...
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
        // ...but the map-service route never gets a FROST credential or its tracking label.
        assertNull(authSetHeadersOf(routeBody), "OWS toggle must not inject a FROST credential");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultLabels = (Map<String, Object>) routeBody.get("labels");
        assertFalse(resultLabels.containsKey("civitas-frost-upstream-auth-header"));
        assertEquals("OWS", resultLabels.get("civitas-named-api-standard"));
        // Re-applying the state also (re-)installs the capabilities URL rewrite, so a route created
        // before this feature is healed on the next UPDATE/RESTORE.
        @SuppressWarnings("unchecked")
        Map<String, Object> responseRewrite =
            (Map<String, Object>) pluginsOf(routeBody).get("response-rewrite");
        assertNotNull(
            responseRewrite, "toggling an OWS route (re-)installs the capabilities rewrite");
        @SuppressWarnings("unchecked")
        List<Object> filters = (List<Object>) responseRewrite.get("filters");
        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) filters.get(0);
        assertEquals(
            "https?://[^/]+/geoserver/ds_001/(wfs|wms|wcs|wps|wmts|ows|gwc)", filter.get("regex"));
        assertEquals("https://api.example.test/v1/datasets/ds-001/map", filter.get("replace"));
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
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");
        SagaCommandMessage command =
            new SagaCommandMessage(
                "EXECUTE_STEP", "m", "saga-001", "delete-route", "apisix", "DELETE_ROUTE", payload);

        SagaCommandResult result = handler.handle(command);

        assertEquals("STEP_COMPLETED", result.type());
        // Two slug routes + both per-dataset upstreams (FROST + map server). The DELETE payload
        // carries no standard, so both upstreams are torn down (404-tolerant for the absent one).
        assertEquals(4, server.getRequestCount());
        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(paths.contains("/apisix/admin/routes/rid-weather"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001-ows"));
      }
    }

    @Test
    @DisplayName("tolerates an already-gone slug route (404) and still deletes the rest + upstream")
    void shouldTolerate404OnIndividualSlugRoute() {
      try (ApisixSagaHandler handler = createHandler()) {
        // First delete is an already-gone route (404), the remaining route + upstreams are present.
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        // Two slug routes + both per-dataset upstreams (FROST + map server).
        assertEquals(4, server.getRequestCount());
      }
    }

    @Test
    @DisplayName(
        "DELETE_ROUTE is idempotent: a 404 on every expected route-id counts as success (no"
            + " STEP_FAILED); state drift is surfaced via WARN log, not a failure")
    void shouldCompleteWhenNoTargetRoutesExist() {
      try (ApisixSagaHandler handler = createHandler()) {
        for (int i = 0; i < 4; i++) {
          server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        }

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
        // Two slug routes + both per-dataset upstreams (FROST + map server) — all 404, all
        // tolerated.
        assertEquals(4, server.getRequestCount());
      }
    }

    @Test
    @DisplayName(
        "retries the upstream delete while APISIX still reports a stale route reference, then"
            + " completes")
    void shouldRetryUpstreamDeleteOnStaleRouteReference() {
      try (ApisixSagaHandler handler = createHandler()) {
        // Route delete succeeds; the immediately following upstream delete still sees the route in
        // APISIX's worker-local route cache, then succeeds once the cache has caught up.
        server.enqueue(new MockResponse.Builder().code(200).build()); // route delete
        server.enqueue(staleRouteReferenceResponse()); // ds-001 upstream attempt 1
        server.enqueue(new MockResponse.Builder().code(200).build()); // ds-001 upstream attempt 2
        server.enqueue(new MockResponse.Builder().code(200).build()); // ds-001-ows upstream

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("things", "rid-things"));
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
        // 1 route + 2 upstream attempts for ds-001 (one rejected, one accepted) + 1 for ds-001-ows.
        assertEquals(4, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("fails the step when the stale route reference does not clear within the retries")
    void shouldFailWhenStaleRouteReferencePersists() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build()); // route delete
        server.enqueue(staleRouteReferenceResponse());
        server.enqueue(staleRouteReferenceResponse());
        server.enqueue(staleRouteReferenceResponse());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("things", "rid-things"));
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

        // A reference that outlives the retry budget is surfaced, not waited out.
        assertEquals("STEP_FAILED", result.type());
        // 1 route + exactly 3 upstream attempts: pins the budget, which STEP_FAILED alone does not.
        assertEquals(4, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("does not retry an upstream delete rejected for an unrelated reason")
    void shouldNotRetryUnrelatedUpstreamDeleteFailure() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build()); // route delete
        server.enqueue(
            new MockResponse.Builder()
                .code(400)
                .body("{\"error_msg\":\"invalid configuration\"}")
                .build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("things", "rid-things"));
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

        assertEquals("STEP_FAILED", result.type());
        // 1 route + a single (non-retried) upstream attempt — the step aborts on the first
        // unrelated rejection.
        assertEquals(2, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("drops a routeIds entry with a null value and still deletes the valid route(s)")
    void shouldDropNullValuedRouteIdEntry() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        // Only the valid 'traffic' route + both per-dataset upstreams are deleted; the null-valued
        // 'weather' entry is dropped rather than NPE-ing or aborting the step.
        assertEquals(3, server.getRequestCount());
        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001-ows"));
      }
    }
  }

  @Nested
  @DisplayName("RESTORE_ROUTE per named API (slug-keyed route model, issue #1368)")
  class RestoreRoutePerNamedApi {

    private MockResponse privateRouteGet() {
      Map<String, Object> value = new HashMap<>();
      value.put("uri", "/v1/datasets/ds-001/x");
      value.put("plugin_config_id", "auth-plugin-1");
      return jsonResponse(200, Map.of("value", value));
    }

    @Test
    @DisplayName("skips a slug route that is already gone (404) and restores the rest")
    void shouldSkipGoneSlugAndRestoreOthers() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // One slug route is already gone (404 on GET), the other is present and restorable.
        server.enqueue(new MockResponse.Builder().code(404).build());
        server.enqueue(privateRouteGet());
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic", "weather", "rid-weather"));
        payload.put("serviceId", "ds-001");

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
        // The gone route is skipped — only the present route is PUT back (2 GETs + 1 PUT).
        assertEquals(3, server.getRequestCount());
      }
    }

    @Test
    @DisplayName("restores a slug route as protected (routes are always protected)")
    void shouldRestoreSlugAsProtected() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(privateRouteGet());
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("traffic", "rid-traffic"));
        payload.put("serviceId", "ds-001");

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
        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
        // Default false → protected → plugin_config_id retained.
        assertTrue(body.containsKey("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("restoring an OWS route applies the gateway gate but injects no FROST credential")
    void shouldNotInjectFrostCredentialWhenRestoringOwsRoute() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // The route read back from APISIX carries the OWS standard marker the handler wrote at
        // CREATE, so the compensation knows it is a map-service route (the standard is not in the
        // RESTORE payload).
        Map<String, Object> value = new HashMap<>();
        value.put("uri", "/v1/datasets/ds-001/map");
        value.put("upstream_id", "ds-001-ows");
        Map<String, Object> labels = new HashMap<>();
        labels.put("civitas-named-api-standard", "OWS");
        value.put("labels", labels);
        Map<String, Object> proxyRewrite = new HashMap<>();
        proxyRewrite.put(
            "regex_uri", List.of("^/v1/datasets/ds-001/map(/.*)?$", "/geoserver/ds_001/ows$1"));
        Map<String, Object> plugins = new HashMap<>();
        plugins.put("proxy-rewrite", proxyRewrite);
        value.put("plugins", plugins);
        server.enqueue(jsonResponse(200, Map.of("value", value)));
        server.enqueue(new MockResponse.Builder().code(200).build());

        Map<String, Object> payload = new HashMap<>();
        payload.put("routeIds", Map.of("map", "rid-map"));
        payload.put("serviceId", "ds-001");
        // Compensation must re-apply the gateway gate without ever injecting a FROST credential
        // onto a GeoServer route.

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
        takeRequest(); // GET
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());

        // Protected → the gateway gate applies to OWS too...
        assertEquals("auth-plugin-1", routeBody.get("plugin_config_id"));
        // ...but the map-service route never gets a FROST credential or its tracking label.
        assertNull(authSetHeadersOf(routeBody), "OWS restore must not inject a FROST credential");
        @SuppressWarnings("unchecked")
        Map<String, Object> resultLabels = (Map<String, Object>) routeBody.get("labels");
        assertFalse(resultLabels.containsKey("civitas-frost-upstream-auth-header"));
        assertEquals("OWS", resultLabels.get("civitas-named-api-standard"));
      }
    }
  }

  @Nested
  @DisplayName("Open-data method gate + routeIds drift handling (MR !547 findings 1, 2, 5)")
  class MethodGateAndDriftHandling {

    private static final List<String> OPEN_DATA_METHODS = List.of("GET", "HEAD", "OPTIONS");

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

    @Test
    @DisplayName("protected routes carry no methods filter (OPA gates per request)")
    void shouldNotRestrictMethodsOnProtectedRoute() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        handler.handle(createRouteCommand(false));

        takeRequest(); // upstream
        Map<String, Object> body = requestBodyAsMap(takeRequest());
        assertFalse(body.containsKey("methods"));
      }
    }

    @Test
    @DisplayName("UPDATE toggle open→protected removes the method gate (OPA takes over)")
    void shouldRemoveMethodGateWhenTogglingToProtected() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        Map<String, Object> value = new HashMap<>();
        value.put("uri", "/v1/datasets/ds-001/data");
        value.put("methods", new ArrayList<>(OPEN_DATA_METHODS));
        server.enqueue(jsonResponse(200, Map.of("value", value)));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
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
        assertEquals(0, server.getRequestCount());
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
        assertEquals(0, server.getRequestCount());
      }
    }

    @Test
    @DisplayName(
        "DELETE derives the deterministic routeId for slugs missing from the map (finding 5)")
    void shouldDeleteDerivedRouteIdForSlugWithoutPersistedId() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        // Persisted route + derived route + both per-dataset upstreams (FROST + map server).
        assertEquals(4, server.getRequestCount());
        List<String> paths = recordedPaths();
        assertTrue(paths.contains("/apisix/admin/routes/rid-traffic"));
        assertTrue(
            paths.contains("/apisix/admin/routes/" + NamedApiHelper.derive("ds-001", "weather")),
            "the drifted slug's route must be deleted under its derived deterministic id");
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001"));
        assertTrue(paths.contains("/apisix/admin/upstreams/ds-001-ows"));
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

    @Test
    @DisplayName(
        "pins api host, uses /v1/datasets/{id}/{slug} layout, rewrites to FROST upstream and returns"
            + " publicUrl")
    void shouldBuildRouteBodyForApiHost() {
      try (ApisixSagaHandler handler = createHandler()) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        SagaCommandResult result = handler.handle(createRouteCommand());

        takeRequest(); // upstream
        Map<String, Object> routeBody = requestBodyAsMap(takeRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite =
            (Map<String, Object>)
                ((Map<String, Object>) routeBody.get("plugins")).get("proxy-rewrite");

        assertAll(
            () ->
                assertEquals(
                    List.of("api.example.test"),
                    routeBody.get("hosts"),
                    "route body must carry hosts to pin saga route to configured API host"),
            () ->
                assertEquals(
                    List.of("/v1/datasets/ds-001/data", "/v1/datasets/ds-001/data/*"),
                    routeBody.get("uris")),
            () ->
                assertEquals(
                    List.of(
                        "^/v1/datasets/ds-001/data/?$",
                        "/FROST-Server/v1.1/Projects(1)",
                        "^/v1/datasets/ds-001/data(/.+)$",
                        "/FROST-Server/v1.1/Projects(1)$1"),
                    proxyRewrite.get("regex_uri")),
            () ->
                assertEquals(
                    "https://api.example.test/v1/datasets/ds-001",
                    result.resultData().get("publicUrl")));
      }
    }
  }

  @Nested
  @DisplayName("CREATE_ROUTE injects FROST upstream Basic Auth header")
  class CreateRouteFrostUpstreamAuth {

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
      takeRequest(); // upstream
      Map<String, Object> routeBody = requestBodyAsMap(takeRequest());
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
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

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
    @DisplayName("merges configured proxy-rewrite headers.remove into route-level plugin")
    void shouldMergeProxyRewriteHeadersRemoveIntoRoute() {
      try (ApisixSagaHandler handler =
          createHandlerWithHeadersRemove("X-Allowed-Scope-Ids,X-Some-Other")) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

        handler.handle(createRouteCommand(false));

        Map<String, Object> proxyRewrite = captureProxyRewrite();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
        Object remove = headers.get("remove");
        assertEquals(
            List.of("X-Allowed-Scope-Ids", "X-Allowed-Pool-Ids", "X-Some-Other", "Accept-Encoding"),
            remove,
            "route-level proxy-rewrite must strip the configured headers (Finding P1 — plugin"
                + " config's proxy-rewrite is overridden by route precedence), plus the"
                + " Accept-Encoding the SensorThings link rewrite needs to see a plain body");
      }
    }

    @Test
    @DisplayName("injects API key header (not Authorization) when configured with API key auth")
    void shouldInjectApiKeyHeaderWhenConfigured() {
      try (ApisixSagaHandler handler =
          createHandlerWithApiKeyAuth("apisix-test-key", "X-API-Key")) {
        server.enqueue(created()); // upstream
        server.enqueue(created()); // route

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

    @Test
    @DisplayName("fails clearly when the route GET returns an unexpected body shape")
    void shouldFailClearlyOnMalformedRouteBody() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // value should be a route object, not a string
        server.enqueue(jsonResponse(200, Map.of("value", "not-an-object")));

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
    @DisplayName("strips read-only fields (create_time, update_time) from the PUT body")
    void shouldStripReadOnlyFields() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(jsonResponse(200, Map.of("value", existingRoute(true))));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
        assertFalse(body.containsKey("create_time"));
        assertFalse(body.containsKey("update_time"));
      }
    }

    @Test
    @DisplayName("normalizes a route whose rewrite still passes a lone trailing slash upstream")
    void shouldNormalizeTrailingSlashOnExistingRoute() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        // A route provisioned before the rewrite was split into pairs forwards "<path>/" as
        // "<upstream>/", which reaches a different service than "<path>" does.
        server.enqueue(jsonResponse(200, Map.of("value", existingRoute(false))));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> proxyRewrite =
            (Map<String, Object>) ((Map<String, Object>) body.get("plugins")).get("proxy-rewrite");
        assertEquals(
            List.of(
                "^/v1/datasets/ds-001/?$",
                "/FROST-Server/v1.1/Projects(1)",
                "^/v1/datasets/ds-001(/.+)$",
                "/FROST-Server/v1.1/Projects(1)$1"),
            RouteAuthConfigurer.readStringList(proxyRewrite.get("regex_uri")));
        // An STA endpoint addresses entities by sub-path, so the wildcard stays.
        assertEquals(
            List.of("/v1/datasets/ds-001", "/v1/datasets/ds-001/*"),
            RouteAuthConfigurer.readStringList(body.get("uris")));
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
        server.enqueue(jsonResponse(200, Map.of("value", existing)));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
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
        server.enqueue(jsonResponse(200, Map.of("value", existing)));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
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

        @SuppressWarnings("unchecked")
        List<Object> removeList = (List<Object>) resultHeaders.get("remove");
        assertTrue(
            removeList.contains("X-User-Strip"),
            "user-defined headers.remove entries must survive the merge");
      }
    }

    @Test
    @DisplayName("preserves existing route fields like uris, hosts, upstream_id")
    void shouldPreserveExistingRouteFields() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(jsonResponse(200, Map.of("value", existingRoute(true))));
        server.enqueue(new MockResponse.Builder().code(200).build());

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

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
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
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());
        server.enqueue(new MockResponse.Builder().code(200).build());

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
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

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
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());
        server.enqueue(new MockResponse.Builder().code(404).body("Key not found").build());

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

    @Test
    @DisplayName("returns COMPENSATION_COMPLETED and restores previous private state")
    void shouldRestorePreviousPrivateState() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(jsonResponse(200, Map.of("value", existingRoute(false))));
        server.enqueue(new MockResponse.Builder().code(200).build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001")));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals("saga-001", result.sagaId());

        takeRequest(); // GET
        Map<String, Object> body = requestBodyAsMap(takeRequest());
        assertEquals("auth-plugin-1", body.get("plugin_config_id"));
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on HTTP error")
    void shouldReturnCompensationFailureOnError() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(jsonResponse(200, Map.of("value", existingRoute(false))));
        server.enqueue(new MockResponse.Builder().code(500).body("Internal Server Error").build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001")));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("returns COMPENSATION_FAILED on network error")
    void shouldReturnCompensationFailureOnNetworkError() throws IOException {
      try (ApisixSagaHandler handler = createHandler()) {
        server.close();

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001")));

        assertEquals("COMPENSATION_FAILED", result.type());
        assertNotNull(result.error());
      }
    }

    @Test
    @DisplayName("skips restore when the route is already gone (404) — idempotent compensation")
    void shouldSkipRestoreWhenRouteAlreadyGone() {
      try (ApisixSagaHandler handler = createHandlerWithPluginConfig("auth-plugin-1")) {
        server.enqueue(new MockResponse.Builder().code(404).build());

        SagaCommandResult result =
            handler.handle(
                createCommand(
                    "COMPENSATE_STEP",
                    "RESTORE_ROUTE",
                    Map.of("routeIds", Map.of("data", "ds-001"), "serviceId", "ds-001")));

        assertEquals("COMPENSATION_COMPLETED", result.type());
        assertEquals(1, server.getRequestCount());
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
  @DisplayName("Proxy-rewrite regex pattern (applies the production rewrite pairs)")
  class ProxyRewriteRegex {

    /**
     * Applies the production rewrite pairs the way APISIX does — pairs are tried in order and the
     * first match wins. Expected values below state what each upstream requires of the path it
     * receives, so they hold the pattern to an external contract rather than restating it. The
     * genuine end-to-end coverage that a real gateway rewrites this way lives in {@code
     * ApisixSagaHandlerRoutingIT}.
     */
    private String applyRewrite(
        String routePath, String upstreamPath, RouteUpstreamKind kind, String requestPath) {
      String[] pairs = PathRewrite.pairs(routePath, upstreamPath, kind);
      for (int i = 0; i < pairs.length; i += 2) {
        Matcher matcher = Pattern.compile(pairs[i]).matcher(requestPath);
        if (matcher.matches()) {
          return matcher.replaceFirst(pairs[i + 1]);
        }
      }
      return null;
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        nullValues = "NULL",
        value = {
          "rewrites sub-path to upstream path | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-001/Things | /FROST-Server/v1.1/Projects(1)/Things",
          "rewrites nested sub-path | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-001/Things(42)/Datastreams | /FROST-Server/v1.1/Projects(1)/Things(42)/Datastreams",
          "rewrites base path without trailing slash | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1)",
          "drops a lone trailing slash, which FROST answers with 404 | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-001/ | /FROST-Server/v1.1/Projects(1)",
          "rewrites with query string in path | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-001/Things?$top=10&$skip=0 | /FROST-Server/v1.1/Projects(1)/Things?$top=10&$skip=0",
          "keeps an OWS request on the throttled map service | OWS | /v1/datasets/ds-001/map | /geoserver/ds_001/ows | /v1/datasets/ds-001/map | /geoserver/ds_001/ows",
          "drops a lone trailing slash, which GeoServer routes to the unthrottled admin service | OWS | /v1/datasets/ds-001/map | /geoserver/ds_001/ows | /v1/datasets/ds-001/map/ | /geoserver/ds_001/ows",
          "does not rewrite an OWS sub-path, which the route no longer matches | OWS | /v1/datasets/ds-001/map | /geoserver/ds_001/ows | /v1/datasets/ds-001/map/wfs | NULL",
          "does not match different dataset ID | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /v1/datasets/ds-002/Things | NULL",
          "does not match unrelated path | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /api/v1/users | NULL",
          "does not match legacy /datasets path without /v1 prefix | STA | /v1/datasets/ds-001 | /FROST-Server/v1.1/Projects(1) | /datasets/ds-001/Things | NULL",
        })
    void shouldApplyRewriteRegex(
        String description,
        RouteUpstreamKind kind,
        String routePath,
        String upstreamPath,
        String requestPath,
        String expected) {
      String result = applyRewrite(routePath, upstreamPath, kind, requestPath);
      assertEquals(expected, result);
    }
  }

  /**
   * Builds a mutable, JSON-shaped APISIX route response approximating what {@code readRoute} would
   * deliver — used by UPDATE_ROUTE/RESTORE_ROUTE tests to drive the GET→mutate→PUT flow.
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
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
    when(mockConfig.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
    when(mockConfig.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn("X-API-Key");
    when(mockConfig.getProperty("apisix.geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://civitas-geoserver:8080/geoserver");
    when(mockConfig.getProperty("apisix.proxy.rewrite.headers.remove"))
        .thenReturn(csvHeadersToRemove);
    handler.initialize(mockConfig);
    return handler;
  }

  private ApisixSagaHandler createHandlerWithApiKeyAuth(String apiKey, String apiKeyHeader) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
    when(mockConfig.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.api.key")).thenReturn(apiKey);
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn(apiKeyHeader);
    when(mockConfig.getProperty("apisix.geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://civitas-geoserver:8080/geoserver");
    handler.initialize(mockConfig);

    return handler;
  }

  private ApisixSagaHandler createHandlerWithConfig(String pluginConfig, String serviceId) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn(pluginConfig);
    when(mockConfig.getProperty("apisix.service.id")).thenReturn(serviceId);
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
    when(mockConfig.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn("X-API-Key");
    when(mockConfig.getProperty("apisix.geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn("http://civitas-geoserver:8080/geoserver");
    handler.initialize(mockConfig);

    return handler;
  }

  private ApisixSagaHandler createHandlerWithGeoserverUrl(String geoserverUrl) {
    ApisixSagaHandler handler = new ApisixSagaHandler();
    AdapterConfig mockConfig = mock(AdapterConfig.class);
    when(mockConfig.getProperty("apisix.admin.key")).thenReturn("test-admin-key");
    when(mockConfig.getProperty("apisix.admin.url", "http://localhost:9180"))
        .thenReturn(server.url("/").toString());
    when(mockConfig.getProperty("apisix.plugin.config.id")).thenReturn("auth-plugin-default");
    when(mockConfig.getProperty("apisix.service.id")).thenReturn("svc-frost-server");
    when(mockConfig.getProperty("apisix.api.host")).thenReturn("api.example.test");
    when(mockConfig.getProperty("apisix.api.public.url")).thenReturn("https://api.example.test");
    when(mockConfig.getProperty("apisix.frost.basic.auth.username")).thenReturn("frost-user");
    when(mockConfig.getProperty("apisix.frost.basic.auth.password")).thenReturn("frost-pass");
    when(mockConfig.getProperty("apisix.frost.api.key.header", "X-API-Key"))
        .thenReturn("X-API-Key");
    when(mockConfig.getProperty("apisix.geoserver.url", "http://localhost:8080/geoserver"))
        .thenReturn(geoserverUrl);
    handler.initialize(mockConfig);
    return handler;
  }

  /** Extracts the {@code plugins} block from a captured route body. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> pluginsOf(Map<String, Object> routeBody) {
    return (Map<String, Object>) routeBody.get("plugins");
  }

  /** Extracts the {@code plugins.proxy-rewrite} block from a captured route body. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> proxyRewriteOf(Map<String, Object> routeBody) {
    return (Map<String, Object>) pluginsOf(routeBody).get("proxy-rewrite");
  }

  /**
   * Extracts {@code plugins.proxy-rewrite.headers.set} (the upstream-credential block), or null.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> authSetHeadersOf(Map<String, Object> routeBody) {
    Map<String, Object> headers = (Map<String, Object>) proxyRewriteOf(routeBody).get("headers");
    return headers == null ? null : (Map<String, Object>) headers.get("set");
  }

  /** A bare 201, used for PUT (upstream/route create) responses. */
  private static MockResponse created() {
    return new MockResponse.Builder().code(201).build();
  }

  /** The APISIX Admin API's "upstream still referenced by a route" rejection (400). */
  private static MockResponse staleRouteReferenceResponse() {
    return new MockResponse.Builder()
        .code(400)
        .body(
            "{\"error_msg\":\"can not delete this upstream, route [rid-things] is still using"
                + " it now\"}")
        .build();
  }

  /** A 200 GET response wrapping the given route value in the etcd {@code value} envelope. */
  private static MockResponse routeGet(String uri, String pluginConfigId) {
    Map<String, Object> value = new HashMap<>();
    value.put("uri", uri);
    value.put("plugin_config_id", pluginConfigId);
    return jsonResponse(200, Map.of("value", value));
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

  private static Map<String, Object> requestBodyAsMap(RecordedRequest request) {
    try {
      return PayloadConverter.readMap(request.getBody().toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Drains every recorded request's encoded path, in call order. Call ONLY ONCE per test — {@code
   * takeRequest()} drains the queue, so a second call would see nothing.
   */
  private List<String> recordedPaths() {
    List<String> paths = new ArrayList<>();
    int count = server.getRequestCount();
    for (int i = 0; i < count; i++) {
      paths.add(takeRequest().getUrl().encodedPath());
    }
    return paths;
  }

  /** {@link MockWebServer#takeRequest()}, wrapping its checked {@link InterruptedException}. */
  private RecordedRequest takeRequest() {
    try {
      return server.takeRequest();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private SagaCommandMessage createCommand(
      String type, String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        type, "msg-001", "saga-001", "create-route", "apisix", operation, payload);
  }
}

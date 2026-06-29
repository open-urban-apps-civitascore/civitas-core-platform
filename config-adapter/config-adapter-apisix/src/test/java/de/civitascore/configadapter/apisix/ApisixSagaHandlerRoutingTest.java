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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end routing tests for the published-data API route created by {@link ApisixSagaHandler}
 * (issue #1368).
 *
 * <p>Starts a stub upstream in the shared test network and exercises the APISIX gateway to prove
 * that:
 *
 * <ul>
 *   <li>Requests on the configured API host ({@code apisix.api.host}) reach the FROST-Server
 *       upstream with the expected path rewrite from {@code /v1/datasets/{id}/...} to {@code
 *       /FROST-Server/v1.1/Projects(1)/...}.
 *   <li>The legacy {@code /datasets/{id}} path (without {@code /v1} prefix) is no longer matched.
 *   <li>Query strings are preserved through the rewrite.
 *   <li>The saga-provisioned route wins over a coexisting {@code /v1/*} catch-all on the same host
 *       — this pins the APISIX radix-tree URI-specificity guarantee that replaced the earlier
 *       data/api host split (issue #1368).
 * </ul>
 *
 * <p><b>IDE setup:</b> the JDK HTTP client refuses to set the {@code Host} header by default. The
 * Surefire build passes {@code -Djdk.httpclient.allowRestrictedHeaders=host} via {@code pom.xml};
 * IDE run configurations must set the same system property (VM options), otherwise {@link
 * #sendGatewayRequest} throws {@link IllegalArgumentException}: {@code restricted header name:
 * "Host"}.
 */
class ApisixSagaHandlerRoutingTest extends AbstractApisixIntegrationTest {

  private static final String API_HOST = "api.example.test";
  private static final String API_PUBLIC_URL = "https://api.example.test";
  private static final String FROST_USER = "frost-user";
  private static final String FROST_PASS = "frost-pass";
  private static final String STUB_ALIAS = "stub-upstream";
  private static final int STUB_PORT = 8080;
  // Per-named-API model: one route per slug. These routing tests use a single named API ("data").
  private static final String SLUG = "data";

  private static String routeId(String datasetId) {
    return NamedApiHelper.derive(datasetId, SLUG);
  }

  @SuppressWarnings("resource")
  private static final GenericContainer<?> STUB_UPSTREAM;

  static {
    STUB_UPSTREAM =
        new GenericContainer<>(DockerImageName.parse("mendhak/http-https-echo:34"))
            .withNetwork(NETWORK)
            .withNetworkAliases(STUB_ALIAS)
            .withEnv("HTTP_PORT", String.valueOf(STUB_PORT))
            .withExposedPorts(STUB_PORT)
            .waitingFor(Wait.forHttp("/").forPort(STUB_PORT).forStatusCode(200))
            .withReuse(false);
    STUB_UPSTREAM.start();
    Runtime.getRuntime().addShutdownHook(new Thread(STUB_UPSTREAM::stop));
  }

  private ApisixSagaHandler sagaHandler;
  private String gatewayBaseUrl;

  @BeforeEach
  void setUpSagaHandler() throws Exception {
    createPluginConfigDirectly("auth-plugin-default", Map.of());

    Map<String, Object> props = new HashMap<>();
    props.put("apisix.admin.url", adminApiUrl);
    props.put("apisix.admin.key", ADMIN_API_KEY);
    props.put("apisix.api.host", API_HOST);
    props.put("apisix.api.public.url", API_PUBLIC_URL);
    props.put("apisix.plugin.config.id", "auth-plugin-default");
    props.put("apisix.proxy.rewrite.headers.remove", "X-Allowed-Scope-Ids");
    props.put("apisix.frost.basic.auth.username", FROST_USER);
    props.put("apisix.frost.basic.auth.password", FROST_PASS);
    AppConfig config = new AppConfig(new MapConfiguration(props));

    sagaHandler = new ApisixSagaHandler();
    sagaHandler.initialize(config);

    gatewayBaseUrl = "http://" + APISIX.getHost() + ":" + APISIX.getMappedPort(9080);
  }

  @AfterEach
  void tearDownSagaHandler() {
    if (sagaHandler != null) {
      sagaHandler.close();
    }
  }

  @Test
  void shouldRouteToUpstreamOnApiHost() throws Exception {
    String datasetId = "hi-" + UUID.randomUUID();
    createRouteAndAwaitGateway(datasetId);

    HttpResponse<String> response =
        sendGatewayRequest("/v1/datasets/" + datasetId + "/" + SLUG + "/Things", API_HOST);

    assertEquals(
        200,
        response.statusCode(),
        "expected 200 for request on api host — got: " + response.body());

    JsonNode echoed = objectMapper.readTree(response.body());
    String upstreamPath = echoed.get("path").asText();
    assertEquals(
        "/FROST-Server/v1.1/Projects(1)/Things",
        upstreamPath,
        "proxy-rewrite must map /v1/datasets/{id}/Things onto the FROST upstream path");
  }

  @Test
  void shouldReturn404ForLegacyPathWithoutV1Prefix() throws Exception {
    String datasetId = "hi-" + UUID.randomUUID();
    createRouteAndAwaitGateway(datasetId);

    HttpResponse<String> response =
        sendGatewayRequest("/datasets/" + datasetId + "/Things", API_HOST);

    assertEquals(
        404,
        response.statusCode(),
        "legacy /datasets path must no longer match — body: " + response.body());
  }

  @Test
  void shouldWinOverV1CatchAllOnSameHost() throws Exception {
    String datasetId = "shadow-" + UUID.randomUUID();

    // 1. Provision the saga route first (FROST upstream with proxy-rewrite to
    //    /FROST-Server/v1.1/Projects(1)/...).
    createRouteAndAwaitGateway(datasetId);

    // 2. Add a /v1/* catch-all on the SAME api host, pointing at the SAME stub container
    //    but WITHOUT any proxy-rewrite — created AFTER the saga route so creation-time
    //    ordering can't save us if URI-specificity is ever broken.
    String catchAllUpstreamId = "catch-all-upstream-" + datasetId;
    String catchAllRouteId = "catch-all-route-" + datasetId;
    createUpstreamDirectly(
        catchAllUpstreamId,
        Map.of("type", "roundrobin", "nodes", Map.of(STUB_ALIAS + ":" + STUB_PORT, 1)));
    createRouteDirectly(
        catchAllRouteId,
        Map.of(
            "uri",
            "/v1/*",
            "hosts",
            List.of(API_HOST),
            "upstream_id",
            catchAllUpstreamId,
            "status",
            1));

    // 3. Request the dataset path. If the saga route wins, the stub sees the rewritten
    //    FROST path. If the catch-all leaks through, the stub sees the original URL,
    //    which is the #1368 regression.
    HttpResponse<String> response =
        sendGatewayRequest("/v1/datasets/" + datasetId + "/" + SLUG + "/Things", API_HOST);

    assertEquals(200, response.statusCode(), "expected 200 — body: " + response.body());
    JsonNode echoed = objectMapper.readTree(response.body());
    assertEquals(
        "/FROST-Server/v1.1/Projects(1)/Things",
        echoed.get("path").asText(),
        "saga route must win over /v1/* catch-all via URI specificity — if the catch-all"
            + " leaked, the stub would echo /v1/datasets/"
            + datasetId
            + "/"
            + SLUG
            + "/Things (issue #1368 regression)");
  }

  @Test
  void shouldForwardBasicAuthHeaderToUpstreamForPrivateRoute() throws Exception {
    String datasetId = "private-" + UUID.randomUUID();
    createRouteAndAwaitGateway(datasetId, false);

    HttpResponse<String> response =
        sendGatewayRequest("/v1/datasets/" + datasetId + "/" + SLUG + "/Things", API_HOST);

    assertEquals(
        200,
        response.statusCode(),
        "private route must still proxy to upstream — body: " + response.body());

    JsonNode echoed = objectMapper.readTree(response.body());
    String expected =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString(
                    (FROST_USER + ":" + FROST_PASS)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(
        expected,
        echoed.get("headers").get("authorization").asText(),
        "upstream stub must receive the FROST Basic Auth header injected by proxy-rewrite"
            + " (Finding P2 — actual forwarded-header behavior, not just route config)");
  }

  @Test
  void shouldStripClientSuppliedScopeHeaderFromUpstreamForPrivateRoute() throws Exception {
    assertScopeHeaderStripped("scope-priv-" + UUID.randomUUID(), false);
  }

  @Test
  void shouldStripClientSuppliedScopeHeaderFromUpstreamForPublicRoute() throws Exception {
    assertScopeHeaderStripped("scope-pub-" + UUID.randomUUID(), true);
  }

  private void assertScopeHeaderStripped(String datasetId, boolean openDataAccess)
      throws Exception {
    createRouteAndAwaitGateway(datasetId, openDataAccess);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(gatewayBaseUrl + "/v1/datasets/" + datasetId + "/" + SLUG + "/Things"))
            .header("Host", API_HOST)
            .header("X-Allowed-Scope-Ids", "malicious-bypass-attempt-*")
            .header("X-Allowed-Pool-Ids", "malicious-pool-bypass-attempt")
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode());
    JsonNode echoed = objectMapper.readTree(response.body());
    JsonNode headers = echoed.get("headers");
    assertEquals(
        null,
        headers.get("x-allowed-scope-ids"),
        "client-supplied X-Allowed-Scope-Ids must be stripped before reaching the upstream"
            + " regardless of openDataAccess — strip is a general saga-route protection — got"
            + " headers: "
            + headers);
    assertEquals(
        null,
        headers.get("x-allowed-pool-ids"),
        "client-supplied X-Allowed-Pool-Ids must also be stripped — the backend trusts it for"
            + " datapool collection filtering, so a spoofed value would bypass pool scoping — got"
            + " headers: "
            + headers);
  }

  @Test
  void shouldNotForwardBasicAuthHeaderToUpstreamForPublicRoute() throws Exception {
    String datasetId = "public-" + UUID.randomUUID();
    createRouteAndAwaitGateway(datasetId, true);

    HttpResponse<String> response =
        sendGatewayRequest("/v1/datasets/" + datasetId + "/" + SLUG + "/Things", API_HOST);

    assertEquals(200, response.statusCode(), "public route must proxy");

    JsonNode echoed = objectMapper.readTree(response.body());
    JsonNode headers = echoed.get("headers");
    assertEquals(
        null,
        headers.get("authorization"),
        "public route must NOT forward an Authorization header to the upstream — got: " + headers);
  }

  @Test
  void shouldPreserveQueryStringThroughRewrite() throws Exception {
    String datasetId = "hi-" + UUID.randomUUID();
    createRouteAndAwaitGateway(datasetId);

    HttpResponse<String> response =
        sendGatewayRequest(
            "/v1/datasets/" + datasetId + "/" + SLUG + "/Things?$top=1&$skip=2", API_HOST);

    assertEquals(200, response.statusCode(), "expected 200 — body: " + response.body());
    JsonNode echoed = objectMapper.readTree(response.body());
    String upstreamPath = echoed.get("path").asText();
    assertEquals(
        "/FROST-Server/v1.1/Projects(1)/Things",
        upstreamPath,
        "upstream must see rewritten FROST path");
    JsonNode query = echoed.get("query");
    assertEquals(
        "1",
        query.get("$top").asText(),
        "upstream must see original $top query parameter — got: " + query);
    assertEquals(
        "2",
        query.get("$skip").asText(),
        "upstream must see original $skip query parameter — got: " + query);
  }

  @Test
  void shouldRouteThroughApisixWhenServiceIdConfigured() throws Exception {
    String datasetId = "svc-" + UUID.randomUUID();
    String serviceId = "svc-frost-it-" + datasetId;

    // Seed the APISIX Service the saga route will reference — this is the Service OPA reads via
    // with_service=true (input.service.name → frost_server dispatch). Give it a valid upstream.
    createServiceDirectly(
        serviceId,
        Map.of(
            "name",
            "frost-server",
            "upstream",
            Map.of("type", "roundrobin", "nodes", Map.of(STUB_ALIAS + ":" + STUB_PORT, 1))));

    // A handler configured WITH apisix.service.id stamps service_id onto every saga route.
    Map<String, Object> props = new HashMap<>();
    props.put("apisix.admin.url", adminApiUrl);
    props.put("apisix.admin.key", ADMIN_API_KEY);
    props.put("apisix.api.host", API_HOST);
    props.put("apisix.api.public.url", API_PUBLIC_URL);
    props.put("apisix.plugin.config.id", "auth-plugin-default");
    props.put("apisix.proxy.rewrite.headers.remove", "X-Allowed-Scope-Ids");
    props.put("apisix.frost.basic.auth.username", FROST_USER);
    props.put("apisix.frost.basic.auth.password", FROST_PASS);
    props.put("apisix.service.id", serviceId);

    try (ApisixSagaHandler svcHandler = new ApisixSagaHandler()) {
      svcHandler.initialize(new AppConfig(new MapConfiguration(props)));

      SagaCommandResult result =
          svcHandler.handle(
              new SagaCommandMessage(
                  "EXECUTE_STEP",
                  "msg-" + datasetId,
                  "saga-" + datasetId,
                  "create-route",
                  "apisix",
                  "CREATE_ROUTE",
                  Map.of(
                      "datasetId",
                      datasetId,
                      "upstreamUrl",
                      "http://" + STUB_ALIAS + ":" + STUB_PORT + "/FROST-Server/v1.1/Projects(1)",
                      "openDataAccess",
                      true,
                      "namedApis",
                      List.of(Map.of("slug", SLUG, "standard", "STA")))));
      assertEquals("STEP_COMPLETED", result.type());

      // The provisioned route actually carries the configured service_id...
      await()
          .atMost(10, SECONDS)
          .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                JsonNode route = getRouteFromApisix(routeId(datasetId));
                assertEquals(
                    serviceId,
                    route.get("value").get("service_id").asText(),
                    "saga route must reference the configured APISIX service_id (OPA with_service"
                        + " path)");
              });

      // ...and a real request still routes through that service_id-bearing route to the upstream,
      // proving APISIX accepts the route against a real instance — not just the JSON body shape.
      HttpResponse<String> response =
          sendGatewayRequest("/v1/datasets/" + datasetId + "/" + SLUG + "/Things", API_HOST);
      assertEquals(
          200,
          response.statusCode(),
          "a route carrying service_id must still route through APISIX — body: " + response.body());
      JsonNode echoed = objectMapper.readTree(response.body());
      assertEquals(
          "/FROST-Server/v1.1/Projects(1)/Things",
          echoed.get("path").asText(),
          "proxy-rewrite must still map onto the FROST upstream path with service_id present");
    }
  }

  @Test
  void shouldRouteOwsToGeoServerWorkspaceOwsPath() throws Exception {
    String datasetId = "ows-" + UUID.randomUUID();
    String workspace = WorkspaceNames.fromDatasetId(datasetId);
    String owsSlug = "map";

    // A handler whose map-server upstream is the stub container (standing in for GeoServer).
    Map<String, Object> props = new HashMap<>();
    props.put("apisix.admin.url", adminApiUrl);
    props.put("apisix.admin.key", ADMIN_API_KEY);
    props.put("apisix.api.host", API_HOST);
    props.put("apisix.api.public.url", API_PUBLIC_URL);
    props.put("apisix.plugin.config.id", "auth-plugin-default");
    props.put("apisix.proxy.rewrite.headers.remove", "X-Allowed-Scope-Ids");
    props.put("apisix.frost.basic.auth.username", FROST_USER);
    props.put("apisix.frost.basic.auth.password", FROST_PASS);
    props.put("apisix.geoserver.url", "http://" + STUB_ALIAS + ":" + STUB_PORT + "/geoserver");

    try (ApisixSagaHandler owsHandler = new ApisixSagaHandler()) {
      owsHandler.initialize(new AppConfig(new MapConfiguration(props)));

      SagaCommandResult result =
          owsHandler.handle(
              new SagaCommandMessage(
                  "EXECUTE_STEP",
                  "msg-" + datasetId,
                  "saga-" + datasetId,
                  "create-route",
                  "apisix",
                  "CREATE_ROUTE",
                  Map.of(
                      "datasetId",
                      datasetId,
                      // FROST upstreamUrl is always carried by the saga even for a map-only
                      // dataset;
                      // here it is unused because the only named API is OWS.
                      "upstreamUrl",
                      "http://" + STUB_ALIAS + ":" + STUB_PORT + "/FROST-Server/v1.1/Projects(1)",
                      "openDataAccess",
                      true,
                      "namedApis",
                      List.of(Map.of("slug", owsSlug, "standard", "OWS")))));
      assertEquals("STEP_COMPLETED", result.type());

      String mapRouteId = NamedApiHelper.derive(datasetId, owsSlug);
      await()
          .atMost(10, SECONDS)
          .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
          .ignoreExceptions()
          .untilAsserted(() -> assertTrue(getRouteFromApisix(mapRouteId).has("value")));

      // A single OWS route serves both WFS and WMS; the OGC service= query param selects it.
      HttpResponse<String> response =
          sendGatewayRequest(
              "/v1/datasets/" + datasetId + "/" + owsSlug + "?service=WFS&request=GetCapabilities",
              API_HOST);

      assertEquals(
          200,
          response.statusCode(),
          "OWS route must proxy to the GeoServer stub — body: " + response.body());
      JsonNode echoed = objectMapper.readTree(response.body());
      assertEquals(
          "/geoserver/" + workspace + "/ows",
          echoed.get("path").asText(),
          "proxy-rewrite must map the OWS named API onto the dataset's workspace OWS endpoint");
      JsonNode query = echoed.get("query");
      assertEquals(
          "WFS", query.get("service").asText(), "OGC service query parameter must be preserved");
      assertEquals("GetCapabilities", query.get("request").asText());
    }
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private void createRouteAndAwaitGateway(String datasetId) throws Exception {
    createRouteAndAwaitGateway(datasetId, true);
  }

  private void createRouteAndAwaitGateway(String datasetId, boolean openDataAccess)
      throws Exception {
    SagaCommandMessage command =
        new SagaCommandMessage(
            "EXECUTE_STEP",
            "msg-" + datasetId,
            "saga-" + datasetId,
            "create-route",
            "apisix",
            "CREATE_ROUTE",
            Map.of(
                "datasetId",
                datasetId,
                "upstreamUrl",
                "http://" + STUB_ALIAS + ":" + STUB_PORT + "/FROST-Server/v1.1/Projects(1)",
                "openDataAccess",
                openDataAccess,
                "namedApis",
                List.of(Map.of("slug", SLUG, "standard", "STA"))));
    SagaCommandResult result = sagaHandler.handle(command);
    assertEquals("STEP_COMPLETED", result.type());

    // Route creation is async in APISIX — poll until the route is observable.
    await()
        .atMost(10, SECONDS)
        .pollInterval(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId(datasetId));
              assertTrue(route.has("value"));
            });
  }

  private HttpResponse<String> sendGatewayRequest(String path, String host) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(gatewayBaseUrl + path))
            .header("Host", host)
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}

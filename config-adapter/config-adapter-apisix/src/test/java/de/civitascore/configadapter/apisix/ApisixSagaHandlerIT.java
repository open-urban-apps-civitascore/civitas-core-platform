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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.model.dataset.NamedApiHelper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link ApisixSagaHandler} against a real APISIX instance.
 *
 * <p>Asserts that the route created for a published dataset carries the configured API virtual host
 * and the /v1/datasets/{id} URI prefix so APISIX can dispatch published-data requests
 * deterministically (issue #1368).
 */
class ApisixSagaHandlerIT extends AbstractApisixIT {

  private static final String API_HOST = "api.example.test";
  private static final String API_PUBLIC_URL = "https://api.example.test";
  private static final String FROST_USER = "frost-user";
  private static final String FROST_PASS = "frost-pass";
  private static final String PLUGIN_CONFIG_ID = "auth-plugin-default";
  private static final String SERVICE_ID = "svc-frost-it";
  // Per-named-API model: the saga provisions one route per slug. These tests use a single named API
  // ("data"); the route id is the deterministic NamedApiHelper.derive(datasetId, slug).
  private static final String SLUG = "data";

  private ApisixSagaHandler sagaHandler;

  private static String routeId(String datasetId) {
    return NamedApiHelper.derive(datasetId, SLUG);
  }

  @BeforeEach
  void setUpSagaHandler() throws Exception {
    // APISIX rejects routes that reference an unknown plugin_config_id, so we provision a no-op
    // gateway-side stand-in before each test. Real deployments wire this to OIDC/OPA plugins.
    createPluginConfigDirectly(PLUGIN_CONFIG_ID, Map.of());

    // apisix.service.id is required: every saga route references it and OPA resolves the backend
    // from it via with_service=true. Seed the referenced Service so APISIX accepts the routes.
    createServiceDirectly(
        SERVICE_ID,
        Map.of(
            "name",
            "frost-server",
            "upstream",
            Map.of("type", "roundrobin", "nodes", Map.of("127.0.0.1:80", 1))));

    Map<String, Object> props = new HashMap<>();
    props.put("apisix.admin.url", adminApiUrl);
    props.put("apisix.admin.key", ADMIN_API_KEY);
    props.put("apisix.api.host", API_HOST);
    props.put("apisix.api.public.url", API_PUBLIC_URL);
    props.put("apisix.plugin.config.id", PLUGIN_CONFIG_ID);
    props.put("apisix.service.id", SERVICE_ID);
    props.put("apisix.proxy.rewrite.headers.remove", "X-Allowed-Scope-Ids");
    props.put("apisix.frost.basic.auth.username", FROST_USER);
    props.put("apisix.frost.basic.auth.password", FROST_PASS);
    AppConfig config = new AppConfig(new MapConfiguration(props));

    sagaHandler = new ApisixSagaHandler();
    sagaHandler.initialize(config);
  }

  @AfterEach
  void tearDownSagaHandler() {
    if (sagaHandler != null) {
      sagaHandler.close();
    }
  }

  @Test
  void createRouteShouldBindApiHostAndV1Prefix() throws Exception {
    String datasetId = "it-" + UUID.randomUUID();

    SagaCommandResult result = sagaHandler.handle(createRouteCommand(datasetId, true));

    assertEquals("STEP_COMPLETED", result.type());
    assertEquals(
        API_PUBLIC_URL + "/v1/datasets/" + datasetId, result.resultData().get("publicUrl"));

    JsonNode value = getRouteFromApisix(routeId(datasetId)).get("value");

    JsonNode hosts = value.get("hosts");
    assertNotNull(hosts, "route must carry hosts array (issue #1368)");
    assertTrue(hosts.isArray(), "hosts must be an array");
    assertEquals(1, hosts.size(), "expected exactly one host");
    assertEquals(API_HOST, hosts.get(0).asText());

    List<String> uris = extractUris(value);
    assertTrue(
        uris.contains("/v1/datasets/" + datasetId + "/" + SLUG),
        "uris must contain the /v1/datasets/{id}/{slug} root — got: " + uris);
    assertTrue(
        uris.contains("/v1/datasets/" + datasetId + "/" + SLUG + "/*"),
        "uris must contain the /v1/datasets/{id}/{slug}/* wildcard — got: " + uris);

    String regex = value.get("plugins").get("proxy-rewrite").get("regex_uri").get(0).asText();
    assertEquals("^/v1/datasets/" + datasetId + "/" + SLUG + "(/.*)?$", regex);
  }

  @Test
  void createRouteShouldInjectBasicAuthHeaderAndPluginConfigForPrivateProject() throws Exception {
    String datasetId = "it-" + UUID.randomUUID();

    SagaCommandResult result = sagaHandler.handle(createRouteCommand(datasetId, false));
    assertEquals("STEP_COMPLETED", result.type());

    JsonNode value = getRouteFromApisix(routeId(datasetId)).get("value");
    assertEquals(
        PLUGIN_CONFIG_ID,
        value.get("plugin_config_id").asText(),
        "private routes must carry plugin_config_id so OIDC/OPA gate stays in front (Finding P1.1)");

    JsonNode proxyRewrite = value.get("plugins").get("proxy-rewrite");
    JsonNode headers = proxyRewrite.get("headers");
    assertNotNull(
        headers, "private-data routes must carry proxy-rewrite headers for upstream auth");
    String authHeader = headers.get("set").get("Authorization").asText();

    String expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((FROST_USER + ":" + FROST_PASS).getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, authHeader);

    JsonNode remove = headers.get("remove");
    assertNotNull(
        remove,
        "route-level proxy-rewrite must include headers.remove so plugin_config's strip rules are"
            + " preserved despite Route-over-PluginConfig precedence (Finding P1)");
    assertEquals("X-Allowed-Scope-Ids", remove.get(0).asText());
  }

  @Test
  void updateRouteKeepsRouteProtectedAndCapturesNoPreviousOpen() throws Exception {
    String datasetId = "it-" + UUID.randomUUID();
    sagaHandler.handle(createRouteCommand(datasetId, true));

    // openDataAccess is ignored — the route is always protected. UPDATE re-applies the protected
    // state and captures no previousOpenDataAccess (there is no open/protected toggle anymore).
    SagaCommandResult result = sagaHandler.handle(updateRouteCommand(datasetId, false));
    assertEquals("STEP_COMPLETED", result.type());
    assertNull(result.compensationData().get("previousOpenDataAccess"));

    JsonNode value = getRouteFromApisix(routeId(datasetId)).get("value");
    assertEquals(
        PLUGIN_CONFIG_ID,
        value.get("plugin_config_id").asText(),
        "protected route must carry gateway-side plugin_config_id");

    JsonNode proxyRewrite = value.get("plugins").get("proxy-rewrite");
    String expected =
        "Basic "
            + Base64.getEncoder()
                .encodeToString((FROST_USER + ":" + FROST_PASS).getBytes(StandardCharsets.UTF_8));
    assertEquals(expected, proxyRewrite.get("headers").get("set").get("Authorization").asText());

    String regex = proxyRewrite.get("regex_uri").get(0).asText();
    assertEquals(
        "^/v1/datasets/" + datasetId + "/" + SLUG + "(/.*)?$",
        regex,
        "UPDATE must preserve the existing regex_uri (Finding 1 — PATCH would have wiped it)");
  }

  private SagaCommandMessage updateRouteCommand(String datasetId, boolean openDataAccess) {
    return new SagaCommandMessage(
        "EXECUTE_STEP",
        "msg-update-" + datasetId,
        "saga-it-update-" + datasetId,
        "update-route",
        "apisix",
        "UPDATE_ROUTE",
        Map.of(
            "routeIds", Map.of(SLUG, routeId(datasetId)),
            "serviceId", datasetId,
            "openDataAccess", openDataAccess));
  }

  private SagaCommandMessage createRouteCommand(String datasetId, boolean openDataAccess) {
    return new SagaCommandMessage(
        "EXECUTE_STEP",
        "msg-" + datasetId,
        "saga-it-" + datasetId,
        "create-route",
        "apisix",
        "CREATE_ROUTE",
        Map.of(
            "datasetId",
            datasetId,
            "upstreamUrl",
            "http://stub-upstream:5678/FROST-Server/v1.1/Projects(1)",
            "openDataAccess",
            openDataAccess,
            "namedApis",
            List.of(Map.of("slug", SLUG, "standard", "STA"))));
  }

  private static List<String> extractUris(JsonNode routeValue) {
    List<String> uris = new ArrayList<>();
    JsonNode urisNode = routeValue.get("uris");
    if (urisNode != null && urisNode.isArray()) {
      urisNode.forEach(u -> uris.add(u.asText()));
      return uris;
    }
    JsonNode uriNode = routeValue.get("uri");
    if (uriNode != null) {
      uris.add(uriNode.asText());
    }
    return uris;
  }
}

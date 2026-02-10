/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.apisix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.Topics;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests for ApisixAdapter response-rewrite plugin. */
class ApisixResponseRewriteIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createRouteWithResponseRewriteSetHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-set");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-set/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "headers",
                Map.of(
                    "set",
                    Map.of(
                        "X-Server-Id", "server-1",
                        "X-Environment", "production")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteRemoveHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-remove");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-remove/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of("headers", Map.of("remove", List.of("X-Internal-Header", "X-Debug-Info")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteStatusCode() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-status");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-status/*");
    routeConfig.put("methods", List.of("POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("response-rewrite", Map.of("status_code", 201)));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteBody() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-body");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-body/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of("response-rewrite", Map.of("body", "{\"status\":\"ok\",\"processed\":true}")));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteBodyBase64() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-base64");

    // Base64 of: {"result":"encoded"}
    String base64Body = "eyJyZXN1bHQiOiJlbmNvZGVkIn0=";
    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-base64/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins", Map.of("response-rewrite", Map.of("body", base64Body, "body_base64", true)));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteFilters() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-filters");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-filters/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "filters",
                List.of(
                    Map.of("regex", "old_text", "replace", "new_text"),
                    Map.of("regex", "internal_value", "replace", "public_value")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithResponseRewriteVars() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-vars");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-vars/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "response-rewrite",
            Map.of(
                "headers",
                Map.of(
                    "set",
                    Map.of(
                        "X-Upstream-Status", "$upstream_status",
                        "X-Request-Id", "$request_id")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void updateRouteAddResponseRewrite() throws Exception {
    String routeId = "test-route-add-response-rewrite";
    String upstreamId = createDefaultUpstream("for-add-response-rewrite");

    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-response-rewrite/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-response-rewrite/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "response-rewrite", Map.of("headers", Map.of("set", Map.of("X-Added-Via", "UPDATE")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds(
            "routes/" + routeId, Operation.UPDATE, updatedRouteConfig);

    adapter.processConfigEvent(Topics.ROUTE_UPDATED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              JsonNode route = getRouteFromApisix(routeId);
              assertNotNull(route);
              JsonNode plugins = route.get("value").get("plugins");
              assertNotNull(plugins);
              assertTrue(plugins.has("response-rewrite"));
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithResponseRewriteFullConfig() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-full");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/response-full/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> responseRewriteConfig = new HashMap<>();
    responseRewriteConfig.put("status_code", 200);
    responseRewriteConfig.put(
        "headers",
        Map.of(
            "set", Map.of("X-Processed", "true", "Content-Type", "application/json"),
            "remove", List.of("X-Internal", "X-Debug")));
    responseRewriteConfig.put("body", "{\"result\":\"success\",\"processed\":true}");
    routeConfig.put("plugins", Map.of("response-rewrite", responseRewriteConfig));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithInvalidResponseRewriteConfig() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-response-rewrite");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-response-rewrite/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    // status_code must be between 200-598, using 999 should be invalid
    routeConfig.put("plugins", Map.of("response-rewrite", Map.of("status_code", 999)));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }
}

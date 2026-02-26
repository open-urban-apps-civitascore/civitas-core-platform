/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.apisix.RouteConfigValue;
import de.civitascore.configadapter.model.apisix.plugins.ResponseFilter;
import de.civitascore.configadapter.model.apisix.plugins.ResponseRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests for ApisixAdapter response-rewrite plugin. */
class ApisixResponseRewriteIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createRouteWithResponseRewriteSetHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-set");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Server-Id", "server-1", "X-Environment", "production"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-set/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-set/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteRemoveHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-remove");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setRemove(List.of("X-Internal-Header", "X-Debug-Info"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-remove/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-remove/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteStatusCode() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-status");

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setStatusCode(201);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-status/*");
    routeConfig.setMethods(List.of("POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-status/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteBody() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-body");

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setBody("{\"status\":\"ok\",\"processed\":true}");

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-body/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-body/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteBodyBase64() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-base64");

    // Base64 of: {"result":"encoded"}
    String base64Body = "eyJyZXN1bHQiOiJlbmNvZGVkIn0=";

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setBody(base64Body);
    responseRewrite.setBodyBase64(true);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-base64/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-base64/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteFilters() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-filters");

    ResponseFilter filter1 = new ResponseFilter();
    filter1.setRegex("old_text");
    filter1.setReplace("new_text");

    ResponseFilter filter2 = new ResponseFilter();
    filter2.setRegex("internal_value");
    filter2.setReplace("public_value");

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setFilters(List.of(filter1, filter2));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-filters/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-filters/*", "response-rewrite");
  }

  @Test
  void createRouteWithResponseRewriteVars() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-vars");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Upstream-Status", "$upstream_status", "X-Request-Id", "$request_id"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-vars/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-vars/*", "response-rewrite");
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

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Added-Via", "UPDATE"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue updatedRouteConfig = new RouteConfigValue();
    updatedRouteConfig.setUri("/api/v1/add-response-rewrite/*");
    updatedRouteConfig.setMethods(List.of("GET", "POST"));
    updatedRouteConfig.setUpstreamId(upstreamId);
    updatedRouteConfig.setPlugins(plugins);

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
              JsonNode responseRewriteNode =
                  route.get("value").get("plugins").get("response-rewrite");
              assertNotNull(responseRewriteNode, "response-rewrite plugin should exist");
              JsonNode headersSet = responseRewriteNode.path("headers").path("set");
              assertTrue(headersSet.has("X-Added-Via"), "headers.set should contain X-Added-Via");
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithResponseRewriteFullConfig() throws Exception {
    String upstreamId = createDefaultUpstream("response-rewrite-full");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Processed", "true", "Content-Type", "application/json"));
    headers.setRemove(List.of("X-Internal", "X-Debug"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setStatusCode(200);
    responseRewrite.setHeaders(headers);
    responseRewrite.setBody("{\"result\":\"success\",\"processed\":true}");

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-full/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/response-full/*", "response-rewrite");
  }

  @Test
  void createRouteWithInvalidResponseRewriteConfig() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-response-rewrite");

    // status_code must be between 200-598, using 999 should be invalid
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setStatusCode(999);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setResponseRewrite(responseRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/invalid-response-rewrite/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }
}

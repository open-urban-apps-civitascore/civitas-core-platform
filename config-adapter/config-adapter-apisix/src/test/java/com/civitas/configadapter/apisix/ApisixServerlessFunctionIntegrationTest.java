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

/**
 * Integration tests for ApisixAdapter serverless-pre-function and serverless-post-function plugins.
 */
class ApisixServerlessFunctionIntegrationTest extends AbstractApisixIntegrationTest {

  // ============== SERVERLESS-POST-FUNCTION ==============

  @Test
  void createRouteWithServerlessPostFunction() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-post");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-post/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Post-function executed') end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/serverless-post/*", "serverless-post-function");
  }

  @Test
  void createRouteWithServerlessPostFunctionHeaderFilter() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-header-filter");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/header-filter/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "header_filter",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.header['X-Custom-Post-Header'] = 'processed' end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/header-filter/*", "serverless-post-function");
  }

  @Test
  void updateRouteAddServerlessPostFunction() throws Exception {
    String routeId = "test-route-add-serverless-post";
    String upstreamId = createDefaultUpstream("for-add-serverless");

    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-serverless/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-serverless/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of("return function(conf, ctx) ngx.log(ngx.INFO, 'Added via UPDATE') end"))));

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
              JsonNode serverlessPostFunction =
                  route.get("value").get("plugins").get("serverless-post-function");
              assertNotNull(serverlessPostFunction, "serverless-post-function plugin should exist");
              assertEquals(
                  "log", serverlessPostFunction.get("phase").asText(), "phase should be log");
              assertTrue(
                  serverlessPostFunction.get("functions").isArray(),
                  "functions should be an array");
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithMultipleServerlessFunctions() throws Exception {
    String upstreamId = createDefaultUpstream("multi-serverless");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/multi-serverless/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of(
                "phase",
                "log",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 1') end",
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 2') end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/multi-serverless/*", "serverless-post-function");
  }

  @Test
  void createRouteWithServerlessPostFunctionAndOtherPlugins() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-combo");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-combo/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put("prometheus", Map.of());
    plugins.put(
        "response-rewrite", Map.of("headers", Map.of("set", Map.of("X-Processed-By", "apisix"))));
    plugins.put(
        "serverless-post-function",
        Map.of(
            "phase",
            "log",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.log(ngx.INFO, 'Request processed: ' .. ngx.var.uri) end")));
    routeConfig.put("plugins", plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix(
        "/api/v1/serverless-combo/*", "serverless-post-function", "prometheus", "response-rewrite");
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPostFunction() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-lua-post");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-lua-post/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-post-function",
            Map.of("phase", "log", "functions", List.of("this is not valid lua syntax !!!"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }

  // ============== SERVERLESS-PRE-FUNCTION ==============

  @Test
  void createRouteWithServerlessPreFunction() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-pre");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/serverless-pre/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Request-ID', ngx.var.request_id) end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/serverless-pre/*", "serverless-pre-function");
  }

  @Test
  void createRouteWithServerlessPreFunctionAccessPhase() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-pre-access");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/pre-access/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "access",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.log(ngx.INFO, 'Pre-access check passed') end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/pre-access/*", "serverless-pre-function");
  }

  @Test
  void updateRouteAddServerlessPreFunction() throws Exception {
    String routeId = "test-route-add-serverless-pre";
    String upstreamId = createDefaultUpstream("for-add-serverless-pre");

    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/add-serverless-pre/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/add-serverless-pre/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Added-Via', 'UPDATE') end"))));

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
              JsonNode serverlessPreFunction =
                  route.get("value").get("plugins").get("serverless-pre-function");
              assertNotNull(serverlessPreFunction, "serverless-pre-function plugin should exist");
              assertEquals(
                  "rewrite",
                  serverlessPreFunction.get("phase").asText(),
                  "phase should be rewrite");
              assertTrue(
                  serverlessPreFunction.get("functions").isArray(), "functions should be an array");
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithMultipleServerlessPreFunctions() throws Exception {
    String upstreamId = createDefaultUpstream("multi-serverless-pre");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/multi-serverless-pre/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of(
                "phase",
                "rewrite",
                "functions",
                List.of(
                    "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-1', 'value1') end",
                    "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-2', 'value2') end"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/multi-serverless-pre/*", "serverless-pre-function");
  }

  @Test
  void createRouteWithServerlessPreAndPostFunctions() throws Exception {
    String upstreamId = createDefaultUpstream("pre-and-post");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/pre-and-post/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put(
        "serverless-pre-function",
        Map.of(
            "phase",
            "rewrite",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.req.set_header('X-Request-Start', ngx.now()) end")));
    plugins.put(
        "serverless-post-function",
        Map.of(
            "phase",
            "log",
            "functions",
            List.of(
                "return function(conf, ctx) ngx.log(ngx.INFO, 'Request completed: ' .. ngx.var.uri) end")));
    routeConfig.put("plugins", plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix(
        "/api/v1/pre-and-post/*", "serverless-pre-function", "serverless-post-function");
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPreFunction() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-lua-pre");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-lua-pre/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "serverless-pre-function",
            Map.of("phase", "rewrite", "functions", List.of("this is not valid lua syntax !!!"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }
}

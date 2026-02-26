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
import de.civitascore.configadapter.model.apisix.plugins.ResponseRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import de.civitascore.configadapter.model.apisix.plugins.ServerlessFunctionPlugin;
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

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/serverless-post/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("log");
    sfp.setFunctions(
        List.of("return function(conf, ctx) ngx.log(ngx.INFO, 'Post-function executed') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/serverless-post/*", "serverless-post-function");
  }

  @Test
  void createRouteWithServerlessPostFunctionHeaderFilter() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-header-filter");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/header-filter/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("header_filter");
    sfp.setFunctions(
        List.of("return function(conf, ctx) ngx.header['X-Custom-Post-Header'] = 'processed' end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
    routeConfig.setPlugins(plugins);

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

    RouteConfigValue updatedRouteConfig = new RouteConfigValue();
    updatedRouteConfig.setUri("/api/v1/add-serverless/*");
    updatedRouteConfig.setMethods(List.of("GET", "POST"));
    updatedRouteConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("log");
    sfp.setFunctions(
        List.of("return function(conf, ctx) ngx.log(ngx.INFO, 'Added via UPDATE') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
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

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/multi-serverless/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("log");
    sfp.setFunctions(
        List.of(
            "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 1') end",
            "return function(conf, ctx) ngx.log(ngx.INFO, 'Function 2') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/multi-serverless/*", "serverless-post-function");
  }

  @Test
  void createRouteWithServerlessPostFunctionAndOtherPlugins() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-combo");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/serverless-combo/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("log");
    sfp.setFunctions(
        List.of(
            "return function(conf, ctx) ngx.log(ngx.INFO, 'Request processed: ' .. ngx.var.uri) end"));

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Processed-By", "apisix"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
    plugins.setResponseRewrite(responseRewrite);
    plugins.handleUnknownPlugin("prometheus", Map.of());
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix(
        "/api/v1/serverless-combo/*", "serverless-post-function", "prometheus", "response-rewrite");
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPostFunction() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-lua-post");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/invalid-lua-post/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("log");
    sfp.setFunctions(List.of("this is not valid lua syntax !!!"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPostFunction(sfp);
    routeConfig.setPlugins(plugins);

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

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/serverless-pre/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("rewrite");
    sfp.setFunctions(
        List.of(
            "return function(conf, ctx) ngx.req.set_header('X-Request-ID', ngx.var.request_id) end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(sfp);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/serverless-pre/*", "serverless-pre-function");
  }

  @Test
  void createRouteWithServerlessPreFunctionAccessPhase() throws Exception {
    String upstreamId = createDefaultUpstream("serverless-pre-access");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/pre-access/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("access");
    sfp.setFunctions(
        List.of("return function(conf, ctx) ngx.log(ngx.INFO, 'Pre-access check passed') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(sfp);
    routeConfig.setPlugins(plugins);

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

    RouteConfigValue updatedRouteConfig = new RouteConfigValue();
    updatedRouteConfig.setUri("/api/v1/add-serverless-pre/*");
    updatedRouteConfig.setMethods(List.of("GET", "POST"));
    updatedRouteConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("rewrite");
    sfp.setFunctions(
        List.of("return function(conf, ctx) ngx.req.set_header('X-Added-Via', 'UPDATE') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(sfp);
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

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/multi-serverless-pre/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("rewrite");
    sfp.setFunctions(
        List.of(
            "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-1', 'value1') end",
            "return function(conf, ctx) ngx.req.set_header('X-Pre-Func-2', 'value2') end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(sfp);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/multi-serverless-pre/*", "serverless-pre-function");
  }

  @Test
  void createRouteWithServerlessPreAndPostFunctions() throws Exception {
    String upstreamId = createDefaultUpstream("pre-and-post");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/pre-and-post/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin preFunction = new ServerlessFunctionPlugin();
    preFunction.setPhase("rewrite");
    preFunction.setFunctions(
        List.of("return function(conf, ctx) ngx.req.set_header('X-Request-Start', ngx.now()) end"));

    ServerlessFunctionPlugin postFunction = new ServerlessFunctionPlugin();
    postFunction.setPhase("log");
    postFunction.setFunctions(
        List.of(
            "return function(conf, ctx) ngx.log(ngx.INFO, 'Request completed: ' .. ngx.var.uri) end"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(preFunction);
    plugins.setServerlessPostFunction(postFunction);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix(
        "/api/v1/pre-and-post/*", "serverless-pre-function", "serverless-post-function");
  }

  @Test
  void createRouteWithInvalidLuaInServerlessPreFunction() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-lua-pre");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/invalid-lua-pre/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);

    ServerlessFunctionPlugin sfp = new ServerlessFunctionPlugin();
    sfp.setPhase("rewrite");
    sfp.setFunctions(List.of("this is not valid lua syntax !!!"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setServerlessPreFunction(sfp);
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

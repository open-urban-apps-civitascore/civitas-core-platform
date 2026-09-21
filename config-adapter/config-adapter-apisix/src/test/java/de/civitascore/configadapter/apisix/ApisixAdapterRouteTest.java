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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.apisix.ApisixConfigValue;
import de.civitascore.configadapter.model.apisix.RouteConfigValue;
import de.civitascore.configadapter.model.apisix.plugins.ProxyRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.ResponseRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import de.civitascore.configadapter.model.apisix.plugins.ServerlessFunctionPlugin;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter route operations. */
class ApisixAdapterRouteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId("backend-users");
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    plugins.handleUnknownPlugin("openid-connect", Map.of("client_id", "api-gateway"));
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }

  @Test
  void testRouteCreateFailure() {
    givenMockPostReturns(400, "{\"error\":\"Invalid route config\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/invalid");
    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.route.created", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 400"));
  }

  @Test
  void testRouteUpdateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId("backend-users-updated");
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/users");
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }

  @Test
  void testRouteUpdateFailure() {
    givenMockPutReturns(404, "{\"error\":\"Route not found\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/nonexistent-route", routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.route.updated", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 404"));
  }

  @Test
  void testRouteDeleteSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockDeleteReturns(200, "{\"deleted\":\"routes/test-route-id\"}");

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(
            Operation.DELETE, "routes/test-route-id", (RouteConfigValue) null);

    adapter.processConfigEvent("de.civitascore.api.route.deleted", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route deleted successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }

  @Test
  void deleteRoute_whenRouteNotFound_shouldPublishSuccess()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockDeleteReturns(404, "{\"error\":\"Route not found\"}");

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(
            Operation.DELETE, "routes/nonexistent-route", (RouteConfigValue) null);

    adapter.processConfigEvent("de.civitascore.api.route.deleted", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("nonexistent-route", result.resourceId());
  }

  @Test
  void deleteRoute_whenForbidden_shouldThrowFatalException() {
    givenMockDeleteReturns(403, "{\"error\":\"Forbidden\"}");

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(
            Operation.DELETE, "routes/forbidden-route", (RouteConfigValue) null);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.route.deleted", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 403"));
  }

  @Test
  void testRouteWithFullPluginConfiguration()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Create a route with all CIVITAS/CORE V1 plugins
    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/data/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId("data-service");

    RoutePlugins plugins = new RoutePlugins();

    // Untyped plugins
    plugins.handleUnknownPlugin(
        "openid-connect",
        Map.of(
            "discovery", "https://keycloak.example.com/.well-known/openid-configuration",
            "client_id", "api-gateway",
            "client_secret", "secret"));
    plugins.handleUnknownPlugin("prometheus", Map.of());
    plugins.handleUnknownPlugin("loki", Map.of("endpoint", "http://loki:3100"));

    // Typed plugins
    ServerlessFunctionPlugin preFunction = new ServerlessFunctionPlugin();
    preFunction.setPhase("rewrite");
    preFunction.setFunctions(List.of("return function() end"));
    plugins.setServerlessPreFunction(preFunction);

    ServerlessFunctionPlugin postFunction = new ServerlessFunctionPlugin();
    postFunction.setPhase("log");
    postFunction.setFunctions(List.of("return function() end"));
    plugins.setServerlessPostFunction(postFunction);

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    RewriteHeaders responseHeaders = new RewriteHeaders();
    responseHeaders.setSet(Map.of("X-Custom-Header", "value"));
    responseRewrite.setHeaders(responseHeaders);
    plugins.setResponseRewrite(responseRewrite);

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("/api/v1/(.*)", "/$1"));
    plugins.setProxyRewrite(proxyRewrite);

    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithConnectionException() throws IOException {
    // Torn down before the request is even made: the connection attempt itself fails.
    server.close();

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/test");
    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class,
            () -> adapter.processConfigEvent("de.civitascore.api.route.created", event));

    assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    assertTrue(exception.isRetryable());
  }

  @Test
  void testRouteWithApisixConfigValue() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Test that routes also work with ApisixConfigValue (backward compatibility)
    ApisixConfigValue routeConfig = new ApisixConfigValue();
    routeConfig.setType("roundrobin");

    ConfigEvent event = ApisixTestFixtures.upstreamEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }
}

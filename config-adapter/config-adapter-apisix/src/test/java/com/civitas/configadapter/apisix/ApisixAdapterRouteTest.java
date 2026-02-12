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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Entity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter route operations. */
class ApisixAdapterRouteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/users/*",
            "methods",
            List.of("GET", "POST"),
            "upstream_id",
            "backend-users",
            "plugins",
            Map.of("prometheus", Map.of(), "openid-connect", Map.of("client_id", "api-gateway")));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }

  @Test
  void testRouteCreateFailure() {
    givenMockPostReturns(400, "{\"error\":\"Invalid route config\"}");

    Map<String, Object> routeConfig = Map.of("uri", "/api/v1/invalid");
    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("core.civitas.api.route.created", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 400"));
  }

  @Test
  void testRouteUpdateSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/users/*",
            "methods",
            List.of("GET", "POST", "PUT", "DELETE"),
            "upstream_id",
            "backend-users-updated",
            "plugins",
            Map.of("prometheus", Map.of(), "proxy-rewrite", Map.of("uri", "/users")));

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }

  @Test
  void testRouteUpdateFailure() {
    givenMockPutReturns(404, "{\"error\":\"Route not found\"}");

    Map<String, Object> routeConfig = Map.of("uri", "/api/v1/users/*");
    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/nonexistent-route", routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("core.civitas.api.route.updated", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 404"));
  }

  @Test
  void testRouteDeleteSuccess() throws FatalAdapterException, RetryableAdapterException {
    givenMockDeleteReturns(200, "{\"deleted\":\"routes/test-route-id\"}");

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.DELETE, "routes/test-route-id", null);

    adapter.processConfigEvent("core.civitas.api.route.deleted", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route deleted successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }

  @Test
  void testRouteDeleteFailure() {
    givenMockDeleteReturns(404, "{\"error\":\"Route not found\"}");

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.DELETE, "routes/nonexistent-route", null);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent("core.civitas.api.route.deleted", event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
    assertTrue(exception.getMessage().contains("HTTP 404"));
  }

  @Test
  void testRouteWithFullPluginConfiguration()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Create a route with all CIVITAS/CORE V1 plugins
    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/data/*",
            "methods",
            List.of("GET", "POST", "PUT", "DELETE"),
            "upstream_id",
            "data-service",
            "plugins",
            Map.of(
                "openid-connect",
                Map.of(
                    "discovery", "https://keycloak.example.com/.well-known/openid-configuration",
                    "client_id", "api-gateway",
                    "client_secret", "secret"),
                "serverless-pre-function",
                Map.of("phase", "rewrite", "functions", List.of("return function() end")),
                "serverless-post-function",
                Map.of("phase", "log", "functions", List.of("return function() end")),
                "response-rewrite",
                Map.of("headers", Map.of("X-Custom-Header", "value")),
                "proxy-rewrite",
                Map.of("regex_uri", List.of("/api/v1/(.*)", "/$1")),
                "prometheus",
                Map.of(),
                "loki",
                Map.of("endpoint", "http://loki:3100")));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithConnectionException() {
    when(mockBuilder.post(any(Entity.class)))
        .thenThrow(new ProcessingException("Connection refused"));

    Map<String, Object> routeConfig = Map.of("uri", "/api/v1/test");
    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    RetryableAdapterException exception =
        assertThrows(
            RetryableAdapterException.class,
            () -> adapter.processConfigEvent("core.civitas.api.route.created", event));

    assertEquals(AdapterErrorCode.NETWORK_ERROR, exception.getErrorCode());
    assertTrue(exception.isRetryable());
  }

  @Test
  void testRouteWithApisixConfigValue() throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Test that routes also work with ApisixConfigValue (backward compatibility)
    Map<String, Object> routeConfig =
        Map.of("uri", "/api/v1/legacy/*", "upstream_id", "legacy-backend");

    ConfigEvent event = ApisixTestFixtures.upstreamEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }
}

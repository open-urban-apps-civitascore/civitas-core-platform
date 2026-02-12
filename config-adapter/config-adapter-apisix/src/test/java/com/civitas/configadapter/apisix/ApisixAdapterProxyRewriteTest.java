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

import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter route operations with the proxy-rewrite plugin. */
class ApisixAdapterProxyRewriteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateWithProxyRewriteUri()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/users/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of("proxy-rewrite", Map.of("uri", "/users")));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }

  @Test
  void testRouteCreateWithProxyRewriteRegexUri()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Path stripping: /api/v1/users -> /users
    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/*",
            "methods",
            List.of("GET", "POST", "PUT", "DELETE"),
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of("proxy-rewrite", Map.of("regex_uri", List.of("^/api/v1/(.*)", "/$1"))));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithProxyRewriteHost()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/external/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of("proxy-rewrite", Map.of("host", "internal-backend.local")));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithProxyRewriteHeaders()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/data/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "proxy-rewrite",
                Map.of(
                    "headers",
                    Map.of(
                        "set", Map.of("X-Forwarded-Prefix", "/api/v1/data"),
                        "add", Map.of("X-Request-Source", "gateway"),
                        "remove", List.of("X-Internal-Token")))));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteUpdateAddProxyRewrite() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/users/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of("prometheus", Map.of(), "proxy-rewrite", Map.of("uri", "/users")));

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }

  @Test
  void testRouteUpdateModifyProxyRewrite() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v2/*",
            "upstream_id",
            "backend-service-v2",
            "plugins",
            Map.of(
                "proxy-rewrite",
                Map.of(
                    "regex_uri",
                    List.of("^/api/v2/(.*)", "/$1"),
                    "headers",
                    Map.of("set", Map.of("X-API-Version", "v2")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
  }

  @Test
  void testRouteCreateWithProxyRewriteFullConfig()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    // Full proxy-rewrite configuration with all options
    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/data/*",
            "methods",
            List.of("GET", "POST", "PUT", "DELETE"),
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "proxy-rewrite",
                Map.of(
                    "regex_uri",
                    List.of("^/api/v1/data/(.*)", "/$1"),
                    "host",
                    "internal-backend.local",
                    "headers",
                    Map.of(
                        "set",
                        Map.of("X-Forwarded-Prefix", "/api/v1/data", "X-Real-IP", "$remote_addr"),
                        "add",
                        Map.of("X-Request-ID", "$request_id"),
                        "remove",
                        List.of("X-Internal-Token", "X-Debug-Mode")))));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }
}

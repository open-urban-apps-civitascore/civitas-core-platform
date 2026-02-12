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

/** Unit tests for ApisixAdapter route operations with the response-rewrite plugin. */
class ApisixAdapterResponseRewriteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateWithResponseRewriteHeaders()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/response-headers/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "response-rewrite",
                Map.of(
                    "headers",
                    Map.of(
                        "set", Map.of("X-Server-Id", "server-1"),
                        "add", Map.of("X-Custom", "value"),
                        "remove", List.of("X-Internal")))));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }

  @Test
  void testRouteCreateWithResponseRewriteStatusCode()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/response-status/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of("response-rewrite", Map.of("status_code", 201)));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithResponseRewriteBody()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/response-body/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "response-rewrite", Map.of("body", "{\"status\":\"ok\"}", "body_base64", false)));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithResponseRewriteFilters()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/response-filters/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "response-rewrite",
                Map.of("filters", List.of(Map.of("regex", "old_text", "replace", "new_text")))));

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteUpdateWithResponseRewrite()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    Map<String, Object> routeConfig =
        Map.of(
            "uri",
            "/api/v1/updated/*",
            "upstream_id",
            "backend-service",
            "plugins",
            Map.of(
                "response-rewrite",
                Map.of(
                    "status_code",
                    200,
                    "headers",
                    Map.of("set", Map.of("X-Updated", "true")),
                    "body",
                    "{\"updated\":true}")));

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("core.civitas.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }
}

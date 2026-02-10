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

/** Integration tests for ApisixAdapter route operations. */
class ApisixRouteIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createRoute() throws Exception {
    String upstreamId = createDefaultUpstream("for-route");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/test/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("prometheus", Map.of()));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void updateRoute() throws Exception {
    String routeId = "test-route-update";
    String upstreamId = createDefaultUpstream("for-route-update");

    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/initial/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/updated/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins", Map.of("prometheus", Map.of(), "proxy-rewrite", Map.of("uri", "/updated")));

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
              String uri = route.get("value").get("uri").asText();
              assertEquals("/api/v1/updated/*", uri);
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void deleteRoute() throws Exception {
    String routeId = "test-route-delete";
    String upstreamId = createDefaultUpstream("for-route-delete");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/to-delete/*");
    routeConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, routeConfig);

    JsonNode route = getRouteFromApisix(routeId);
    assertNotNull(route, "Route should exist before delete");

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes/" + routeId, Operation.DELETE, null);

    adapter.processConfigEvent(Topics.ROUTE_DELETED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              try {
                getRouteFromApisix(routeId);
              } catch (Exception e) {
                assertTrue(e.getMessage().contains("404") || e.getMessage().contains("not found"));
              }
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithAllPlugins() throws Exception {
    String upstreamId = createDefaultUpstream("for-full-route");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/full-plugins/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put("prometheus", Map.of());
    plugins.put("proxy-rewrite", Map.of("uri", "/rewritten"));
    plugins.put(
        "response-rewrite",
        Map.of("headers", Map.of("set", Map.of("X-Custom-Header", "custom-value"))));
    routeConfig.put("plugins", plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void handleInvalidRouteConfiguration() {
    Map<String, Object> invalidConfig = Map.of("methods", List.of("GET"));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, invalidConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }
}

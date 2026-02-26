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
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.Topics;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.apisix.RouteConfigValue;
import de.civitascore.configadapter.model.apisix.plugins.ProxyRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.ResponseRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests for ApisixAdapter route operations. */
class ApisixRouteIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createRoute() throws Exception {
    String upstreamId = createDefaultUpstream("for-route");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/test/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/test/*", "prometheus");
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

    RouteConfigValue updatedRouteConfig = new RouteConfigValue();
    updatedRouteConfig.setUri("/api/v1/updated/*");
    updatedRouteConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    updatedRouteConfig.setUpstreamId(upstreamId);
    RoutePlugins updatedPlugins = new RoutePlugins();
    updatedPlugins.handleUnknownPlugin("prometheus", Map.of());
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/updated");
    updatedPlugins.setProxyRewrite(proxyRewrite);
    updatedRouteConfig.setPlugins(updatedPlugins);

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
        ApisixTestFixtures.routeEventRandomIds(
            "routes/" + routeId, Operation.DELETE, (RouteConfigValue) null);

    adapter.processConfigEvent(Topics.ROUTE_DELETED.toString(), event);

    await()
        .atMost(10, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              try {
                getRouteFromApisix(routeId);
                fail("Route should have been deleted but still exists");
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

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/full-plugins/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId(upstreamId);

    RoutePlugins plugins = new RoutePlugins();
    plugins.handleUnknownPlugin("prometheus", Map.of());
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/rewritten");
    plugins.setProxyRewrite(proxyRewrite);
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom-Header", "custom-value"));
    responseRewrite.setHeaders(headers);
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/full-plugins/*", "prometheus", "proxy-rewrite", "response-rewrite");
  }

  @Test
  void handleInvalidRouteConfiguration() {
    RouteConfigValue invalidConfig = new RouteConfigValue();
    invalidConfig.setMethods(List.of("GET"));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, invalidConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertEquals(AdapterErrorCode.APISIX_ROUTE_ERROR, exception.getErrorCode());
  }
}

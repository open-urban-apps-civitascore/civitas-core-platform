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
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Operation;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Integration tests for ApisixAdapter proxy-rewrite plugin. */
class ApisixProxyRewriteIntegrationTest extends AbstractApisixIntegrationTest {

  @Test
  void createRouteWithProxyRewriteStaticUri() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-uri");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/users/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("proxy-rewrite", Map.of("uri", "/users")));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithProxyRewriteRegexUri() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-regex");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins", Map.of("proxy-rewrite", Map.of("regex_uri", List.of("^/api/v1/(.*)", "/$1"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithProxyRewritePathStripping() throws Exception {
    // CRITICAL TEST: Path stripping scenario
    // Incoming: /api/v1/users -> Upstream: /users
    String upstreamId = createDefaultUpstream("path-stripping");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins", Map.of("proxy-rewrite", Map.of("regex_uri", List.of("^/api/v1/(.*)", "/$1"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithProxyRewriteHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-headers");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/data/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put(
        "plugins",
        Map.of(
            "proxy-rewrite",
            Map.of(
                "headers",
                Map.of(
                    "set", Map.of("X-Forwarded-Prefix", "/api/v1/data"),
                    "add", Map.of("X-Request-Source", "gateway"),
                    "remove", List.of("X-Internal-Token")))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void updateRouteProxyRewriteConfig() throws Exception {
    String routeId = "test-route-update-proxy-rewrite";
    String upstreamId = createDefaultUpstream("for-update-proxy-rewrite");

    Map<String, Object> initialRouteConfig = new HashMap<>();
    initialRouteConfig.put("uri", "/api/v1/update-proxy/*");
    initialRouteConfig.put("methods", List.of("GET"));
    initialRouteConfig.put("upstream_id", upstreamId);
    createRouteDirectly(routeId, initialRouteConfig);

    Map<String, Object> updatedRouteConfig = new HashMap<>();
    updatedRouteConfig.put("uri", "/api/v1/update-proxy/*");
    updatedRouteConfig.put("methods", List.of("GET", "POST", "PUT"));
    updatedRouteConfig.put("upstream_id", upstreamId);
    updatedRouteConfig.put(
        "plugins",
        Map.of(
            "proxy-rewrite",
            Map.of(
                "regex_uri",
                List.of("^/api/v1/update-proxy/(.*)", "/$1"),
                "headers",
                Map.of("set", Map.of("X-Updated-Via", "proxy-rewrite")))));

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
              assertTrue(plugins.has("proxy-rewrite"));
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithProxyRewriteHost() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-host");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/external/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);
    routeConfig.put("plugins", Map.of("proxy-rewrite", Map.of("host", "internal-backend.local")));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithProxyRewriteFullConfig() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-full");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/full-proxy/*");
    routeConfig.put("methods", List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> proxyRewriteConfig = new HashMap<>();
    proxyRewriteConfig.put("regex_uri", List.of("^/api/v1/full-proxy/(.*)", "/$1"));
    proxyRewriteConfig.put("host", "internal-backend.local");
    proxyRewriteConfig.put(
        "headers",
        Map.of(
            "set", Map.of("X-Forwarded-Prefix", "/api/v1/full-proxy", "X-Real-IP", "$remote_addr"),
            "add", Map.of("X-Request-ID", "$request_id"),
            "remove", List.of("X-Internal-Token", "X-Debug-Mode")));
    routeConfig.put("plugins", Map.of("proxy-rewrite", proxyRewriteConfig));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithProxyRewriteAndOtherPlugins() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-combo");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/combo/*");
    routeConfig.put("methods", List.of("GET", "POST"));
    routeConfig.put("upstream_id", upstreamId);

    Map<String, Object> plugins = new HashMap<>();
    plugins.put("prometheus", Map.of());
    plugins.put(
        "proxy-rewrite",
        Map.of(
            "regex_uri",
            List.of("^/api/v1/combo/(.*)", "/$1"),
            "headers",
            Map.of("set", Map.of("X-Forwarded-Prefix", "/api/v1/combo"))));
    plugins.put(
        "response-rewrite", Map.of("headers", Map.of("set", Map.of("X-Processed", "true"))));
    routeConfig.put("plugins", plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitSuccessResult();
  }

  @Test
  void createRouteWithInvalidProxyRewrite() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-proxy-rewrite");

    Map<String, Object> routeConfig = new HashMap<>();
    routeConfig.put("uri", "/api/v1/invalid-proxy/*");
    routeConfig.put("methods", List.of("GET"));
    routeConfig.put("upstream_id", upstreamId);
    // regex_uri with only one element (should have two: pattern and replacement)
    routeConfig.put("plugins", Map.of("proxy-rewrite", Map.of("regex_uri", List.of("^/api/v1/"))));

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertNotNull(exception.getErrorCode());
  }
}

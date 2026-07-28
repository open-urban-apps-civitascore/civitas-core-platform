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

/** Integration tests for ApisixAdapter proxy-rewrite plugin. */
class ApisixProxyRewriteIT extends AbstractApisixIT {

  @Test
  void createRouteWithProxyRewriteStaticUri() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-uri");

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/users");

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/users/*", "proxy-rewrite");
  }

  @Test
  void createRouteWithProxyRewriteRegexUri() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-regex");

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/(.*)", "/$1"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/*", "proxy-rewrite");
  }

  @Test
  void createRouteWithProxyRewritePathStripping() throws Exception {
    // CRITICAL TEST: Path stripping scenario
    // Incoming: /api/v1/users -> Upstream: /users
    String upstreamId = createDefaultUpstream("path-stripping");

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/(.*)", "/$1"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/*", "proxy-rewrite");
  }

  @Test
  void createRouteWithProxyRewriteHeaders() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-headers");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Forwarded-Prefix", "/api/v1/data"));
    headers.setAdd(Map.of("X-Request-Source", "gateway"));
    headers.setRemove(List.of("X-Internal-Token"));

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/data/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/data/*", "proxy-rewrite");
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

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Updated-Via", "proxy-rewrite"));

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/update-proxy/(.*)", "/$1"));
    proxyRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue updatedRouteConfig = new RouteConfigValue();
    updatedRouteConfig.setUri("/api/v1/update-proxy/*");
    updatedRouteConfig.setMethods(List.of("GET", "POST", "PUT"));
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
              JsonNode proxyRewriteNode = route.get("value").get("plugins").get("proxy-rewrite");
              assertNotNull(proxyRewriteNode, "proxy-rewrite plugin should exist");
              assertTrue(proxyRewriteNode.has("regex_uri"), "proxy-rewrite should have regex_uri");
              assertTrue(proxyRewriteNode.has("headers"), "proxy-rewrite should have headers");
            });

    assertEquals(1, eventPublisher.getPublishedEvents().size());
    ConfigResultEvent resultEvent = eventPublisher.getPublishedEvents().getFirst();
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultEvent.status());
  }

  @Test
  void createRouteWithProxyRewriteHost() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-host");

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setHost("internal-backend.local");

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/external/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/external/*", "proxy-rewrite");
  }

  @Test
  void createRouteWithProxyRewriteFullConfig() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-full");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Forwarded-Prefix", "/api/v1/full-proxy", "X-Real-IP", "$remote_addr"));
    headers.setAdd(Map.of("X-Request-ID", "$request_id"));
    headers.setRemove(List.of("X-Internal-Token", "X-Debug-Mode"));

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/full-proxy/(.*)", "/$1"));
    proxyRewrite.setHost("internal-backend.local");
    proxyRewrite.setHeaders(headers);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/full-proxy/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/full-proxy/*", "proxy-rewrite");
  }

  @Test
  void createRouteWithProxyRewriteAndOtherPlugins() throws Exception {
    String upstreamId = createDefaultUpstream("proxy-rewrite-combo");

    RewriteHeaders proxyHeaders = new RewriteHeaders();
    proxyHeaders.setSet(Map.of("X-Forwarded-Prefix", "/api/v1/combo"));

    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/combo/(.*)", "/$1"));
    proxyRewrite.setHeaders(proxyHeaders);

    RewriteHeaders responseHeaders = new RewriteHeaders();
    responseHeaders.setSet(Map.of("X-Processed", "true"));

    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setHeaders(responseHeaders);

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);
    plugins.setResponseRewrite(responseRewrite);
    plugins.handleUnknownPlugin("prometheus", Map.of());

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/combo/*");
    routeConfig.setMethods(List.of("GET", "POST"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event);

    awaitRouteInApisix("/api/v1/combo/*", "proxy-rewrite", "prometheus", "response-rewrite");
  }

  @Test
  void createRouteWithInvalidProxyRewrite() throws Exception {
    String upstreamId = createDefaultUpstream("invalid-proxy-rewrite");

    // regex_uri with only one element (should have two: pattern and replacement)
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/"));

    RoutePlugins plugins = new RoutePlugins();
    plugins.setProxyRewrite(proxyRewrite);

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/invalid-proxy/*");
    routeConfig.setMethods(List.of("GET"));
    routeConfig.setUpstreamId(upstreamId);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEventRandomIds("routes", Operation.CREATE, routeConfig);

    FatalAdapterException exception =
        assertThrows(
            FatalAdapterException.class,
            () -> adapter.processConfigEvent(Topics.ROUTE_CREATED.toString(), event));

    assertNotNull(exception.getErrorCode());
  }
}

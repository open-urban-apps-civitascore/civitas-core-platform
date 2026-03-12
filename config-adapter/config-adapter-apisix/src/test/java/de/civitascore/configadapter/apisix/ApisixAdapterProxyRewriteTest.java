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

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.apisix.RouteConfigValue;
import de.civitascore.configadapter.model.apisix.plugins.ProxyRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter route operations with the proxy-rewrite plugin. */
class ApisixAdapterProxyRewriteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateWithProxyRewriteUri()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setUri("/users");
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

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
    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/(.*)", "/$1"));
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithProxyRewriteHost()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/external/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setHost("internal-backend.local");
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithProxyRewriteHeaders()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/data/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Forwarded-Prefix", "/api/v1/data"));
    headers.setAdd(Map.of("X-Request-Source", "gateway"));
    headers.setRemove(List.of("X-Internal-Token"));
    proxyRewrite.setHeaders(headers);
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteUpdateAddProxyRewrite() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/users/*");
    routeConfig.setUpstreamId("backend-service");
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
  void testRouteUpdateModifyProxyRewrite() throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/*");
    routeConfig.setUpstreamId("backend-service-v1");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/(.*)", "/$1"));
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-API-Version", "v1"));
    proxyRewrite.setHeaders(headers);
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

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
    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/data/*");
    routeConfig.setMethods(List.of("GET", "POST", "PUT", "DELETE"));
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ProxyRewritePlugin proxyRewrite = new ProxyRewritePlugin();
    proxyRewrite.setRegexUri(List.of("^/api/v1/data/(.*)", "/$1"));
    proxyRewrite.setHost("internal-backend.local");
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Forwarded-Prefix", "/api/v1/data", "X-Real-IP", "$remote_addr"));
    headers.setAdd(Map.of("X-Request-ID", "$request_id"));
    headers.setRemove(List.of("X-Internal-Token", "X-Debug-Mode"));
    proxyRewrite.setHeaders(headers);
    plugins.setProxyRewrite(proxyRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }
}

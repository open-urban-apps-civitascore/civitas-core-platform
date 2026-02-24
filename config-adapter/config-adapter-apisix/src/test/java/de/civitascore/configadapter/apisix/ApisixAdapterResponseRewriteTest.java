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
import de.civitascore.configadapter.model.apisix.plugins.ResponseFilter;
import de.civitascore.configadapter.model.apisix.plugins.ResponseRewritePlugin;
import de.civitascore.configadapter.model.apisix.plugins.RewriteHeaders;
import de.civitascore.configadapter.model.apisix.plugins.RoutePlugins;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixAdapter route operations with the response-rewrite plugin. */
class ApisixAdapterResponseRewriteTest extends AbstractApisixAdapterTest {

  @Test
  void testRouteCreateWithResponseRewriteHeaders()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-headers/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Server-Id", "server-1"));
    headers.setAdd(Map.of("X-Custom", "value"));
    headers.setRemove(List.of("X-Internal"));
    responseRewrite.setHeaders(headers);
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route created successfully", result.message());
  }

  @Test
  void testRouteCreateWithResponseRewriteStatusCode()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-status/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setStatusCode(201);
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithResponseRewriteBody()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-body/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setBody("{\"status\":\"ok\"}");
    responseRewrite.setBodyBase64(false);
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteCreateWithResponseRewriteFilters()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPostReturns(201, "{\"key\":\"routes/1\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/response-filters/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("old_text");
    filter.setReplace("new_text");
    responseRewrite.setFilters(List.of(filter));
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event = ApisixTestFixtures.routeEvent(Operation.CREATE, "routes", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.created", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
  }

  @Test
  void testRouteUpdateWithResponseRewrite()
      throws FatalAdapterException, RetryableAdapterException {
    givenMockPutReturns(200, "{\"key\":\"routes/test-route-id\"}");

    RouteConfigValue routeConfig = new RouteConfigValue();
    routeConfig.setUri("/api/v1/updated/*");
    routeConfig.setUpstreamId("backend-service");
    RoutePlugins plugins = new RoutePlugins();
    ResponseRewritePlugin responseRewrite = new ResponseRewritePlugin();
    responseRewrite.setStatusCode(200);
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Updated", "true"));
    responseRewrite.setHeaders(headers);
    responseRewrite.setBody("{\"updated\":true}");
    plugins.setResponseRewrite(responseRewrite);
    routeConfig.setPlugins(plugins);

    ConfigEvent event =
        ApisixTestFixtures.routeEvent(Operation.UPDATE, "routes/test-route-id", routeConfig);

    adapter.processConfigEvent("de.civitascore.api.route.updated", event);

    ConfigResultEvent result = capturePublishedResult();
    assertEquals(ConfigResultEvent.Status.SUCCESS, result.status());
    assertEquals("APISIX route updated successfully", result.message());
    assertEquals("test-route-id", result.resourceId());
  }
}

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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PathRewrite — bringing an already-provisioned route onto the current shape")
class PathRewriteTest {

  private static final String OWS_UPSTREAM = "/geoserver/ws/ows";
  private static final String STA_UPSTREAM = "/FROST-Server/v1.1/Projects(1)";

  private static Map<String, Object> routeMatching(List<String> uris) {
    Map<String, Object> route = new HashMap<>();
    route.put("uris", uris);
    return route;
  }

  /** The single-pair rewrite a route provisioned before the pairs split carries. */
  private static Map<String, Object> singlePairRewrite(String routePath, String upstreamPath) {
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put("regex_uri", List.of("^" + routePath + "(/.*)?$", upstreamPath + "$1"));
    return proxyRewrite;
  }

  @Test
  @DisplayName("an OWS route stops matching sub-paths and its rewrite loses the back-reference")
  void correctsAnOwsRouteProvisionedEarlier() {
    String routePath = "/v1/datasets/ds-1/map";
    Map<String, Object> route = routeMatching(List.of(routePath, routePath + "/*"));
    Map<String, Object> proxyRewrite = singlePairRewrite(routePath, OWS_UPSTREAM);

    PathRewrite.normalize(route, proxyRewrite, RouteUpstreamKind.OWS);

    assertArrayEquals(new String[] {routePath, routePath + "/"}, (String[]) route.get("uris"));
    assertArrayEquals(
        new String[] {"^" + routePath + "/?$", OWS_UPSTREAM},
        (String[]) proxyRewrite.get("regex_uri"));
  }

  @Test
  @DisplayName("re-applying the same route a second time changes nothing")
  void isIdempotent() {
    String routePath = "/v1/datasets/ds-1/map";
    Map<String, Object> route = routeMatching(List.of(routePath, routePath + "/*"));
    Map<String, Object> proxyRewrite = singlePairRewrite(routePath, OWS_UPSTREAM);

    PathRewrite.normalize(route, proxyRewrite, RouteUpstreamKind.OWS);
    PathRewrite.normalize(route, proxyRewrite, RouteUpstreamKind.OWS);

    assertArrayEquals(new String[] {routePath, routePath + "/"}, (String[]) route.get("uris"));
    assertArrayEquals(
        new String[] {"^" + routePath + "/?$", OWS_UPSTREAM},
        (String[]) proxyRewrite.get("regex_uri"));
  }

  @Test
  @DisplayName("the bare address is recovered whichever matching entry is read back first")
  void doesNotDependOnTheOrderOfTheMatchedAddresses() {
    String owsPath = "/v1/datasets/ds-1/map";
    Map<String, Object> owsRoute = routeMatching(List.of(owsPath + "/", owsPath));
    PathRewrite.normalize(
        owsRoute, singlePairRewrite(owsPath, OWS_UPSTREAM), RouteUpstreamKind.OWS);
    assertArrayEquals(new String[] {owsPath, owsPath + "/"}, (String[]) owsRoute.get("uris"));

    String staPath = "/v1/datasets/ds-1/data";
    Map<String, Object> staRoute = routeMatching(List.of(staPath + "/*", staPath));
    PathRewrite.normalize(
        staRoute, singlePairRewrite(staPath, STA_UPSTREAM), RouteUpstreamKind.STA);
    assertArrayEquals(new String[] {staPath, staPath + "/*"}, (String[]) staRoute.get("uris"));
  }
}

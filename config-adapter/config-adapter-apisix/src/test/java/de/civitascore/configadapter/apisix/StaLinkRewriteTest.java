/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SensorThings link rewrite on STA dataset routes (issue #336). {@link
 * LinkRoundTrip} applies the produced filters to links captured from a real FROST 2.7.3 response.
 */
@DisplayName("StaLinkRewrite")
class StaLinkRewriteTest {

  private static final String API_HOST = "api.example.test";
  private static final String ROUTE_PATH = "/v1/datasets/ds-001/data";
  private static final String UPSTREAM_PATH = "/FROST-Server/v1.1/Projects(7)";
  private static final String EXTERNAL = "https://api.example.test/v1/datasets/ds-001/data";

  /** A route body in the shape {@link RouteAuthConfigurer} builds for an STA route. */
  private static Map<String, Object> staRoute() {
    Map<String, Object> route = new HashMap<>();
    route.put("uris", new String[] {ROUTE_PATH, ROUTE_PATH + "/*"});
    return route;
  }

  /** The route's {@code proxy-rewrite}, with the gateway→FROST path mapping in place. */
  private static Map<String, Object> staProxyRewrite() {
    Map<String, Object> proxyRewrite = new HashMap<>();
    proxyRewrite.put(
        "regex_uri",
        new String[] {
          "^" + ROUTE_PATH + "/?$", UPSTREAM_PATH, "^" + ROUTE_PATH + "(/.+)$", UPSTREAM_PATH + "$1"
        });
    return proxyRewrite;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> filtersOf(Map<String, Object> plugins) {
    Map<String, Object> responseRewrite = (Map<String, Object>) plugins.get("response-rewrite");
    assertNotNull(responseRewrite, "STA route must carry a response-rewrite");
    List<Map<String, Object>> filters = new ArrayList<>();
    for (Object filter : (Object[]) responseRewrite.get("filters")) {
      filters.add((Map<String, Object>) filter);
    }
    return filters;
  }

  @SuppressWarnings("unchecked")
  private static List<String> removeListOf(Map<String, Object> proxyRewrite) {
    Map<String, Object> headers = (Map<String, Object>) proxyRewrite.get("headers");
    assertNotNull(headers, "the Accept-Encoding strip needs a headers block");
    return RouteAuthConfigurer.readStringList(headers.get("remove"));
  }

  @Nested
  @DisplayName("filters")
  class Filters {

    @Test
    @DisplayName("maps the project-scoped and the canonical link form onto the dataset endpoint")
    void shouldInstallBothFilters() {
      Map<String, Object> plugins = new HashMap<>();
      Map<String, Object> proxyRewrite = staProxyRewrite();

      StaLinkRewrite.apply(staRoute(), plugins, proxyRewrite, API_HOST);

      List<Map<String, Object>> filters = filtersOf(plugins);
      assertEquals(2, filters.size());
      // @iot.nextLink repeats the project segment the regex_uri adds back — drop it.
      assertEquals("https?://[^/]+/v1\\.1/Projects\\(7\\)", filters.get(0).get("regex"));
      assertEquals(EXTERNAL, filters.get(0).get("replace"));
      assertEquals("global", filters.get(0).get("scope"));
      // Every entity link is canonical, so it only needs the endpoint in front.
      assertEquals("https?://[^/]+/v1\\.1/", filters.get(1).get("regex"));
      assertEquals(EXTERNAL + "/", filters.get(1).get("replace"));
      assertEquals("global", filters.get(1).get("scope"));
    }

    @Test
    @DisplayName("orders the project-scoped filter first so no doubled project segment survives")
    void shouldOrderProjectFilterFirst() {
      Map<String, Object> plugins = new HashMap<>();

      StaLinkRewrite.apply(staRoute(), plugins, staProxyRewrite(), API_HOST);

      List<Map<String, Object>> filters = filtersOf(plugins);
      assertTrue(
          ((String) filters.get(0).get("regex")).contains("Projects"),
          "the canonical regex also matches this prefix and would leave Projects(7) behind");
    }

    @Test
    @DisplayName("takes the version segment from the route's own upstream mapping")
    void shouldUseVersionFromUpstreamPath() {
      Map<String, Object> plugins = new HashMap<>();
      Map<String, Object> proxyRewrite = new HashMap<>();
      proxyRewrite.put(
          "regex_uri", new String[] {"^" + ROUTE_PATH + "/?$", "/FROST-Server/v1.0/Projects(7)"});

      StaLinkRewrite.apply(staRoute(), plugins, proxyRewrite, API_HOST);

      assertEquals("https?://[^/]+/v1\\.0/Projects\\(7\\)", filtersOf(plugins).get(0).get("regex"));
    }

    @Test
    @DisplayName("re-applying replaces the filters instead of stacking them")
    void shouldBeIdempotent() {
      Map<String, Object> plugins = new HashMap<>();
      Map<String, Object> proxyRewrite = staProxyRewrite();

      StaLinkRewrite.apply(staRoute(), plugins, proxyRewrite, API_HOST);
      StaLinkRewrite.apply(staRoute(), plugins, proxyRewrite, API_HOST);

      assertEquals(2, filtersOf(plugins).size());
      assertEquals(
          List.of("Accept-Encoding"),
          removeListOf(proxyRewrite),
          "the Accept-Encoding strip is a union, not an append");
    }
  }

  @Nested
  @DisplayName("Accept-Encoding strip")
  class AcceptEncodingStrip {

    @Test
    @DisplayName("adds Accept-Encoding so the filters see an uncompressed body")
    void shouldStripAcceptEncoding() {
      Map<String, Object> proxyRewrite = staProxyRewrite();

      StaLinkRewrite.apply(staRoute(), new HashMap<>(), proxyRewrite, API_HOST);

      assertTrue(removeListOf(proxyRewrite).contains("Accept-Encoding"));
    }

    @Test
    @DisplayName("keeps strip entries another concern already put there")
    void shouldPreserveForeignStripEntries() {
      Map<String, Object> proxyRewrite = staProxyRewrite();
      Map<String, Object> headers = new HashMap<>();
      headers.put("remove", new ArrayList<>(List.of("X-Allowed-Scope-Ids")));
      proxyRewrite.put("headers", headers);

      StaLinkRewrite.apply(staRoute(), new HashMap<>(), proxyRewrite, API_HOST);

      assertEquals(List.of("X-Allowed-Scope-Ids", "Accept-Encoding"), removeListOf(proxyRewrite));
    }
  }

  @Nested
  @DisplayName("routes it must leave alone")
  class NoOps {

    @Test
    @DisplayName("skips a route that carries no address")
    void shouldSkipRouteWithoutUris() {
      Map<String, Object> plugins = new HashMap<>();
      Map<String, Object> proxyRewrite = staProxyRewrite();

      StaLinkRewrite.apply(new HashMap<>(), plugins, proxyRewrite, API_HOST);

      assertNull(plugins.get("response-rewrite"));
      assertFalse(
          proxyRewrite.containsKey("headers"), "a skipped route keeps its headers untouched");
    }

    @Test
    @DisplayName("skips a route whose rewrite carries no complete pair")
    void shouldSkipRouteWithoutRegexUri() {
      Map<String, Object> plugins = new HashMap<>();

      StaLinkRewrite.apply(staRoute(), plugins, new HashMap<>(), API_HOST);

      assertNull(plugins.get("response-rewrite"));
    }

    @Test
    @DisplayName("skips an upstream path that is not project-scoped")
    void shouldSkipUnscopedUpstreamPath() {
      Map<String, Object> plugins = new HashMap<>();
      Map<String, Object> proxyRewrite = new HashMap<>();
      proxyRewrite.put("regex_uri", new String[] {"^" + ROUTE_PATH + "/?$", "/FROST-Server/v1.1"});

      StaLinkRewrite.apply(staRoute(), plugins, proxyRewrite, API_HOST);

      assertNull(
          plugins.get("response-rewrite"),
          "without a project segment the two link forms are indistinguishable");
    }
  }

  @Nested
  @DisplayName("link round-trip")
  class LinkRoundTrip {

    /** Applies the filters the way APISIX does: in order, globally. */
    private String rewrite(String body) {
      Map<String, Object> plugins = new HashMap<>();
      StaLinkRewrite.apply(staRoute(), plugins, staProxyRewrite(), API_HOST);
      String rewritten = body;
      for (Map<String, Object> filter : filtersOf(plugins)) {
        rewritten =
            rewritten.replaceAll(
                (String) filter.get("regex"),
                java.util.regex.Matcher.quoteReplacement((String) filter.get("replace")));
      }
      return rewritten;
    }

    @Test
    @DisplayName("rewrites @iot.selfLink onto the dataset endpoint")
    void shouldRewriteSelfLink() {
      assertEquals(
          "\"@iot.selfLink\":\"" + EXTERNAL + "/Things(1)\"",
          rewrite("\"@iot.selfLink\":\"https://portal.example.com:443/v1.1/Things(1)\""));
    }

    @Test
    @DisplayName("rewrites an absolute navigation link onto the dataset endpoint")
    void shouldRewriteNavigationLink() {
      assertEquals(
          "\"Datastreams@iot.navigationLink\":\"" + EXTERNAL + "/Things(1)/Datastreams\"",
          rewrite(
              "\"Datastreams@iot.navigationLink\":\"https://portal.example.com:443/v1.1/Things(1)"
                  + "/Datastreams\""));
    }

    @Test
    @DisplayName("drops the project segment from @iot.nextLink instead of doubling it")
    void shouldRewriteNextLink() {
      String rewritten =
          rewrite(
              "\"@iot.nextLink\":\"https://portal.example.com:443/v1.1/Projects(7)/Things"
                  + "?$top=2&$skip=2\"");
      assertEquals("\"@iot.nextLink\":\"" + EXTERNAL + "/Things?$top=2&$skip=2\"", rewritten);
      assertFalse(rewritten.contains("Projects(7)"), "the project segment must not survive");
    }

    @Test
    @DisplayName("rewrites the project's own self link to the bare dataset endpoint")
    void shouldRewriteProjectSelfLink() {
      assertEquals(
          "\"@iot.selfLink\":\"" + EXTERNAL + "\"",
          rewrite("\"@iot.selfLink\":\"https://portal.example.com:443/v1.1/Projects(7)\""));
    }

    @Test
    @DisplayName("leaves a URL that is not a FROST service link untouched")
    void shouldLeaveForeignUrlsAlone() {
      String observationType =
          "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/"
              + "OM_Measurement\"";
      assertEquals(observationType, rewrite(observationType));
    }
  }
}

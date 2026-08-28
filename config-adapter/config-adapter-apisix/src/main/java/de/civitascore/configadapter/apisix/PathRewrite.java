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

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The addresses a named-API route matches, and the {@code proxy-rewrite.regex_uri} mapping them
 * onto the upstream path.
 *
 * <p>An address forwarded with a trailing slash or a sub-path reaches a different upstream resource
 * than the bare one: FROST answers those with 404, and GeoServer serves them from its admin
 * service, where the map-rendering limit is switched off. An OWS endpoint takes its parameters in
 * the query string, so its route matches the bare address and the trailing-slash form and nothing
 * below them. An STA endpoint addresses entities by sub-path, so its route keeps them.
 */
final class PathRewrite {

  private static final Logger LOG = LoggerFactory.getLogger(PathRewrite.class);

  /** Back-reference carrying a sub-path through to the upstream path. */
  private static final String SUB_PATH_GROUP = "$1";

  /** A {@code regex_uri} entry is a pattern followed by its replacement. */
  private static final int PAIR_SIZE = 2;

  private PathRewrite() {}

  /** The addresses a route matches: an OWS endpoint exactly, an STA endpoint with its sub-paths. */
  static String[] matchedUris(String routePath, RouteUpstreamKind kind) {
    return kind == RouteUpstreamKind.OWS
        ? new String[] {routePath, routePath + "/"}
        : new String[] {routePath, routePath + "/*"};
  }

  /**
   * Pattern/replacement pairs for {@code proxy-rewrite.regex_uri}. APISIX tries them in order and
   * keeps the first match, so the bare address and the trailing-slash form reach one upstream path,
   * while an STA sub-path keeps its suffix.
   */
  static String[] pairs(String routePath, String upstreamPath, RouteUpstreamKind kind) {
    String exact = "^" + routePath + "/?$";
    if (kind == RouteUpstreamKind.OWS) {
      return new String[] {exact, upstreamPath};
    }
    return new String[] {
      exact, upstreamPath, "^" + routePath + "(/.+)$", upstreamPath + SUB_PATH_GROUP
    };
  }

  /**
   * Brings a route provisioned earlier onto the current shape, so it is corrected by its next
   * UPDATE or RESTORE rather than only when its dataset is provisioned again. Rebuilt from the
   * address the route already matches, so it holds for any shape an earlier version wrote, and is
   * idempotent. Left untouched when the route carries no address or no rewrite.
   */
  static void normalize(
      Map<String, Object> route, Map<String, Object> proxyRewrite, RouteUpstreamKind kind) {
    String routePath = routePathOf(route);
    String upstreamPath = upstreamPathOf(proxyRewrite);
    if (routePath == null || upstreamPath == null) {
      // A skipped correction leaves the route forwarding a trailing slash and any sub-path
      // verbatim, so it must not pass silently.
      LOG.warn(
          "Left a named-API route's rewrite unchanged: {}",
          routePath == null ? "it carries no address" : "its rewrite carries no complete pair");
      return;
    }
    proxyRewrite.put("regex_uri", pairs(routePath, upstreamPath, kind));
    // A route read back as a singular `uri` is left alone: writing `uris` beside it would hand
    // APISIX two ways of matching the same route.
    if (route.get("uris") != null) {
      route.put("uris", matchedUris(routePath, kind));
    }
  }

  /**
   * The address a route matches, without the trailing-slash or sub-path suffix a matching entry
   * carries, so a caller never has to know which entry it was handed. Reads {@code uris} and falls
   * back to a singular {@code uri}, either of which APISIX may return on read-back. Null when the
   * route carries no address.
   */
  static String routePathOf(Map<String, Object> route) {
    List<String> uris = RouteAuthConfigurer.readStringList(route.get("uris"));
    if (!uris.isEmpty()) {
      return basePath(uris.get(0));
    }
    Object uri = route.get("uri");
    return uri instanceof String s && !s.isBlank() ? basePath(s) : null;
  }

  /**
   * The upstream path a route's rewrite maps onto, read from {@code proxy-rewrite.regex_uri} and
   * stripped of any sub-path back-reference. The single reader of a replacement's shape, so a
   * change to that shape has one place to land. Null when the route carries no complete pair.
   */
  static String upstreamPathOf(Map<String, Object> proxyRewrite) {
    List<String> regexUri = RouteAuthConfigurer.readStringList(proxyRewrite.get("regex_uri"));
    if (regexUri.size() < PAIR_SIZE) {
      return null;
    }
    String replacement = regexUri.get(1);
    return replacement.endsWith(SUB_PATH_GROUP)
        ? replacement.substring(0, replacement.length() - SUB_PATH_GROUP.length())
        : replacement;
  }

  private static String basePath(String uri) {
    if (uri.endsWith("/*")) {
      return uri.substring(0, uri.length() - "/*".length());
    }
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}

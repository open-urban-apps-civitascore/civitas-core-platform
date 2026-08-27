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
    String routePath = basePath(firstUri(route));
    List<String> regexUri = RouteAuthConfigurer.readStringList(proxyRewrite.get("regex_uri"));
    if (routePath == null || regexUri.size() < PAIR_SIZE) {
      return;
    }
    proxyRewrite.put("regex_uri", pairs(routePath, upstreamPathOf(regexUri.get(1)), kind));
    // A route read back as a singular `uri` is left alone: writing `uris` beside it would hand
    // APISIX two ways of matching the same route.
    if (route.get("uris") != null) {
      route.put("uris", matchedUris(routePath, kind));
    }
  }

  /**
   * The address a route matches, which is the path its rewrite maps. Reads {@code uris} and falls
   * back to a singular {@code uri}, either of which APISIX may return on read-back.
   */
  static String firstUri(Map<String, Object> route) {
    List<String> uris = RouteAuthConfigurer.readStringList(route.get("uris"));
    if (!uris.isEmpty()) {
      return uris.get(0);
    }
    Object uri = route.get("uri");
    return uri instanceof String s && !s.isBlank() ? s : null;
  }

  /**
   * The address a route matches, without the trailing-slash or sub-path suffix a matching entry
   * carries, so the shape a rewrite is rebuilt from does not depend on which entry is read first.
   */
  private static String basePath(String uri) {
    if (uri == null) {
      return null;
    }
    if (uri.endsWith("/*")) {
      return uri.substring(0, uri.length() - "/*".length());
    }
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  /** The upstream path a rewrite replacement targets, without any sub-path back-reference. */
  private static String upstreamPathOf(String replacement) {
    return replacement.endsWith(SUB_PATH_GROUP)
        ? replacement.substring(0, replacement.length() - SUB_PATH_GROUP.length())
        : replacement;
  }
}

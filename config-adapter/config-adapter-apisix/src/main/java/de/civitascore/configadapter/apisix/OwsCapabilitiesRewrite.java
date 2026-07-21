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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds a {@code response-rewrite} to an OWS (map-service) route so GeoServer's self-referential
 * capabilities URLs are reachable by map clients through the gateway.
 *
 * <p>GeoServer advertises its GetMap/GetFeature endpoints in GetCapabilities with the internal
 * gateway path ({@code scheme://host/…/{workspace}/{wfs|wms|…}}). APISIX only routes {@code
 * /v1/datasets/{id}/{slug}}, so a client (QGIS, …) following the advertised URL would get a 404.
 * The filter rewrites that internal form to this route's external dataset endpoint ({@code
 * https://{apiHost}{routePath}}), which the route's own {@code regex_uri} maps back to the
 * workspace OWS endpoint — so the rewritten URL round-trips.
 *
 * <p>It also strips the request {@code Accept-Encoding} so the upstream returns an uncompressed
 * body: {@code response-rewrite} filters match the raw response bytes and would silently no-op on a
 * gzipped capabilities document.
 *
 * <p>Everything is derived from the route body — the external path from {@code uris}, the workspace
 * path from {@code proxy-rewrite.regex_uri} — so CREATE (fresh skeleton) and UPDATE/RESTORE (route
 * read back from APISIX) all produce the same shape.
 */
final class OwsCapabilitiesRewrite {

  /** OWS service endpoints GeoServer emits in its capabilities self-URLs ({@code .../{ws}/wfs}). */
  private static final String OWS_SERVICE_ALTERNATION = "wfs|wms|wcs|wps|wmts|ows|gwc";

  private static final String HEADERS_KEY = "headers";
  private static final String REMOVE_KEY = "remove";

  /** {@code regex_uri} is a {@code [match, replacement]} pair; the replacement is at index 1. */
  private static final int REGEX_URI_PAIR_SIZE = 2;

  private static final String OWS_PATH_SUFFIX = "/ows";

  private OwsCapabilitiesRewrite() {}

  /**
   * Installs the capabilities-URL rewrite on {@code plugins} (and the {@code Accept-Encoding} strip
   * on {@code proxyRewrite}). No-op when the route carries neither an external path nor a workspace
   * OWS mapping.
   */
  static void apply(
      Map<String, Object> route,
      Map<String, Object> plugins,
      Map<String, Object> proxyRewrite,
      String apiHost) {
    String externalPath = firstUri(route);
    String workspacePath = upstreamWorkspacePath(proxyRewrite);
    if (externalPath == null || workspacePath == null) {
      return;
    }

    Map<String, Object> filter = new HashMap<>();
    filter.put(
        "regex",
        "https?://[^/]+" + regexEscape(workspacePath) + "/(" + OWS_SERVICE_ALTERNATION + ")");
    filter.put("scope", "global");
    filter.put("replace", "https://" + apiHost + externalPath);

    Map<String, Object> responseRewrite = new HashMap<>();
    responseRewrite.put("filters", new Object[] {filter});
    plugins.put("response-rewrite", responseRewrite);

    stripRequestBodyEncoding(proxyRewrite);
  }

  /**
   * First URI a route matches — its external path (e.g. {@code /v1/datasets/{id}/{slug}}). Reads
   * {@code uris} (what CREATE writes) and falls back to a singular {@code uri} (APISIX may return
   * either form on read-back).
   */
  private static String firstUri(Map<String, Object> route) {
    List<String> uris = RouteAuthConfigurer.readStringList(route.get("uris"));
    if (!uris.isEmpty()) {
      return uris.get(0);
    }
    Object uri = route.get("uri");
    return uri instanceof String s && !s.isBlank() ? s : null;
  }

  /**
   * The workspace OWS path without the trailing {@code /ows}, read from the route's {@code
   * proxy-rewrite.regex_uri} replacement (e.g. {@code /geoserver-cloud/{ws}/ows$1} → {@code
   * /geoserver-cloud/{ws}}). Null when the route carries no such OWS mapping.
   */
  private static String upstreamWorkspacePath(Map<String, Object> proxyRewrite) {
    List<String> regexUri = RouteAuthConfigurer.readStringList(proxyRewrite.get("regex_uri"));
    if (regexUri.size() < REGEX_URI_PAIR_SIZE) {
      return null;
    }
    String upstreamPath = regexUri.get(1);
    int marker = upstreamPath.indexOf('$');
    if (marker >= 0) {
      upstreamPath = upstreamPath.substring(0, marker);
    }
    if (!upstreamPath.endsWith(OWS_PATH_SUFFIX)) {
      return null;
    }
    return upstreamPath.substring(0, upstreamPath.length() - OWS_PATH_SUFFIX.length());
  }

  /**
   * Add {@code Accept-Encoding} to {@code proxy-rewrite.headers.remove} (idempotent union) so the
   * upstream returns an uncompressed body the {@code response-rewrite} filter can match.
   */
  @SuppressWarnings("unchecked")
  private static void stripRequestBodyEncoding(Map<String, Object> proxyRewrite) {
    Object existing = proxyRewrite.get(HEADERS_KEY);
    Map<String, Object> headers =
        existing instanceof Map ? new HashMap<>((Map<String, Object>) existing) : new HashMap<>();
    List<String> remove = RouteAuthConfigurer.readStringList(headers.get(REMOVE_KEY));
    if (!remove.contains("Accept-Encoding")) {
      remove.add("Accept-Encoding");
    }
    headers.put(REMOVE_KEY, remove);
    proxyRewrite.put(HEADERS_KEY, headers);
  }

  /** Escape PCRE metacharacters in a literal path used inside a {@code response-rewrite} regex. */
  private static String regexEscape(String literal) {
    return literal.replaceAll("([.^$*+?()\\[\\]{}|\\\\])", "\\\\$1");
  }
}

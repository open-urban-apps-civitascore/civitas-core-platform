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
    String externalPath = PathRewrite.routePathOf(route);
    String workspacePath = upstreamWorkspacePath(proxyRewrite);
    if (externalPath == null || workspacePath == null) {
      return;
    }

    ResponseRewrites.install(
        plugins,
        List.of(
            ResponseRewrites.filter(
                "https?://[^/]+"
                    + ResponseRewrites.regexEscape(workspacePath)
                    + "/("
                    + OWS_SERVICE_ALTERNATION
                    + ")",
                "https://" + apiHost + externalPath)));

    ResponseRewrites.stripRequestBodyEncoding(proxyRewrite);
  }

  /**
   * The workspace path behind the route's OWS mapping (e.g. {@code /geoserver-cloud/{ws}/ows} →
   * {@code /geoserver-cloud/{ws}}). Null when the route carries no such mapping.
   */
  private static String upstreamWorkspacePath(Map<String, Object> proxyRewrite) {
    String upstreamPath = PathRewrite.upstreamPathOf(proxyRewrite);
    if (upstreamPath == null || !upstreamPath.endsWith(OWS_PATH_SUFFIX)) {
      return null;
    }
    return upstreamPath.substring(0, upstreamPath.length() - OWS_PATH_SUFFIX.length());
  }
}

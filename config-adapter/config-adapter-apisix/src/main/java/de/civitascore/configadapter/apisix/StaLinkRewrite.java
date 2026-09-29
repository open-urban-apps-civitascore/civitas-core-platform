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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds a {@code response-rewrite} to an STA (sensor-data) route so FROST's {@code @iot.selfLink},
 * {@code *@iot.navigationLink} and {@code @iot.nextLink} are reachable through the gateway. FROST
 * renders them from its server-wide {@code serviceRootUrl}, which cannot know the per-dataset
 * address a client used; the filters map them onto this route's external endpoint, which the
 * route's own {@code regex_uri} maps back — so a rewritten link round-trips.
 *
 * <p>Two filters, and the order is load-bearing: {@code @iot.nextLink} mirrors the requested path
 * and so repeats the project segment the {@code regex_uri} adds back, which must be dropped; every
 * other link is canonical and only needs the endpoint in front. The canonical filter also matches
 * the project-scoped prefix, so running it first would leave the segment doubled.
 *
 * <p>Requires {@code useAbsoluteNavigationLinks: true} on FROST — a relative link is calibrated to
 * FROST's own path depth and cannot be repaired in the response.
 *
 * <p>Limits, measured on FROST 2.7.3. Every entity stays reachable under its owning Thing ({@code
 * …/{slug}/Things(1)/Datastreams(1)/Observations(1)}), but the canonical top-level self link FROST
 * advertises for a Datastream, Observation, ObservedProperty or HistoricalLocation 404s: the
 * Projects plugin gives a Project navigation to Things, Locations, Sensors and FeaturesOfInterest
 * only. No filter can bridge that — the owning Thing's id is in neither the link nor the object
 * around it — and mapping those onto the unscoped FROST path would serve one dataset's client
 * another dataset's data, since FROST does not filter reads by the caller's project roles. A link
 * into another project ({@code $expand=Projects} on a shared Thing) likewise becomes a 404 inside
 * the gateway, which beats handing out an internal address.
 *
 * <p>Everything is derived from the route body, so CREATE and UPDATE/RESTORE produce the same
 * shape.
 */
final class StaLinkRewrite {

  /** Upstream path of an STA dataset route, e.g. {@code /FROST-Server/v1.1/Projects(7)}. */
  private static final Pattern PROJECT_UPSTREAM_PATH =
      Pattern.compile("^.*/(v\\d+\\.\\d+)/(Projects\\([^/]+\\))$");

  /**
   * Origin of a FROST-rendered link, matched host-agnostically because {@code serviceRootUrl} is a
   * FROST-side setting the adapter never sees. Keying on the path alone also rewrites a foreign URL
   * carrying the same version segment in entity data — the exposure {@link OwsCapabilitiesRewrite}
   * accepts too.
   */
  private static final String ANY_ORIGIN = "https?://[^/]+";

  private StaLinkRewrite() {}

  /**
   * Installs the rewrite on {@code plugins} and the {@code Accept-Encoding} strip on {@code
   * proxyRewrite}. No-op without an external path or a project-scoped FROST mapping.
   */
  static void apply(
      Map<String, Object> route,
      Map<String, Object> plugins,
      Map<String, Object> proxyRewrite,
      String apiHost) {
    String externalPath = PathRewrite.routePathOf(route);
    String upstreamPath = PathRewrite.upstreamPathOf(proxyRewrite);
    if (externalPath == null || upstreamPath == null) {
      return;
    }
    Matcher upstream = PROJECT_UPSTREAM_PATH.matcher(upstreamPath);
    if (!upstream.matches()) {
      return;
    }

    String serviceRoot = ANY_ORIGIN + "/" + ResponseRewrites.regexEscape(upstream.group(1)) + "/";
    String projectSegment = ResponseRewrites.regexEscape(upstream.group(2));
    String external = "https://" + apiHost + externalPath;

    ResponseRewrites.install(
        plugins,
        List.of(
            ResponseRewrites.filter(serviceRoot + projectSegment, external),
            ResponseRewrites.filter(serviceRoot, external + "/")));

    ResponseRewrites.stripRequestBodyEncoding(proxyRewrite);
  }
}

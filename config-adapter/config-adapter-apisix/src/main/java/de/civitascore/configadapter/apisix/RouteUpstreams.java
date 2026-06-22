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

import de.civitascore.configadapter.model.dataset.WorkspaceNames;
import java.net.URI;
import java.util.Map;

/**
 * Resolves the APISIX upstream a named-API route binds to, by {@link RouteUpstreamKind}. A dataset
 * has at most two upstreams: the FROST-project upstream (keyed by the bare dataset id, for STA) and
 * the map-server upstream (keyed by {@link #owsUpstreamId}, for OWS). The FROST upstream URL
 * arrives per-command from the saga; the map-server base comes from {@code apisix.geoserver.url}.
 */
final class RouteUpstreams {

  /**
   * Suffix for the per-dataset map-server (GeoServer/OWS) upstream id, distinct from the FROST one.
   */
  private static final String OWS_UPSTREAM_SUFFIX = "-ows";

  private final String geoserverUrl;

  RouteUpstreams(String geoserverUrl) {
    this.geoserverUrl = geoserverUrl;
  }

  /**
   * An upstream target split into the APISIX node ({@code host[:port]}), path and scheme, e.g.
   * {@code http://civitas-frost:8080/FROST-Server/v1.1/Projects(1)} → node {@code
   * civitas-frost:8080}, path {@code /FROST-Server/v1.1/Projects(1)}.
   */
  record Target(String node, String path, String scheme) {

    static Target parse(String url) {
      URI uri = URI.create(url);
      if (uri.getHost() == null || uri.getHost().isBlank()) {
        // A host-less URL (missing scheme/authority) would yield a "null"/"null:port" node and a
        // silently-unreachable upstream APISIX accepts without complaint — fail fast instead. The
        // raw URL is not echoed: a FROST upstream URL may carry credentials in its userinfo.
        throw new IllegalArgumentException(
            "upstream URL has no host (scheme=" + uri.getScheme() + ")");
      }
      String node = uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
      return new Target(
          node,
          uri.getPath() != null ? uri.getPath() : "/",
          uri.getScheme() != null ? uri.getScheme() : "http");
    }
  }

  /** APISIX upstream id for a dataset's map-server (GeoServer/OWS) upstream. */
  static String owsUpstreamId(String datasetId) {
    return datasetId + OWS_UPSTREAM_SUFFIX;
  }

  /** The APISIX upstream request body (single round-robin node) for a target. */
  static Map<String, Object> body(Target target) {
    return Map.of(
        "type", "roundrobin", "scheme", target.scheme(), "nodes", Map.of(target.node(), 1));
  }

  /** The FROST (STA) upstream target, parsed from the per-command upstream URL. */
  static Target frost(String upstreamUrl) {
    return Target.parse(upstreamUrl);
  }

  /**
   * The map-server (OWS) upstream target for a dataset: host/port/scheme from the configured {@code
   * apisix.geoserver.url} and the per-workspace OWS path {@code /geoserver/{workspace}/ows}. The
   * workspace name is derived from the dataset id via the same normalization the GeoServer
   * provisioning side uses ({@link WorkspaceNames#fromDatasetId}), so the route reaches the
   * workspace the GeoServer adapter creates for the same dataset.
   */
  Target map(String datasetId) {
    if (geoserverUrl == null || geoserverUrl.isBlank()) {
      throw new IllegalStateException(
          "apisix.geoserver.url must be configured to route an OWS (map services) named API to"
              + " GeoServer");
    }
    Target base = Target.parse(geoserverUrl);
    return new Target(
        base.node(),
        base.path() + "/" + WorkspaceNames.fromDatasetId(datasetId) + "/ows",
        base.scheme());
  }
}

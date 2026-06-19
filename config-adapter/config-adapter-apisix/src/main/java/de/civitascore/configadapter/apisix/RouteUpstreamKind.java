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

import java.util.Optional;

/**
 * Which upstream a named-API route is bound to, selected by the API's standard. This is the seam
 * the map-service routing plugs into: a single dataset can now publish routes to two different
 * upstreams depending on the named API's standard.
 *
 * <ul>
 *   <li>{@link #SENSOR} — {@code STA} (FROST SensorThings): the per-dataset FROST-project upstream,
 *       with FROST upstream credentials injected on protected routes.
 *   <li>{@link #MAP} — {@code OWS} (GeoServer WFS/WMS): the per-dataset map-server upstream, with
 *       <b>no</b> upstream credential. GeoServer serves the workspace OWS endpoint anonymously —
 *       the gateway/OPA gate is the authorization boundary for map-service data access (see {@code
 *       GeoServerAuth} in config-adapter-geoserver).
 * </ul>
 *
 * <p>A null/blank standard maps to {@link #SENSOR} for backward compatibility (the production saga
 * has always sent STA, and routes provisioned before this seam carry no standard marker). Any other
 * standard ({@code CUSTOM}, unknown) is not routable and is rejected fail-fast in {@code
 * CREATE_ROUTE} rather than provisioning a route behind a public URL it cannot serve.
 */
enum RouteUpstreamKind {
  SENSOR,
  MAP;

  static final String STA = "STA";
  static final String OWS = "OWS";

  /**
   * Classifies a named API's standard into its routing kind, or {@link Optional#empty()} when the
   * standard cannot be routed.
   */
  static Optional<RouteUpstreamKind> fromStandard(String standard) {
    if (standard == null || standard.isBlank() || STA.equalsIgnoreCase(standard)) {
      return Optional.of(SENSOR);
    }
    if (OWS.equalsIgnoreCase(standard)) {
      return Optional.of(MAP);
    }
    return Optional.empty();
  }
}

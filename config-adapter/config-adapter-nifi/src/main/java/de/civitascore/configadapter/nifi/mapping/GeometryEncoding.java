/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

/**
 * How a geometry-producing mapping op (currently {@code geoPoint}) must be rendered for the target
 * sink. This is the one axis on which mapping compilation is sink-dependent: a PostGIS column
 * parses a {@code WKT} string, whereas a FROST sink expects a {@code GEOJSON} object. Kept in the
 * mapping package (rather than referencing the {@code flow.SinkType} enum) so the compiler stays
 * free of any sink/flow dependency; the deployment planner translates the sink type to an encoding.
 */
public enum GeometryEncoding {
  /**
   * Well-Known Text, e.g. {@code POINT(8.4 49.0)} — parsed on insert by a PostGIS geometry column.
   */
  WKT,
  /** GeoJSON object, e.g. {@code {"type":"Point","coordinates":[8.4,49.0]}} — for FROST sinks. */
  GEOJSON
}

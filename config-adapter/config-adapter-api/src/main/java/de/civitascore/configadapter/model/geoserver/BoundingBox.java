/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.geoserver;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Axis-aligned bounding box for a GeoServer resource. Used in {@link FeatureTypeConfig} for both
 * the native and lat/lon bounding box declarations.
 *
 * <p>Example:
 *
 * <pre>{@code
 * {
 *   "minx": -180.0, "maxx": 180.0,
 *   "miny":  -90.0, "maxy":  90.0,
 *   "crs": "EPSG:4326"
 * }
 * }</pre>
 */
public record BoundingBox(Double minx, Double maxx, Double miny, Double maxy, String crs) {

  /**
   * Converts this bounding box to a plain map for the GeoServer REST API. Only non-null fields are
   * included.
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (minx != null) map.put("minx", minx);
    if (maxx != null) map.put("maxx", maxx);
    if (miny != null) map.put("miny", miny);
    if (maxy != null) map.put("maxy", maxy);
    if (crs != null) map.put("crs", crs);
    return Collections.unmodifiableMap(map);
  }
}

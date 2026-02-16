/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.frost;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit of measurement for an OGC SensorThings API Datastream. Follows the OGC SensorThings API Part
 * 1 specification for describing the unit used by Observations in a Datastream.
 *
 * <p>Example (OGC):
 *
 * <pre>{@code
 * {
 *   "name": "Degree Celsius",
 *   "symbol": "°C",
 *   "definition": "http://unitsofmeasure.org/ucum.html#para-30"
 * }
 * }</pre>
 *
 * @param name full name of the unit (e.g. "Degree Celsius")
 * @param symbol symbol or abbreviation (e.g. "°C")
 * @param definition URI referencing the unit definition (e.g. UCUM)
 * @see <a href="https://docs.ogc.org/is/18-088/18-088.html">OGC SensorThings API</a>
 */
public record UnitOfMeasurement(String name, String symbol, String definition) {

  /**
   * Converts this unit of measurement to a plain map for the FROST-Server API. Only non-null fields
   * are included.
   *
   * @return an unmodifiable map of unit key-value pairs (may be empty)
   */
  public Map<String, Object> toApiMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    if (name != null) map.put("name", name);
    if (symbol != null) map.put("symbol", symbol);
    if (definition != null) map.put("definition", definition);
    return Collections.unmodifiableMap(map);
  }
}

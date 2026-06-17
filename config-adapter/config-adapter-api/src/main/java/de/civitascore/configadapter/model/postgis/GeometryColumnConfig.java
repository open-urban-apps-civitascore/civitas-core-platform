/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.postgis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Configuration for a PostGIS geometry column. Kept separate from {@link ColumnConfig} so the
 * generic SQL model layer carries no spatial-database concepts.
 *
 * @param name column name (unquoted)
 * @param geometryType shape constraint
 * @param srid coordinate reference system identifier (e.g. 4326); {@code null} for unconstrained
 * @param dimension geometric dimension: {@code 2} (XY), {@code 3} (XYZ or XYM), or {@code 4}
 *     (XYZM); {@code null} treated as 2
 * @param nullable whether the column may be NULL; {@code null} treated as {@code true}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeometryColumnConfig(
    String name, GeometryType geometryType, Integer srid, Integer dimension, Boolean nullable) {

  public boolean isNullable() {
    return nullable == null || nullable;
  }

  public int effectiveDimension() {
    return dimension == null ? 2 : dimension;
  }
}

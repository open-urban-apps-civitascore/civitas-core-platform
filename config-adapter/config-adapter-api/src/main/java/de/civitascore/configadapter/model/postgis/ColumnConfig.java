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
 * Configuration for a single non-spatial SQL column.
 *
 * @param name column name (unquoted; the dialect handles quoting)
 * @param type logical column type (see {@link ColumnType})
 * @param length optional length for VARCHAR; ignored for other types
 * @param precision optional precision for NUMERIC; ignored for other types
 * @param scale optional scale for NUMERIC; ignored for other types
 * @param nullable whether the column may be NULL; {@code null} treated as {@code true}
 * @param defaultExpr raw SQL expression for the column default (e.g. {@code "now()"}); no quoting
 *     applied — caller is responsible for syntactic correctness
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnConfig(
    String name,
    ColumnType type,
    Integer length,
    Integer precision,
    Integer scale,
    Boolean nullable,
    String defaultExpr) {

  public boolean isNullable() {
    return nullable == null || nullable;
  }
}

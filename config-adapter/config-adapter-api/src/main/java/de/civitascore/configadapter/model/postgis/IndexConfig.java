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
import java.util.List;

/**
 * Configuration for a single table index.
 *
 * @param name index name (unquoted); if {@code null} the dialect generates one
 * @param columns one or more column names to index (in order)
 * @param unique whether the index enforces uniqueness; {@code null} treated as {@code false}
 * @param method index method ({@link IndexMethod#BTREE} by default); {@link IndexMethod#GIST} is
 *     required for geometry-column indexes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndexConfig(String name, List<String> columns, Boolean unique, IndexMethod method) {

  public boolean isUnique() {
    return unique != null && unique;
  }

  public IndexMethod effectiveMethod() {
    return method == null ? IndexMethod.BTREE : method;
  }

  /** Index access methods supported by the adapter. */
  public enum IndexMethod {
    BTREE,
    GIST,
    GIN
  }
}

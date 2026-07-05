/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import java.util.ArrayList;
import java.util.List;

/**
 * A PostGIS sink's resolved configuration.
 *
 * @param tableName the target table
 * @param primaryKeyColumns the target's primary-key columns (from the data structure's {@code
 *     x-core-primaryKey} marker); empty for INSERT semantics, non-empty switches PutDatabaseRecord
 *     to UPSERT keyed on these columns so repeated reads do not duplicate rows
 */
public record PostgisSinkSpec(String tableName, List<String> primaryKeyColumns)
    implements SinkSpec {

  public PostgisSinkSpec {
    // A PostGIS sink without a table name is an invalid state: PutDatabaseRecord would have no
    // target and every write would fail. Reject it at construction so the invariant cannot be
    // misrepresented.
    if (tableName == null || tableName.isBlank()) {
      throw new IllegalArgumentException("POSTGIS sink requires a non-blank tableName");
    }
    // These names are joined verbatim into NiFi's "Update Keys", so normalize them here: trim,
    // reject blank entries (a broken UPSERT config otherwise), and de-duplicate while preserving
    // order.
    primaryKeyColumns = sanitizeKeyColumns(primaryKeyColumns);
  }

  /** A sink without an explicit primary key (INSERT semantics). */
  public PostgisSinkSpec(String tableName) {
    this(tableName, List.of());
  }

  @Override
  public SinkType type() {
    return SinkType.POSTGIS;
  }

  private static List<String> sanitizeKeyColumns(List<String> keys) {
    if (keys == null) {
      return List.of();
    }
    List<String> sanitized = new ArrayList<>();
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("primaryKeyColumns must not contain blank entries");
      }
      String trimmed = key.trim();
      if (!sanitized.contains(trimmed)) {
        sanitized.add(trimmed);
      }
    }
    return List.copyOf(sanitized);
  }
}

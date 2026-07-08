/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.mapping.NifiExpressionLanguage;
import java.util.ArrayList;
import java.util.List;

/**
 * A PostGIS sink's resolved configuration.
 *
 * @param tableName the target table
 * @param schemaName the target schema the table lives in (the dedicated per-DataSet schema); {@code
 *     null} when unset, so PutDatabaseRecord falls back to the connection's {@code search_path}
 * @param primaryKeyColumns the target's primary-key columns (from the data structure's {@code
 *     x-core-primaryKey} marker); empty for INSERT semantics, non-empty switches PutDatabaseRecord
 *     to UPSERT keyed on these columns so repeated reads do not duplicate rows
 */
public record PostgisSinkSpec(String tableName, String schemaName, List<String> primaryKeyColumns)
    implements SinkSpec {

  public PostgisSinkSpec {
    // A PostGIS sink without a table name is an invalid state: PutDatabaseRecord would have no
    // target and every write would fail. Reject it at construction so the invariant cannot be
    // misrepresented.
    if (tableName == null || tableName.isBlank()) {
      throw new IllegalArgumentException("POSTGIS sink requires a non-blank tableName");
    }
    // The table name lands in PutDatabaseRecord's EL-enabled "Table Name" property, so a tenant
    // value of ${ENV_VAR} would expand against the NiFi process environment at write time. Escape
    // it here, at the boundary where tenant text enters the spec, so the invariant holds for every
    // sink path — the same escape the mapping values already get.
    tableName = NifiExpressionLanguage.escape(tableName);
    // The schema is optional: blank normalizes to null so bind() leaves "Schema Name" unset and the
    // write resolves via search_path. When present it lands in PutDatabaseRecord's EL-enabled
    // "Schema Name" property, so escape it for the same reason as the table name.
    schemaName = normalizeSchemaName(schemaName);
    // These names are joined verbatim into NiFi's likewise EL-enabled "Update Keys", so normalize
    // them here: trim, reject blank entries (a broken UPSERT config otherwise), de-duplicate while
    // preserving order, and escape EL for the same reason as the table name.
    primaryKeyColumns = sanitizeKeyColumns(primaryKeyColumns);
  }

  /** A sink with a table name and primary key but no explicit schema (search_path fallback). */
  public PostgisSinkSpec(String tableName, List<String> primaryKeyColumns) {
    this(tableName, null, primaryKeyColumns);
  }

  /** A sink without an explicit schema or primary key (INSERT semantics). */
  public PostgisSinkSpec(String tableName) {
    this(tableName, null, List.of());
  }

  private static String normalizeSchemaName(String schema) {
    if (schema == null || schema.isBlank()) {
      return null;
    }
    return NifiExpressionLanguage.escape(schema.trim());
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
      String trimmed = NifiExpressionLanguage.escape(key.trim());
      if (!sanitized.contains(trimmed)) {
        sanitized.add(trimmed);
      }
    }
    return List.copyOf(sanitized);
  }
}

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

import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The resolved inputs to plan a single pipeline deployment.
 *
 * @param pipelineId the pipeline id (used to derive the process-group name)
 * @param graphData the engine-neutral pipeline graph ({@code dataPipelines[].data})
 * @param source the resolved source datasource (with possibly encrypted credentials)
 * @param sink the resolved sink specification
 * @param frostProjectId the dataset's FROST project id (from the saga's create-project step);
 *     required for a FROST sink — it scopes the find-or-create flow to the dataset's project — and
 *     must be null for any other sink
 */
public record PipelineDeploymentRequest(
    String pipelineId,
    Map<String, Object> graphData,
    Datasource source,
    SinkSpec sink,
    String frostProjectId) {

  public PipelineDeploymentRequest {
    if (pipelineId == null || pipelineId.isBlank()) {
      throw new IllegalArgumentException("pipelineId must be non-blank");
    }
    Objects.requireNonNull(sink, "sink");
    // The project id is interpolated into NiFi processor URLs and $filter expressions, so it must
    // be the numeric id FROST's create-project step returned — anything else is a mis-wired
    // payload (or an injection attempt). Without it a FROST flow would post to the server root,
    // invisible to the dataset's project-scoped named API. A non-FROST sink carrying one is a
    // meaningless state (mirrors SinkSpec.primaryKeyColumns).
    if (sink.type() == SinkType.FROST) {
      if (frostProjectId == null || frostProjectId.isBlank()) {
        throw new IllegalArgumentException("a FROST sink requires a non-blank frostProjectId");
      }
      if (!frostProjectId.matches("\\d+")) {
        throw new IllegalArgumentException("frostProjectId must be numeric: " + frostProjectId);
      }
    } else if (frostProjectId != null) {
      throw new IllegalArgumentException(
          "frostProjectId is only valid for a FROST sink, not " + sink.type());
    }
    // Defensively copy the caller's graph map so the record does not alias mutable external state
    // (consistent with MappingConfig). A LinkedHashMap keeps node/edge order and tolerates the null
    // values that arbitrary JSON graphs may carry; Map.copyOf would reject those.
    graphData =
        graphData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(graphData));
  }

  /** A request without a FROST project id (non-FROST sinks). */
  public PipelineDeploymentRequest(
      String pipelineId, Map<String, Object> graphData, Datasource source, SinkSpec sink) {
    this(pipelineId, graphData, source, sink, null);
  }

  /**
   * The resolved sink for the pipeline. The sink's data-structure model is intentionally NOT
   * carried here: deriving the table schema from it is the PostGIS adapter's job (it creates the
   * typed table via DDL); the NiFi pipeline only writes records, and PutDatabaseRecord coerces them
   * to the existing column types.
   *
   * @param type the sink type
   * @param tableName the target table (POSTGIS), or null
   * @param primaryKeyColumns the target's primary-key columns (from the data structure's {@code
   *     x-core-primaryKey} marker); empty for INSERT semantics, non-empty switches
   *     PutDatabaseRecord to UPSERT keyed on these columns so repeated reads do not duplicate rows
   */
  public record SinkSpec(SinkType type, String tableName, List<String> primaryKeyColumns) {
    public SinkSpec {
      Objects.requireNonNull(type, "type");
      // A PostGIS sink without a table name is an invalid state: PutDatabaseRecord would have no
      // target and every write would fail. Reject it at construction so the invariant cannot be
      // misrepresented (a FROST/HTTP sink legitimately carries no table name).
      if (type == SinkType.POSTGIS && (tableName == null || tableName.isBlank())) {
        throw new IllegalArgumentException("POSTGIS sink requires a non-blank tableName");
      }
      // These names are joined verbatim into NiFi's "Update Keys", so normalize them here: trim,
      // reject blank entries (a broken UPSERT config otherwise), and de-duplicate while preserving
      // order.
      primaryKeyColumns = sanitizeKeyColumns(primaryKeyColumns);
      // Primary-key columns only drive the PostGIS PutDatabaseRecord UPSERT; a non-POSTGIS sink
      // carrying them is a meaningless state, so reject it rather than let it pass unused.
      if (type != SinkType.POSTGIS && !primaryKeyColumns.isEmpty()) {
        throw new IllegalArgumentException(
            "primaryKeyColumns are only valid for a POSTGIS sink, not " + type);
      }
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

    /** A sink without an explicit primary key (INSERT semantics). */
    public SinkSpec(SinkType type, String tableName) {
      this(type, tableName, List.of());
    }
  }
}

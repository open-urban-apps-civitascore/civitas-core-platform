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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The resolved inputs to plan a single pipeline deployment.
 *
 * @param pipelineId the pipeline id (used to derive the process-group name)
 * @param graphData the engine-neutral pipeline graph ({@code dataPipelines[].data})
 * @param source the resolved source datasource (with possibly encrypted credentials)
 * @param sink the resolved sink specification
 */
public record PipelineDeploymentRequest(
    String pipelineId, Map<String, Object> graphData, Datasource source, SinkSpec sink) {

  public PipelineDeploymentRequest {
    if (pipelineId == null || pipelineId.isBlank()) {
      throw new IllegalArgumentException("pipelineId must be non-blank");
    }
    Objects.requireNonNull(sink, "sink");
    // Defensively copy the caller's graph map so the record does not alias mutable external state
    // (consistent with MappingConfig). A LinkedHashMap keeps node/edge order and tolerates the null
    // values that arbitrary JSON graphs may carry; Map.copyOf would reject those.
    graphData =
        graphData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(graphData));
  }

  /**
   * The resolved sink for the pipeline. The sink's data-structure model is intentionally NOT
   * carried here: deriving the table schema from it is the PostGIS adapter's job (it creates the
   * typed table via DDL); the NiFi pipeline only writes records, and PutDatabaseRecord coerces them
   * to the existing column types.
   *
   * @param type the sink type
   * @param tableName the target table (POSTGIS), or null
   */
  public record SinkSpec(SinkType type, String tableName) {
    public SinkSpec {
      Objects.requireNonNull(type, "type");
      // A PostGIS sink without a table name is an invalid state: PutDatabaseRecord would have no
      // target and every write would fail. Reject it at construction so the invariant cannot be
      // misrepresented (a FROST/HTTP sink legitimately carries no table name).
      if (type == SinkType.POSTGIS && (tableName == null || tableName.isBlank())) {
        throw new IllegalArgumentException("POSTGIS sink requires a non-blank tableName");
      }
    }
  }
}

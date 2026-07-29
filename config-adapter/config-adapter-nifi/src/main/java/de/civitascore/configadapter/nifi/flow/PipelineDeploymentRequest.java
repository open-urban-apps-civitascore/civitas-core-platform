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
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The resolved inputs to plan a single pipeline deployment. Per-sink concerns (table name, primary
 * key, FROST project id) live in the typed {@link SinkSpec} variant, with their invariants — this
 * record stays sink-agnostic.
 *
 * @param pipelineId the pipeline id (used to derive the process-group name)
 * @param graphData the engine-neutral pipeline graph ({@code dataPipelines[].data})
 * @param source the resolved source datasource (with possibly encrypted credentials)
 * @param sink the resolved, typed sink specification
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
}

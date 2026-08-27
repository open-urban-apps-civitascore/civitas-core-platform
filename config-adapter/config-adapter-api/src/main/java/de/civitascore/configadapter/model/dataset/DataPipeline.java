/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A pipeline definition within a dataset. The {@code data} field carries the clean CORE Pipeline
 * document (the {@code nodes}/{@code edges} graph whose nodes reference their source/sink/mapping
 * by CORE URN — {@code sourceRef}/{@code sinkRef}/{@code mappingRef}), opaque to the backend.
 * {@code dataSourceIds}/{@code dataSinkIds} carry this pipeline's own source/sink association as
 * the referenced <b>configuration URNs</b>; the trigger's top-level {@code datasources}/{@code
 * datasinks} arrays are the dataset-wide configuration catalog those URNs resolve against. The
 * {@code mappings} catalog ships each referenced Mapping artifact's content ({@code fields}/{@code
 * source}/{@code target}) keyed by its CORE URN, so the callback-free pipeline-engine adapter
 * (NiFi) can build the transform for a {@code mappingRef} without a registry round-trip. The
 * pipeline-engine adapter is the only place engine specifics appear.
 *
 * @param id pipeline identifier
 * @param version pipeline version
 * @param action pipeline action (ADD, DELETE, UPDATE)
 * @param data clean CORE Pipeline graph document (null for DELETE actions)
 * @param dataSourceIds configuration URNs of the datasources assigned to this pipeline (never null;
 *     empty for DELETE actions and for triggers that carry no per-pipeline association)
 * @param dataSinkIds configuration URNs of the datasinks assigned to this pipeline (never null;
 *     empty for DELETE actions and for triggers that carry no per-pipeline association)
 * @param mappings the referenced Mapping artifacts keyed by CORE URN (never null; empty when the
 *     pipeline has no mapping nodes), each value a mapping document ({@code fields}/{@code
 *     source}/{@code target})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataPipeline(
    String id,
    String version,
    String action,
    Map<String, Object> data,
    List<String> dataSourceIds,
    List<String> dataSinkIds,
    Map<String, Object> mappings) {
  public DataPipeline {
    dataSourceIds = dataSourceIds == null ? List.of() : List.copyOf(dataSourceIds);
    dataSinkIds = dataSinkIds == null ? List.of() : List.copyOf(dataSinkIds);
    // Not Map.copyOf: a mapping document is a JSON tree that may carry null values; the
    // unmodifiable LinkedHashMap keeps value semantics without rejecting them.
    mappings =
        mappings == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(mappings));
  }
}

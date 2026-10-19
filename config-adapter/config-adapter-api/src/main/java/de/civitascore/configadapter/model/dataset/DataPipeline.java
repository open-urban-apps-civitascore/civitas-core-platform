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
import java.util.List;
import java.util.Map;

/**
 * A pipeline definition within a dataset. The {@code data} field carries the clean CORE Pipeline
 * document (the {@code nodes}/{@code edges} graph whose nodes reference their source/sink/mapping
 * by CORE URN — {@code sourceRef}/{@code sinkRef}/{@code mappingRef}), opaque to the backend. These
 * refs resolve against the trigger's {@code artifacts}. {@code dataSourceIds}/{@code dataSinkIds}
 * carry this pipeline's own source/sink association as the referenced <b>configuration URNs</b>.
 * The pipeline-engine adapter is the only place engine specifics appear.
 *
 * @param id pipeline identifier
 * @param version pipeline version
 * @param action pipeline action (ADD, DELETE, UPDATE)
 * @param data clean CORE Pipeline graph document (null for DELETE actions)
 * @param dataSourceIds configuration URNs of the datasources assigned to this pipeline (never null;
 *     empty for DELETE actions and for triggers that carry no per-pipeline association)
 * @param dataSinkIds configuration URNs of the datasinks assigned to this pipeline (never null;
 *     empty for DELETE actions and for triggers that carry no per-pipeline association)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataPipeline(
    String id,
    String version,
    String action,
    Map<String, Object> data,
    List<String> dataSourceIds,
    List<String> dataSinkIds) {
  public DataPipeline {
    dataSourceIds = dataSourceIds == null ? List.of() : List.copyOf(dataSourceIds);
    dataSinkIds = dataSinkIds == null ? List.of() : List.copyOf(dataSinkIds);
  }
}

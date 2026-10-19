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
 * Typed representation of a dataset CloudEvent payload. Contains the dataset metadata, its data
 * pipeline definitions, named API endpoints, and the artifacts that the pipelines reference.
 *
 * @param id unique dataset identifier (UUID)
 * @param name human-readable dataset name
 * @param datapipelines engine-neutral pipeline graphs
 * @param namedApis named API endpoints exposed by this dataset (per concepts #1379 and #1383); one
 *     APISIX route per entry
 * @param artifacts the artifacts that the pipelines reference, keyed by the CORE URN exactly as the
 *     referencing artifact carries it: a Data structure as its inlined JSON Schema, every other
 *     artifact as stored (never null; empty when the trigger carries none)
 */
// openDataAccess is intentionally absent: it is no longer part of the saga payload (the
// portal-backend no longer sends it; open-data access is an OPA per-request decision). Any
// openDataAccess field on an old payload is silently ignored via @JsonIgnoreProperties.
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dataset(
    String id,
    String name,
    List<DataPipeline> datapipelines,
    List<NamedApi> namedApis,
    Map<String, Map<String, Object>> artifacts) {
  public Dataset {
    artifacts =
        artifacts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
  }
}

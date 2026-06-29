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

/**
 * Typed representation of a dataset CloudEvent payload. Contains the dataset metadata, its
 * datasource connections, data pipeline definitions, and named API endpoints.
 *
 * @param id unique dataset identifier (UUID)
 * @param name human-readable dataset name
 * @param datasources external data source connections
 * @param datapipelines engine-neutral pipeline graphs
 * @param namedApis named API endpoints exposed by this dataset (per concepts #1379 and #1383); one
 *     APISIX route per entry
 */
// openDataAccess is intentionally absent: it is no longer part of the saga payload (the
// portal-backend no longer sends it; open-data access is an OPA per-request decision). Any
// openDataAccess field on an old payload is silently ignored via @JsonIgnoreProperties.
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dataset(
    String id,
    String name,
    List<Datasource> datasources,
    List<DataPipeline> datapipelines,
    List<NamedApi> namedApis) {}

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
 * datasource connections, and data pipeline definitions.
 *
 * @param id unique dataset identifier (UUID)
 * @param name human-readable dataset name
 * @param openDataAccess whether the dataset is publicly accessible
 * @param datasources external data source connections
 * @param datapipelines Redpanda Connect pipeline definitions
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dataset(
    String id,
    String name,
    boolean openDataAccess,
    List<Datasource> datasources,
    List<DataPipeline> datapipelines) {}

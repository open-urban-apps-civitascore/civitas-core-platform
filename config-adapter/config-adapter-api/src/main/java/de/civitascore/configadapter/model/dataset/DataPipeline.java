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
import java.util.Map;

/**
 * A Redpanda Connect pipeline definition within a dataset. The {@code data} field contains the raw
 * pipeline configuration (input, pipeline, output) that is passed through to Redpanda Connect
 * without interpretation.
 *
 * @param id pipeline identifier
 * @param version pipeline version
 * @param action pipeline action (ADD, DELETE, UPDATE)
 * @param data raw Redpanda Connect pipeline definition (null for DELETE actions)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataPipeline(String id, String version, String action, Map<String, Object> data) {}

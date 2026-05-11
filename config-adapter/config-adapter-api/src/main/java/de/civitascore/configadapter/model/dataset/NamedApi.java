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

/**
 * A named API endpoint exposed by a dataset (per concept #1379, #1383, and #1384, ADR #1362).
 *
 * <p>This record is the saga-contract shape — it carries only the fields config-adapter needs to
 * provision APISIX routes. Human-readable metadata (display name, description) lives on the
 * portal-backend entity and DTOs and is not transmitted across the saga boundary; config-adapter
 * never displays named APIs to end users.
 *
 * <p>{@code slug} is the URL segment in the public route {@code /v1/datasets/{datasetId}/{slug}}.
 * {@code standard} carries the API standard per ADR #1362 (see {@link ApiStandard}).
 *
 * <p>Validation constraints (enforced by portal-backend, not this record):
 *
 * <ul>
 *   <li>{@code slug} matches {@code ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$}, max 32 characters
 *   <li>{@code slug} unique within a dataset
 *   <li>{@code slug} and {@code standard} immutable once the dataset reaches AVAILABLE
 * </ul>
 *
 * @param slug URL slug used in the public route (e.g. "traffic")
 * @param standard API standard (see {@link ApiStandard})
 * @param version optional standard version (e.g. "1.1" for STA); nullable
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamedApi(String slug, ApiStandard standard, String version) {}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dataset;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A named API endpoint exposed by a dataset (per concept #1379, #1383, and #1384, ADR #1362).
 *
 * <p>{@code slug} is the URL segment in the public route {@code /v1/datasets/{datasetId}/{slug}}.
 * {@code name} is a human-readable display label and is not used in any URL. {@code standard}
 * carries the API standard (WFS / WMS / STA / CUSTOM) per ADR #1362; it is stored as a string
 * rather than a Java enum so the vocabulary can grow and {@code CUSTOM} can stay free-form.
 *
 * <p>Validation constraints (enforced by portal-backend, not this record):
 *
 * <ul>
 *   <li>{@code slug} matches {@code ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$}, max 32 characters
 *   <li>{@code slug} unique within a dataset
 *   <li>{@code slug} and {@code standard} immutable once the dataset reaches AVAILABLE
 *   <li>{@code standard} must be one of the known values, but the record accepts any string for
 *       forward compatibility
 * </ul>
 *
 * @param name human-readable display label (e.g. "Traffic Sensor Readings")
 * @param slug URL slug used in the public route (e.g. "traffic")
 * @param standard API standard: "WFS", "WMS", "STA", or "CUSTOM"
 * @param version optional standard version (e.g. "1.1" for STA); nullable
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamedApi(String name, String slug, String standard, String version) {}

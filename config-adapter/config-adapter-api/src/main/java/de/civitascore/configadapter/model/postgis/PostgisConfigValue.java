/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.postgis;

import de.civitascore.configadapter.model.ConfigValue;

/**
 * Sealed interface for PostGIS configuration values. Each implementation represents a specific
 * PostGIS resource type with concrete, type-safe fields.
 *
 * <p>Supported PostGIS resource types:
 *
 * <ul>
 *   <li>{@link TableConfig} - Table configuration including columns, geometry columns, primary key,
 *       and indexes
 *   <li>{@link SchemaConfig} - Schema configuration (name, optional owner, drop behaviour)
 *   <li>{@link DbRoleConfig} - Role/user configuration including login flag, password, and embedded
 *       schema-level grants
 * </ul>
 *
 * <p>Type discrimination is handled at the {@link ConfigValue} level using the {@code resourceType}
 * property in JSON.
 */
public sealed interface PostgisConfigValue extends ConfigValue
    permits TableConfig, SchemaConfig, DbRoleConfig {

  String POSTGIS_RESULT_TYPE = "de.civitascore.data.table.processing.result";
}

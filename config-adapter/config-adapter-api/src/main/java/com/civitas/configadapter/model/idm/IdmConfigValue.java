/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.idm;

import com.civitas.configadapter.model.ConfigValue;

/**
 * Sealed interface for IDM (Identity Management) configuration values. Each implementation
 * represents a specific IDM resource type with concrete, type-safe fields.
 *
 * <p>Supported IDM resource types:
 *
 * <ul>
 *   <li>{@link RealmConfig} - Realm configuration (authentication domain)
 *   <li>{@link UserConfig} - User configuration (identity with credentials and roles)
 *   <li>{@link ClientConfig} - OAuth2/OIDC client application configuration
 *   <li>{@link RoleConfig} - Role configuration (authorization role)
 * </ul>
 *
 * <p>This sealed interface ensures type safety and makes it clear to developers which fields are
 * available for each resource type. IDE auto-completion will show all available fields when
 * creating configuration objects.
 *
 * <p>Type discrimination is handled at the {@link ConfigValue} level using the "resourceType"
 * property in JSON.
 */
public sealed interface IdmConfigValue extends ConfigValue
    permits ClientConfig, GroupConfig, RealmConfig, RoleConfig, UserConfig {}

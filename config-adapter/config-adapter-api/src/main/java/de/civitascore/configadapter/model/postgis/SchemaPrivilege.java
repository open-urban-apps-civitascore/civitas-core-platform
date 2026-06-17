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

/**
 * Privileges grantable on a PostgreSQL <em>schema</em>.
 *
 * <ul>
 *   <li>{@link #USAGE} — allows looking up objects within the schema
 *   <li>{@link #CREATE} — allows creating new objects within the schema
 *   <li>{@link #ALL} — shorthand for all schema privileges ({@code USAGE} + {@code CREATE})
 * </ul>
 */
public enum SchemaPrivilege {
  USAGE,
  CREATE,
  ALL
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import de.civitascore.configadapter.nifi.flow.SinkType;

/**
 * The resolved, typed sink configuration of one pipeline — one variant per {@link SinkType}, each
 * carrying only its own fields with its own invariants. The sink's data-structure model is
 * intentionally NOT carried here: deriving the table schema from it is the PostGIS adapter's job
 * (it creates the typed table via DDL); the NiFi pipeline only writes records, and
 * PutDatabaseRecord coerces them to the existing column types.
 */
public sealed interface SinkSpec permits PostgisSinkSpec, FrostSinkSpec {

  SinkType type();
}

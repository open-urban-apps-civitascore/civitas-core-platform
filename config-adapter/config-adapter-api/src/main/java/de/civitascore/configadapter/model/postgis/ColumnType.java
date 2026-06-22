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
 * Generic, dialect-agnostic SQL column types supported by the config adapter.
 *
 * <p>Each {@link de.civitascore.configadapter.postgis.dialect.SqlDialect SqlDialect} implementation
 * is responsible for translating these logical types into its concrete SQL syntax. Geometry types
 * are intentionally <em>not</em> represented here — they live in {@link GeometryColumnConfig} so
 * non-spatial flavors can ignore them.
 */
public enum ColumnType {
  SMALLINT,
  INTEGER,
  BIGINT,
  NUMERIC,
  REAL,
  DOUBLE_PRECISION,
  BOOLEAN,
  VARCHAR,
  TEXT,
  UUID,
  DATE,
  TIME,
  TIMESTAMP,
  TIMESTAMPTZ,
  JSONB,
  BYTEA
}

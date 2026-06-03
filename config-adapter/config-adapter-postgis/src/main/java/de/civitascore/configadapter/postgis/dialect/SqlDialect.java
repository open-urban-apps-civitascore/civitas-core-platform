/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis.dialect;

import de.civitascore.configadapter.model.postgis.ColumnConfig;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import java.sql.SQLException;
import java.util.List;

/**
 * Flavor-specific seam for SQL DDL generation and JDBC error classification.
 *
 * <p>The adapter is dialect-agnostic; everything that differs between SQL flavors lives behind this
 * interface. When a second flavor (MySQL, Oracle, …) is added, the dialect classes move to a shared
 * {@code config-adapter-sql-base} module and the PostGIS-specific {@link PostgisDialect} stays
 * here.
 */
public interface SqlDialect {

  /** Quote an identifier for safe use in SQL (handles reserved words and case sensitivity). */
  String quoteIdent(String ident);

  /** Render a non-spatial column declaration (name + type + nullability + default). */
  String renderColumn(ColumnConfig column);

  /** Render a PostGIS geometry column declaration. */
  String renderGeometryColumn(GeometryColumnConfig column);

  /** Render an index name when the {@link IndexConfig#name()} was not specified. */
  String generatedIndexName(TableConfig table, IndexConfig index);

  /**
   * Build the ordered list of DDL statements needed to create a table. Statements are intended to
   * be executed sequentially in a single transaction.
   */
  List<String> createTable(TableConfig table);

  /** Build the DDL statements to drop a table. */
  List<String> dropTable(String schema, String name);

  /** Build the DDL to create a schema, optionally with an {@code AUTHORIZATION} owner. */
  List<String> createSchema(SchemaConfig schema);

  /**
   * Build the DDL to change a schema's owner. Returns an empty list when no owner is set (nothing
   * to update).
   */
  List<String> alterSchemaOwner(SchemaConfig schema);

  /** Build the DDL to drop a schema, using {@code CASCADE} or {@code RESTRICT} per the config. */
  List<String> dropSchema(SchemaConfig schema);

  /**
   * Build the DDL to create a role. Attributes (login, superuser, …) come from {@code role}; {@code
   * decryptedPassword}, when non-null/non-blank, is embedded as a quoted literal.
   */
  List<String> createRole(DbRoleConfig role, String decryptedPassword);

  /** Build the DDL to alter a role's attributes (and password, when supplied). */
  List<String> alterRole(DbRoleConfig role, String decryptedPassword);

  /** Build the DDL to drop a role. */
  List<String> dropRole(DbRoleConfig role);

  /** Build a single {@code GRANT ... ON SCHEMA ... TO role} statement. */
  String grantOnSchema(
      String roleName, String schema, List<SchemaPrivilege> privileges, boolean withGrantOption);

  /** Build a single {@code REVOKE ... ON SCHEMA ... FROM role} statement. */
  String revokeOnSchema(String roleName, String schema, List<SchemaPrivilege> privileges);

  /**
   * Returns a parameterised query (one {@code ?} bind for the role name) that yields the role's
   * current explicit schema privileges as rows of {@code (schema_name, privilege_type)}. Used to
   * reconcile embedded grants on UPDATE.
   */
  String readSchemaGrantsQuery();

  /**
   * Returns true if the SQL exception indicates the target object already exists (duplicate table,
   * schema, index, or role) — used to absorb CREATE conflicts as idempotent successes.
   */
  boolean isDuplicate(SQLException e);

  /**
   * Returns true if the SQL exception indicates the target object does not exist (undefined table,
   * schema, or role) — used to absorb DROP conflicts as idempotent successes.
   */
  boolean isMissing(SQLException e);

  /**
   * Returns true if the SQL exception indicates a transient connectivity problem (connection
   * failure, exclusion class {@code 08*}) — used to classify the error as retryable.
   */
  boolean isConnectivity(SQLException e);
}

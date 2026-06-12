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
import de.civitascore.configadapter.model.postgis.ColumnType;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.GeometryColumnConfig;
import de.civitascore.configadapter.model.postgis.GeometryType;
import de.civitascore.configadapter.model.postgis.IndexConfig;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL + PostGIS implementation of {@link SqlDialect}.
 *
 * <p>SQLState handling follows the standard PostgreSQL codes:
 *
 * <ul>
 *   <li>{@code 42P07} duplicate_table / duplicate_object (index)
 *   <li>{@code 42P06} duplicate_schema
 *   <li>{@code 42P01} undefined_table
 *   <li>{@code 3F000} invalid_schema_name
 *   <li>{@code 08*} connection exception (retryable)
 * </ul>
 */
public final class PostgisDialect implements SqlDialect {

  private static final String SQLSTATE_DUPLICATE_TABLE = "42P07";
  private static final String SQLSTATE_DUPLICATE_SCHEMA = "42P06";
  private static final String SQLSTATE_DUPLICATE_OBJECT = "42710";
  private static final String SQLSTATE_UNDEFINED_TABLE = "42P01";
  private static final String SQLSTATE_UNDEFINED_OBJECT = "42704";
  private static final String SQLSTATE_INVALID_SCHEMA = "3F000";
  private static final String SQLSTATE_CLASS_CONNECTION = "08";

  @Override
  public String quoteIdent(String ident) {
    if (ident == null) {
      throw new IllegalArgumentException("identifier must not be null");
    }
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  @Override
  public String renderColumn(ColumnConfig column) {
    StringBuilder sb = new StringBuilder();
    sb.append(quoteIdent(column.name())).append(' ').append(renderType(column));
    if (!column.isNullable()) {
      sb.append(" NOT NULL");
    }
    return sb.toString();
  }

  @Override
  public String renderGeometryColumn(GeometryColumnConfig column) {
    StringBuilder sb = new StringBuilder();
    sb.append(quoteIdent(column.name())).append(' ').append(renderGeometryType(column));
    if (!column.isNullable()) {
      sb.append(" NOT NULL");
    }
    return sb.toString();
  }

  @Override
  public String generatedIndexName(TableConfig table, IndexConfig index) {
    String columnsPart = String.join("_", index.columns());
    return "idx_" + table.getName() + "_" + columnsPart;
  }

  @Override
  public List<String> createTable(TableConfig table) {
    if (table.getName() == null || table.getName().isBlank()) {
      throw new IllegalArgumentException("table name must not be blank");
    }

    List<String> statements = new ArrayList<>();
    if (table.getSchema() != null && !table.getSchema().isBlank()) {
      statements.add("CREATE SCHEMA " + quoteIdent(table.getSchema()));
    }
    statements.add(buildCreateTableStatement(table));
    for (IndexConfig index : table.getIndexes()) {
      statements.add(buildCreateIndexStatement(table, index));
    }
    return List.copyOf(statements);
  }

  @Override
  public List<String> dropTable(String schema, String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("table name must not be blank");
    }
    return List.of("DROP TABLE " + qualified(schema, name));
  }

  @Override
  public List<String> createSchema(SchemaConfig schema) {
    requireName(schema.getName(), "schema name");
    String sql = "CREATE SCHEMA " + quoteIdent(schema.getName());
    if (schema.getOwner() != null && !schema.getOwner().isBlank()) {
      sql += " AUTHORIZATION " + quoteIdent(schema.getOwner());
    }
    return List.of(sql);
  }

  @Override
  public List<String> alterSchemaOwner(SchemaConfig schema) {
    requireName(schema.getName(), "schema name");
    if (schema.getOwner() == null || schema.getOwner().isBlank()) {
      return List.of();
    }
    return List.of(
        "ALTER SCHEMA "
            + quoteIdent(schema.getName())
            + " OWNER TO "
            + quoteIdent(schema.getOwner()));
  }

  @Override
  public List<String> dropSchema(SchemaConfig schema) {
    requireName(schema.getName(), "schema name");
    String mode = schema.isCascade() ? "CASCADE" : "RESTRICT";
    return List.of("DROP SCHEMA " + quoteIdent(schema.getName()) + " " + mode);
  }

  @Override
  public List<String> createRole(DbRoleConfig role, String decryptedPassword) {
    requireName(role.getName(), "role name");
    return List.of(
        "CREATE ROLE " + quoteIdent(role.getName()) + roleOptionsClause(role, decryptedPassword));
  }

  @Override
  public List<String> alterRole(DbRoleConfig role, String decryptedPassword) {
    requireName(role.getName(), "role name");
    return List.of(
        "ALTER ROLE " + quoteIdent(role.getName()) + roleOptionsClause(role, decryptedPassword));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Emits {@code DROP OWNED BY} first — PostgreSQL refuses {@code DROP ROLE} while the role
   * still holds privileges (SQLState 2BP01), and {@code DROP OWNED BY} both revokes them and drops
   * objects the role owns (the read roles provisioned by this adapter own nothing; the sink table
   * is dropped explicitly by the same step). This is the role-removal procedure documented by
   * PostgreSQL.
   */
  @Override
  public List<String> dropRole(DbRoleConfig role) {
    requireName(role.getName(), "role name");
    return List.of(
        "DROP OWNED BY " + quoteIdent(role.getName()),
        "DROP ROLE " + quoteIdent(role.getName()));
  }

  @Override
  public String grantOnSchema(
      String roleName, String schema, List<SchemaPrivilege> privileges, boolean withGrantOption) {
    String sql =
        "GRANT "
            + renderPrivileges(privileges)
            + " ON SCHEMA "
            + quoteIdent(schema)
            + " TO "
            + quoteIdent(roleName);
    return withGrantOption ? sql + " WITH GRANT OPTION" : sql;
  }

  @Override
  public String revokeOnSchema(String roleName, String schema, List<SchemaPrivilege> privileges) {
    return "REVOKE "
        + renderPrivileges(privileges)
        + " ON SCHEMA "
        + quoteIdent(schema)
        + " FROM "
        + quoteIdent(roleName);
  }

  @Override
  public String revokeGrantOptionOnSchema(
      String roleName, String schema, List<SchemaPrivilege> privileges) {
    return "REVOKE GRANT OPTION FOR "
        + renderPrivileges(privileges)
        + " ON SCHEMA "
        + quoteIdent(schema)
        + " FROM "
        + quoteIdent(roleName);
  }

  @Override
  public String readSchemaGrantsQuery() {
    return "SELECT n.nspname AS schema_name, acl.privilege_type AS privilege_type, "
        + "acl.is_grantable AS is_grantable "
        + "FROM pg_namespace n "
        + "CROSS JOIN LATERAL aclexplode(n.nspacl) AS acl "
        + "JOIN pg_roles r ON r.oid = acl.grantee "
        + "WHERE r.rolname = ?";
  }

  @Override
  public boolean isDuplicate(SQLException e) {
    String state = e == null ? null : e.getSQLState();
    return SQLSTATE_DUPLICATE_TABLE.equals(state)
        || SQLSTATE_DUPLICATE_SCHEMA.equals(state)
        || SQLSTATE_DUPLICATE_OBJECT.equals(state);
  }

  @Override
  public boolean isMissing(SQLException e) {
    String state = e == null ? null : e.getSQLState();
    return SQLSTATE_UNDEFINED_TABLE.equals(state)
        || SQLSTATE_INVALID_SCHEMA.equals(state)
        || SQLSTATE_UNDEFINED_OBJECT.equals(state);
  }

  @Override
  public boolean isConnectivity(SQLException e) {
    String state = e == null ? null : e.getSQLState();
    return state != null && state.startsWith(SQLSTATE_CLASS_CONNECTION);
  }

  private String buildCreateTableStatement(TableConfig table) {
    List<String> parts = new ArrayList<>();
    for (ColumnConfig column : table.getColumns()) {
      parts.add(renderColumn(column));
    }
    for (GeometryColumnConfig column : table.getGeometryColumns()) {
      parts.add(renderGeometryColumn(column));
    }
    if (!table.getPrimaryKey().isEmpty()) {
      String pkColumns =
          table.getPrimaryKey().stream().map(this::quoteIdent).collect(Collectors.joining(", "));
      parts.add("PRIMARY KEY (" + pkColumns + ")");
    }
    return "CREATE TABLE "
        + qualified(table.getSchema(), table.getName())
        + " ("
        + String.join(", ", parts)
        + ")";
  }

  private String buildCreateIndexStatement(TableConfig table, IndexConfig index) {
    String indexName =
        index.name() == null || index.name().isBlank()
            ? generatedIndexName(table, index)
            : index.name();
    String columns =
        index.columns().stream().map(this::quoteIdent).collect(Collectors.joining(", "));
    String unique = index.isUnique() ? "UNIQUE " : "";
    return "CREATE "
        + unique
        + "INDEX "
        + quoteIdent(indexName)
        + " ON "
        + qualified(table.getSchema(), table.getName())
        + " USING "
        + index.effectiveMethod().name()
        + " ("
        + columns
        + ")";
  }

  private void requireName(String name, String what) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException(what + " must not be blank");
    }
  }

  /** Renders the trailing {@code WITH LOGIN PASSWORD '…' …} option list for CREATE/ALTER ROLE. */
  private String roleOptionsClause(DbRoleConfig role, String decryptedPassword) {
    List<String> options = new ArrayList<>();
    options.add(role.isCanLogin() ? "LOGIN" : "NOLOGIN");
    if (role.getSuperuser() != null) {
      options.add(role.getSuperuser() ? "SUPERUSER" : "NOSUPERUSER");
    }
    if (role.getCreateDb() != null) {
      options.add(role.getCreateDb() ? "CREATEDB" : "NOCREATEDB");
    }
    if (role.getCreateRole() != null) {
      options.add(role.getCreateRole() ? "CREATEROLE" : "NOCREATEROLE");
    }
    if (role.getInherit() != null) {
      options.add(role.getInherit() ? "INHERIT" : "NOINHERIT");
    }
    if (decryptedPassword != null && !decryptedPassword.isBlank()) {
      options.add("PASSWORD " + quoteLiteral(decryptedPassword));
    }
    return " WITH " + String.join(" ", options);
  }

  private String renderPrivileges(List<SchemaPrivilege> privileges) {
    if (privileges == null || privileges.isEmpty()) {
      throw new IllegalArgumentException("at least one privilege is required");
    }
    return privileges.stream().map(Enum::name).collect(Collectors.joining(", "));
  }

  /** Quotes a string literal, doubling embedded single quotes. */
  private String quoteLiteral(String value) {
    return "'" + value.replace("'", "''") + "'";
  }

  private String qualified(String schema, String name) {
    if (schema == null || schema.isBlank()) {
      return quoteIdent(name);
    }
    return quoteIdent(schema) + "." + quoteIdent(name);
  }

  private String renderType(ColumnConfig column) {
    ColumnType type = column.type();
    if (type == null) {
      throw new IllegalArgumentException(
          "column type must not be null for column " + column.name());
    }
    return switch (type) {
      case SMALLINT -> "SMALLINT";
      case INTEGER -> "INTEGER";
      case BIGINT -> "BIGINT";
      case NUMERIC -> renderNumeric(column);
      case REAL -> "REAL";
      case DOUBLE_PRECISION -> "DOUBLE PRECISION";
      case BOOLEAN -> "BOOLEAN";
      case VARCHAR -> column.length() == null ? "VARCHAR" : "VARCHAR(" + column.length() + ")";
      case TEXT -> "TEXT";
      case UUID -> "UUID";
      case DATE -> "DATE";
      case TIME -> "TIME";
      case TIMESTAMP -> "TIMESTAMP";
      case TIMESTAMPTZ -> "TIMESTAMPTZ";
      case JSONB -> "JSONB";
      case BYTEA -> "BYTEA";
    };
  }

  private String renderNumeric(ColumnConfig column) {
    if (column.precision() == null) {
      return "NUMERIC";
    }
    if (column.scale() == null) {
      return "NUMERIC(" + column.precision() + ")";
    }
    return "NUMERIC(" + column.precision() + ", " + column.scale() + ")";
  }

  private String renderGeometryType(GeometryColumnConfig column) {
    GeometryType type =
        column.geometryType() == null ? GeometryType.GEOMETRY : column.geometryType();
    String suffix =
        column.effectiveDimension() == 3 ? "Z" : column.effectiveDimension() == 4 ? "ZM" : "";
    if (column.srid() == null) {
      return "GEOMETRY(" + type.name() + suffix + ")";
    }
    return "GEOMETRY(" + type.name() + suffix + ", " + column.srid() + ")";
  }
}

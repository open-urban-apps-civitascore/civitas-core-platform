/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import de.civitascore.configadapter.adapter.AbstractConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.SqlDdlSupport.StatementPlanner;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostGIS / PostgreSQL adapter that processes configuration messages and manages SQL <b>tables</b>,
 * <b>schemas</b>, and <b>roles</b> (including schema-level grants) via DDL.
 *
 * <p>The payload type ({@link TableConfig}, {@link SchemaConfig}, {@link DbRoleConfig}) selects the
 * resource family; the {@link Operation} selects CREATE / UPDATE / DELETE within it.
 *
 * <p>Role passwords may arrive as encrypted {@code ENC(...)} values and are decrypted with {@code
 * CIVITAS_MASTER_KEY} (see {@link CredentialDecryptor}); plaintext passwords are never logged.
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>SQLState {@code 42P07} / {@code 42P06} / {@code 42710} on CREATE → success (idempotent:
 *       object already exists)
 *   <li>SQLState {@code 42P01} / {@code 3F000} / {@code 42704} on DROP → success (idempotent:
 *       object already gone)
 *   <li>SQLState class {@code 08*} → {@link RetryableAdapterException} ({@code
 *       POSTGIS_CONNECTION_ERROR})
 *   <li>Other SQLExceptions → {@link FatalAdapterException} ({@code POSTGIS_DDL_ERROR})
 * </ul>
 */
public class PostgisAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(PostgisAdapter.class);

  public static final String ADAPTER_NAME = "postgis";

  /** Credential context for per-credential key isolation when decrypting role passwords. */
  public static final String ROLE_CREDENTIAL_CONTEXT = "portal-backend:sql-role";

  private final SqlDdlSupport ddl = new SqlDdlSupport();

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);
    String jdbcUrl = ddl.initialize(config);

    logger.info(
        "PostGIS adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(SqlDdlSupport.sanitizeJdbcUrl(jdbcUrl)));
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(getSubscribedTopics().toString()));
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    ddl.setConnectionProvider(connectionProvider);
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    ddl.setDialect(dialect);
  }

  /** Releases the JDBC connection pool and zeroes the key. Safe to call multiple times. */
  public void close() {
    ddl.close();
  }

  @Override
  protected String getResultType() {
    return PostgisConfigValue.POSTGIS_RESULT_TYPE;
  }

  @Override
  protected void doProcessConfigEvent(String topic, ConfigEvent event)
      throws FatalAdapterException, RetryableAdapterException {
    Operation operation = event.payload().operation();
    String targetResource = event.payload().targetResource();
    String targetComponent = event.payload().targetComponent();

    logger.info(
        "Processing config event - Topic: {}, Operation: {}, TargetComponent: {}, TargetResource: {}",
        Encode.forJava(topic),
        operation,
        Encode.forJava(targetComponent),
        Encode.forJava(targetResource));

    ConfigValue value = event.payload().config().value();
    if (value instanceof TableConfig table) {
      handleTable(event, table, operation);
    } else if (value instanceof SchemaConfig schema) {
      handleSchema(event, schema, operation);
    } else if (value instanceof DbRoleConfig role) {
      handleRole(event, role, operation);
    } else {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "Expected a PostGIS table, schema, or role payload, got: "
              + (value == null ? "null" : value.getClass().getSimpleName()));
    }
  }

  // ─── Tables ────────────────────────────────────────────────────────────────

  private void handleTable(ConfigEvent event, TableConfig table, Operation operation)
      throws FatalAdapterException, RetryableAdapterException {
    switch (operation) {
      case CREATE -> {
        executeDdl(AdapterOperation.SQL_TABLE_CREATE, dialect().createTable(table), true, false);
        publishSuccess(event, "Table " + table.qualifiedName() + " created", table.qualifiedName());
      }
      case DELETE -> {
        executeDdl(
            AdapterOperation.SQL_TABLE_DELETE,
            dialect().dropTable(table.getSchema(), table.getName()),
            false,
            true);
        publishSuccess(event, "Table " + table.qualifiedName() + " deleted", table.qualifiedName());
      }
      default -> throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
    }
  }

  // ─── Schemas ───────────────────────────────────────────────────────────────

  private void handleSchema(ConfigEvent event, SchemaConfig schema, Operation operation)
      throws FatalAdapterException, RetryableAdapterException {
    switch (operation) {
      case CREATE -> {
        executeDdl(AdapterOperation.SQL_SCHEMA_CREATE, dialect().createSchema(schema), true, false);
        publishSuccess(event, "Schema " + schema.getName() + " created", schema.getName());
      }
      case UPDATE -> {
        executeDdl(
            AdapterOperation.SQL_SCHEMA_UPDATE, dialect().alterSchemaOwner(schema), false, false);
        publishSuccess(event, "Schema " + schema.getName() + " updated", schema.getName());
      }
      case DELETE -> {
        executeDdl(AdapterOperation.SQL_SCHEMA_DELETE, dialect().dropSchema(schema), false, true);
        publishSuccess(event, "Schema " + schema.getName() + " deleted", schema.getName());
      }
      default -> throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
    }
  }

  // ─── Roles ─────────────────────────────────────────────────────────────────

  private void handleRole(ConfigEvent event, DbRoleConfig role, Operation operation)
      throws FatalAdapterException, RetryableAdapterException {
    switch (operation) {
      case CREATE -> {
        String password = resolvePassword(role.getPassword());
        List<String> statements = new ArrayList<>(dialect().createRole(role, password));
        ddl.appendGrantStatements(
            statements, role.getName(), GrantReconciler.reconcile(role.getGrants(), Map.of()));
        executeDdl(AdapterOperation.SQL_ROLE_CREATE, statements, true, false);
        publishSuccess(event, "Role " + role.getName() + " created", role.getName());
      }
      case UPDATE -> {
        String password = resolvePassword(role.getPassword());
        executeDdl(
            AdapterOperation.SQL_ROLE_UPDATE,
            connection -> planRoleUpdate(connection, role, password),
            false,
            false);
        publishSuccess(event, "Role " + role.getName() + " updated", role.getName());
      }
      case DELETE -> {
        executeDdl(AdapterOperation.SQL_ROLE_DELETE, dialect().dropRole(role), false, true);
        publishSuccess(event, "Role " + role.getName() + " deleted", role.getName());
      }
      default -> throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
    }
  }

  /** Reads the role's current schema grants and computes ALTER + reconcile statements. */
  private List<String> planRoleUpdate(Connection connection, DbRoleConfig role, String password)
      throws SQLException {
    Map<String, Map<SchemaPrivilege, Boolean>> current =
        readSchemaGrants(connection, role.getName());
    GrantReconcilePlan plan = GrantReconciler.reconcile(role.getGrants(), current);
    List<String> statements = new ArrayList<>(dialect().alterRole(role, password));
    ddl.appendGrantStatements(statements, role.getName(), plan);
    return statements;
  }

  /** Reads the role's current schema privileges, keyed by schema, with their grant-option state. */
  private Map<String, Map<SchemaPrivilege, Boolean>> readSchemaGrants(
      Connection connection, String roleName) throws SQLException {
    Map<String, Map<SchemaPrivilege, Boolean>> grantsBySchema = new LinkedHashMap<>();
    try (PreparedStatement ps = connection.prepareStatement(dialect().readSchemaGrantsQuery())) {
      ps.setString(1, roleName);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          SchemaPrivilege privilege = mapPrivilege(rs.getString("privilege_type"));
          if (privilege != null) {
            grantsBySchema
                .computeIfAbsent(
                    rs.getString("schema_name"), s -> new EnumMap<>(SchemaPrivilege.class))
                .put(privilege, rs.getBoolean("is_grantable"));
          }
        }
      }
    }
    return grantsBySchema;
  }

  private SchemaPrivilege mapPrivilege(String postgresPrivilege) {
    return switch (postgresPrivilege) {
      case "USAGE" -> SchemaPrivilege.USAGE;
      case "CREATE" -> SchemaPrivilege.CREATE;
      default -> null;
    };
  }

  private String resolvePassword(String rawPassword) throws FatalAdapterException {
    try {
      return ddl.resolvePassword(rawPassword);
    } catch (IllegalStateException e) {
      if (e.getCause() != null) {
        throw new FatalAdapterException(
            AdapterErrorCode.POSTGIS_ERROR, e.getCause(), e.getMessage());
      }
      throw new FatalAdapterException(AdapterErrorCode.POSTGIS_ERROR, e.getMessage());
    }
  }

  // ─── DDL execution ───────────────────────────────────────────────────────

  private void executeDdl(
      AdapterOperation operation,
      List<String> statements,
      boolean absorbDuplicate,
      boolean absorbMissing)
      throws FatalAdapterException, RetryableAdapterException {
    executeDdl(operation, connection -> statements, absorbDuplicate, absorbMissing);
  }

  private void executeDdl(
      AdapterOperation operation,
      StatementPlanner planner,
      boolean absorbDuplicate,
      boolean absorbMissing)
      throws FatalAdapterException, RetryableAdapterException {
    try {
      ddl.runDdl(planner, absorbDuplicate, absorbMissing);
    } catch (SQLException e) {
      if (dialect().isConnectivity(e)) {
        throw new RetryableAdapterException(
            AdapterErrorCode.POSTGIS_CONNECTION_ERROR,
            e,
            ADAPTER_NAME,
            SqlDdlSupport.redact(e.getMessage()));
      }
      throw new FatalAdapterException(
          AdapterErrorCode.POSTGIS_DDL_ERROR,
          e,
          operation.getDescription() + " failed: " + SqlDdlSupport.redact(e.getMessage()));
    }
  }

  private SqlDialect dialect() {
    return ddl.dialect();
  }

  private void publishSuccess(ConfigEvent event, String message, String resourceId)
      throws FatalAdapterException, RetryableAdapterException {
    logger.info(Encode.forJava(message));
    publishSuccessResult(event, message, resourceId);
  }
}

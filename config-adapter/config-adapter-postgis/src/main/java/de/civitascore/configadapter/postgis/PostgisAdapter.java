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
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
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
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.SchemaPrivilege;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.SchemaRevoke;
import de.civitascore.configadapter.postgis.ddl.TableDdlBuilder;
import de.civitascore.configadapter.postgis.dialect.PostgisDialect;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
  public static final String ROLE_CREDENTIAL_CONTEXT = "portal-backend:postgis-role";

  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private static final String JDBC_URL_KEY = "jdbc.url";
  private static final String JDBC_USER_KEY = "jdbc.user";
  private static final String JDBC_PASSWORD_KEY = "jdbc.password";
  private static final String JDBC_MAX_POOL_SIZE_KEY = "jdbc.maxPoolSize";
  private static final String JDBC_CONNECTION_TIMEOUT_MS_KEY = "jdbc.connectionTimeoutMs";

  private static final String DEFAULT_MAX_POOL_SIZE = "5";
  private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

  /** Masks {@code PASSWORD '…'} literals so plaintext never reaches logs or error messages. */
  private static final Pattern PASSWORD_LITERAL =
      Pattern.compile("(?i)(PASSWORD\\s+)'(?:[^']|'')*'");

  private ConnectionProvider connectionProvider;
  private SqlDialect dialect;
  private TableDdlBuilder ddlBuilder;
  private byte[] stretchedKey = new byte[0];

  /** Read within the transaction to build the statement list, possibly using the connection. */
  @FunctionalInterface
  private interface StatementPlanner {
    List<String> plan(Connection connection) throws SQLException;
  }

  @Override
  public String getName() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    super.initialize(config);

    String jdbcUrl = requireProperty(JDBC_URL_KEY);
    String user = requireProperty(JDBC_USER_KEY);
    String password = getAdapterProperty(JDBC_PASSWORD_KEY, "");
    int maxPoolSize =
        Integer.parseInt(getAdapterProperty(JDBC_MAX_POOL_SIZE_KEY, DEFAULT_MAX_POOL_SIZE));
    long connectionTimeoutMs =
        Long.parseLong(
            getAdapterProperty(JDBC_CONNECTION_TIMEOUT_MS_KEY, DEFAULT_CONNECTION_TIMEOUT_MS));

    if (this.connectionProvider == null) {
      this.connectionProvider =
          new ConnectionProvider(jdbcUrl, user, password, maxPoolSize, connectionTimeoutMs);
    }
    if (this.dialect == null) {
      this.dialect = new PostgisDialect();
    }
    this.ddlBuilder = new TableDdlBuilder(this.dialect);

    this.stretchedKey = CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    if (this.stretchedKey.length == 0) {
      logger.warn("{} not set — encrypted role passwords cannot be decrypted", MASTER_KEY_ENV);
    }

    logger.info(
        "PostGIS adapter '{}' initialized for: {}",
        Encode.forJava(getName()),
        Encode.forJava(jdbcUrl));
    logger.info(
        "Subscribed to {} Kafka topics: {}",
        getSubscribedTopics().size(),
        Encode.forJava(getSubscribedTopics().toString()));
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    this.dialect = dialect;
  }

  /** Releases the JDBC connection pool and zeroes the key. Safe to call multiple times. */
  public void close() {
    if (connectionProvider != null) {
      connectionProvider.close();
      connectionProvider = null;
    }
    if (stretchedKey != null) {
      Arrays.fill(stretchedKey, (byte) 0);
    }
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
        executeDdl(AdapterOperation.SQL_TABLE_CREATE, ddlBuilder.buildCreate(table), true, false);
        publishSuccess(event, "Table " + table.qualifiedName() + " created", table.qualifiedName());
      }
      case DELETE -> {
        executeDdl(AdapterOperation.SQL_TABLE_DELETE, ddlBuilder.buildDrop(table), false, true);
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
        executeDdl(AdapterOperation.SQL_SCHEMA_CREATE, dialect.createSchema(schema), true, false);
        publishSuccess(event, "Schema " + schema.getName() + " created", schema.getName());
      }
      case UPDATE -> {
        executeDdl(
            AdapterOperation.SQL_SCHEMA_UPDATE, dialect.alterSchemaOwner(schema), false, false);
        publishSuccess(event, "Schema " + schema.getName() + " updated", schema.getName());
      }
      case DELETE -> {
        executeDdl(AdapterOperation.SQL_SCHEMA_DELETE, dialect.dropSchema(schema), false, true);
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
        List<String> statements = new ArrayList<>(dialect.createRole(role, password));
        appendGrantStatements(
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
        executeDdl(AdapterOperation.SQL_ROLE_DELETE, dialect.dropRole(role), false, true);
        publishSuccess(event, "Role " + role.getName() + " deleted", role.getName());
      }
      default -> throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
    }
  }

  /** Reads the role's current schema grants and computes ALTER + reconcile statements. */
  private List<String> planRoleUpdate(Connection connection, DbRoleConfig role, String password)
      throws SQLException {
    Map<String, Set<SchemaPrivilege>> current = readSchemaGrants(connection, role.getName());
    GrantReconcilePlan plan = GrantReconciler.reconcile(role.getGrants(), current);
    List<String> statements = new ArrayList<>(dialect.alterRole(role, password));
    appendGrantStatements(statements, role.getName(), plan);
    return statements;
  }

  private void appendGrantStatements(
      List<String> statements, String roleName, GrantReconcilePlan plan) {
    for (SchemaGrant grant : plan.toGrant()) {
      statements.add(
          dialect.grantOnSchema(
              roleName, grant.schema(), grant.effectivePrivileges(), grant.isWithGrantOption()));
    }
    for (SchemaRevoke revoke : plan.toRevoke()) {
      statements.add(dialect.revokeOnSchema(roleName, revoke.schema(), revoke.privileges()));
    }
  }

  private Map<String, Set<SchemaPrivilege>> readSchemaGrants(Connection connection, String roleName)
      throws SQLException {
    Map<String, Set<SchemaPrivilege>> grantsBySchema = new LinkedHashMap<>();
    try (PreparedStatement ps = connection.prepareStatement(dialect.readSchemaGrantsQuery())) {
      ps.setString(1, roleName);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          SchemaPrivilege privilege = mapPrivilege(rs.getString("privilege_type"));
          if (privilege != null) {
            grantsBySchema
                .computeIfAbsent(
                    rs.getString("schema_name"), s -> EnumSet.noneOf(SchemaPrivilege.class))
                .add(privilege);
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
    if (rawPassword == null || rawPassword.isBlank()) {
      return null;
    }
    Map<String, Object> wrapper = Map.of("password", rawPassword);
    if (CredentialDecryptor.containsEncryptedValues(wrapper) && stretchedKey.length == 0) {
      throw new FatalAdapterException(
          AdapterErrorCode.POSTGIS_ERROR,
          MASTER_KEY_ENV + " is required to decrypt the encrypted role password");
    }
    try {
      Map<String, Object> decrypted =
          CredentialDecryptor.decryptMapValues(wrapper, stretchedKey, ROLE_CREDENTIAL_CONTEXT);
      return (String) decrypted.get("password");
    } catch (GeneralSecurityException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.POSTGIS_ERROR, e, "Failed to decrypt role password");
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
    try (Connection connection = connectionProvider.getConnection()) {
      connection.setAutoCommit(false);
      try {
        List<String> statements = planner.plan(connection);
        try (Statement stmt = connection.createStatement()) {
          for (String sql : statements) {
            executeOne(stmt, sql, absorbDuplicate, absorbMissing);
          }
        }
        connection.commit();
      } catch (SQLException e) {
        safeRollback(connection);
        throw e;
      } catch (RuntimeException e) {
        safeRollback(connection);
        throw e;
      }
    } catch (SQLException e) {
      if (dialect.isConnectivity(e)) {
        throw new RetryableAdapterException(
            AdapterErrorCode.POSTGIS_CONNECTION_ERROR, e, ADAPTER_NAME, redact(e.getMessage()));
      }
      throw new FatalAdapterException(
          AdapterErrorCode.POSTGIS_DDL_ERROR,
          e,
          operation.getDescription() + " failed: " + redact(e.getMessage()));
    }
  }

  private void executeOne(
      Statement stmt, String sql, boolean absorbDuplicate, boolean absorbMissing)
      throws SQLException {
    try {
      logger.debug("Executing DDL: {}", Encode.forJava(redact(sql)));
      stmt.execute(sql);
    } catch (SQLException e) {
      if (absorbDuplicate && dialect.isDuplicate(e)) {
        logger.info(
            "PostGIS object already exists (SQLState {}), treating create as success (idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      if (absorbMissing && dialect.isMissing(e)) {
        logger.info(
            "PostGIS object not found (SQLState {}), treating delete as success (already gone, idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      throw e;
    }
  }

  private void safeRollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException rollbackEx) {
      logger.warn("Rollback failed: {}", Encode.forJava(String.valueOf(rollbackEx.getMessage())));
    }
  }

  private void publishSuccess(ConfigEvent event, String message, String resourceId)
      throws FatalAdapterException, RetryableAdapterException {
    logger.info(Encode.forJava(message));
    publishSuccessResult(event, message, resourceId);
  }

  private static String redact(String text) {
    return text == null ? null : PASSWORD_LITERAL.matcher(text).replaceAll("$1'***'");
  }

  private String requireProperty(String key) {
    String value = getAdapterProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Required postgis adapter property missing: " + getName() + "." + key);
    }
    return value;
  }
}

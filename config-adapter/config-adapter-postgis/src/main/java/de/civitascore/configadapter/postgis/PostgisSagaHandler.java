/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.postgis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.postgis.DbRoleConfig;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.SchemaConfig;
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.SchemaRevoke;
import de.civitascore.configadapter.postgis.ddl.TableDdlBuilder;
import de.civitascore.configadapter.postgis.dialect.PostgisDialect;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Saga command handler for PostGIS, mirroring the FROST / APISIX / RedPanda handlers but executing
 * DDL over JDBC instead of HTTP. Discovered via {@link java.util.ServiceLoader} and registered in
 * the saga orchestrator's handler registry.
 *
 * <p>Forward operations and their compensations:
 *
 * <ul>
 *   <li>{@code CREATE_TABLE} ↔ {@code DROP_TABLE}
 *   <li>{@code CREATE_SCHEMA} ↔ {@code DROP_SCHEMA}
 *   <li>{@code CREATE_ROLE} ↔ {@code DROP_ROLE}
 * </ul>
 *
 * <p>The forward {@code CREATE_*} commands carry the resource definition as a nested map under
 * {@code tableConfig} / {@code schemaConfig} / {@code roleConfig}; each returns the identifiers it
 * created as {@code compensationData}, which the orchestrator flattens back into the payload of the
 * compensating {@code DROP_*} command. CREATE is idempotent (duplicate-object SQLStates absorbed);
 * DROP is idempotent (missing-object SQLStates absorbed), so compensations are safe to retry.
 *
 * <p>Role passwords may be encrypted {@code ENC(...)} values, decrypted with {@code
 * CIVITAS_MASTER_KEY} exactly as in {@link PostgisAdapter}. Plaintext passwords are never logged.
 */
public class PostgisSagaHandler implements SagaCommandHandler {

  private static final Logger logger = LoggerFactory.getLogger(PostgisSagaHandler.class);

  private static final String ADAPTER_NAME = "postgis";
  private static final String ROLE_CREDENTIAL_CONTEXT = PostgisAdapter.ROLE_CREDENTIAL_CONTEXT;
  private static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private static final String COMPENSATE_TYPE = "COMPENSATE_STEP";

  private static final String JDBC_URL_KEY = "postgis.jdbc.url";
  private static final String JDBC_USER_KEY = "postgis.jdbc.user";
  private static final String JDBC_PASSWORD_KEY = "postgis.jdbc.password";
  private static final String JDBC_MAX_POOL_SIZE_KEY = "postgis.jdbc.maxPoolSize";
  private static final String JDBC_CONNECTION_TIMEOUT_MS_KEY = "postgis.jdbc.connectionTimeoutMs";

  private static final String DEFAULT_MAX_POOL_SIZE = "5";
  private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

  private static final Pattern PASSWORD_LITERAL =
      Pattern.compile("(?i)(PASSWORD\\s+)'(?:[^']|'')*'");

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ConnectionProvider connectionProvider;
  private SqlDialect dialect;
  private TableDdlBuilder ddlBuilder;
  private byte[] stretchedKey = new byte[0];

  /** No-arg constructor for ServiceLoader discovery. Call {@link #initialize} before use. */
  public PostgisSagaHandler() {}

  @Override
  public String adapter() {
    return ADAPTER_NAME;
  }

  @Override
  public void initialize(AdapterConfig config) {
    String jdbcUrl = requireProperty(config, JDBC_URL_KEY);
    String user = requireProperty(config, JDBC_USER_KEY);
    String password = config.getProperty(JDBC_PASSWORD_KEY, "");
    int maxPoolSize =
        Integer.parseInt(config.getProperty(JDBC_MAX_POOL_SIZE_KEY, DEFAULT_MAX_POOL_SIZE));
    long connectionTimeoutMs =
        Long.parseLong(
            config.getProperty(JDBC_CONNECTION_TIMEOUT_MS_KEY, DEFAULT_CONNECTION_TIMEOUT_MS));

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

    logger.info("PostgisSagaHandler initialized for: {}", Encode.forJava(jdbcUrl));
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    this.dialect = dialect;
  }

  @Override
  public SagaCommandResult handle(SagaCommandMessage command) {
    boolean compensation = COMPENSATE_TYPE.equals(command.type());
    try {
      return switch (command.operation()) {
        case "CREATE_TABLE" -> createTable(command);
        case "DROP_TABLE" -> dropTable(command, compensation);
        case "CREATE_SCHEMA" -> createSchema(command);
        case "DROP_SCHEMA" -> dropSchema(command, compensation);
        case "CREATE_ROLE" -> createRole(command);
        case "DROP_ROLE" -> dropRole(command, compensation);
        default -> unknownOperation(command, compensation);
      };
    } catch (Exception e) {
      String error = command.operation() + " failed: " + redact(e.getMessage());
      logger.error(
          "PostGIS {} failed for saga {}",
          Encode.forJava(command.operation()),
          Encode.forJava(command.sagaId()),
          e);
      return compensation
          ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
          : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
    }
  }

  // ─── Forward operations ────────────────────────────────────────────────────

  private SagaCommandResult createTable(SagaCommandMessage command) throws SQLException {
    TableConfig table = convert(command, "tableConfig", TableConfig.class);
    runDdl(ddlBuilder.buildCreate(table), true, false);

    Map<String, Object> identifiers = new java.util.HashMap<>();
    if (table.getSchema() != null && !table.getSchema().isBlank()) {
      identifiers.put("schema", table.getSchema());
    }
    identifiers.put("table", table.getName());
    logger.info(
        "PostGIS table {} created, saga={}",
        Encode.forJava(table.qualifiedName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(
        command.sagaId(), command.stepId(), Map.copyOf(identifiers), Map.copyOf(identifiers));
  }

  private SagaCommandResult createSchema(SagaCommandMessage command) throws SQLException {
    SchemaConfig schema = convert(command, "schemaConfig", SchemaConfig.class);
    runDdl(dialect.createSchema(schema), true, false);

    Map<String, Object> identifiers = Map.of("schema", schema.getName());
    logger.info(
        "PostGIS schema {} created, saga={}",
        Encode.forJava(schema.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  private SagaCommandResult createRole(SagaCommandMessage command) throws SQLException {
    DbRoleConfig role = convert(command, "roleConfig", DbRoleConfig.class);
    String password = resolvePassword(role.getPassword());

    List<String> statements = new ArrayList<>(dialect.createRole(role, password));
    appendGrantStatements(statements, role.getName(), role.getGrants());
    runDdl(statements, true, false);

    Map<String, Object> identifiers = Map.of("role", role.getName());
    logger.info(
        "PostGIS role {} created, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return SagaCommandResult.success(command.sagaId(), command.stepId(), identifiers, identifiers);
  }

  // ─── Drop operations (also serve as compensations) ──────────────────────────

  private SagaCommandResult dropTable(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    String schema = optionalString(command, "schema");
    String table = requireString(command, "table");
    runDdl(dialect.dropTable(schema, table), false, true);
    logger.info(
        "PostGIS table {} dropped, saga={}",
        Encode.forJava(qualified(schema, table)),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  private SagaCommandResult dropSchema(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    SchemaConfig schema = new SchemaConfig();
    schema.setName(requireString(command, "schema"));
    schema.setCascade(Boolean.parseBoolean(String.valueOf(command.payload().get("cascade"))));
    runDdl(dialect.dropSchema(schema), false, true);
    logger.info(
        "PostGIS schema {} dropped, saga={}",
        Encode.forJava(schema.getName()),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  private SagaCommandResult dropRole(SagaCommandMessage command, boolean compensation)
      throws SQLException {
    DbRoleConfig role = new DbRoleConfig();
    role.setName(requireString(command, "role"));
    runDdl(dialect.dropRole(role), false, true);
    logger.info(
        "PostGIS role {} dropped, saga={}",
        Encode.forJava(role.getName()),
        Encode.forJava(command.sagaId()));
    return done(command, compensation);
  }

  // ─── DDL execution ───────────────────────────────────────────────────────

  private void runDdl(List<String> statements, boolean absorbDuplicate, boolean absorbMissing)
      throws SQLException {
    try (Connection connection = connectionProvider.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement stmt = connection.createStatement()) {
        for (String sql : statements) {
          executeOne(stmt, sql, absorbDuplicate, absorbMissing);
        }
        connection.commit();
      } catch (SQLException | RuntimeException e) {
        safeRollback(connection);
        throw e;
      }
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
            "PostGIS object already exists (SQLState {}), treating as success (idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      if (absorbMissing && dialect.isMissing(e)) {
        logger.info(
            "PostGIS object not found (SQLState {}), treating as success (idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      throw e;
    }
  }

  private void appendGrantStatements(
      List<String> statements, String roleName, List<SchemaGrant> grants) {
    GrantReconcilePlan plan = GrantReconciler.reconcile(grants, Map.of());
    for (SchemaGrant grant : plan.toGrant()) {
      statements.add(
          dialect.grantOnSchema(
              roleName, grant.schema(), grant.effectivePrivileges(), grant.isWithGrantOption()));
    }
    for (SchemaRevoke revoke : plan.toRevoke()) {
      statements.add(dialect.revokeOnSchema(roleName, revoke.schema(), revoke.privileges()));
    }
  }

  private void safeRollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException rollbackEx) {
      logger.warn("Rollback failed: {}", Encode.forJava(String.valueOf(rollbackEx.getMessage())));
    }
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────

  private SagaCommandResult done(SagaCommandMessage command, boolean compensation) {
    return compensation
        ? SagaCommandResult.compensationSuccess(command.sagaId(), command.stepId())
        : SagaCommandResult.success(command.sagaId(), command.stepId(), Map.of(), Map.of());
  }

  private SagaCommandResult unknownOperation(SagaCommandMessage command, boolean compensation) {
    String error = "Unknown postgis operation: " + command.operation();
    logger.warn(error);
    return compensation
        ? SagaCommandResult.compensationFailure(command.sagaId(), command.stepId(), error)
        : SagaCommandResult.failure(command.sagaId(), command.stepId(), error);
  }

  /**
   * Converts a nested config map into the expected {@link PostgisConfigValue} subtype. The map must
   * carry the {@code resourceType} discriminator (e.g. {@code "sql-table"}), exactly as the config
   * travels inside CloudEvents.
   */
  private <T extends PostgisConfigValue> T convert(
      SagaCommandMessage command, String key, Class<T> type) {
    Object value = command.payload().get(key);
    if (value == null) {
      throw new IllegalArgumentException(
          command.operation() + " payload missing required field: " + key);
    }
    PostgisConfigValue config = objectMapper.convertValue(value, PostgisConfigValue.class);
    if (!type.isInstance(config)) {
      throw new IllegalArgumentException(
          command.operation()
              + " payload field '"
              + key
              + "' is a "
              + config.getClass().getSimpleName()
              + ", expected "
              + type.getSimpleName());
    }
    return type.cast(config);
  }

  private static String requireString(SagaCommandMessage command, String key) {
    Object value = command.payload().get(key);
    if (!(value instanceof String s) || s.isBlank()) {
      throw new IllegalArgumentException(
          command.operation() + " payload missing required String field: " + key);
    }
    return s;
  }

  private static String optionalString(SagaCommandMessage command, String key) {
    Object value = command.payload().get(key);
    return value instanceof String s && !s.isBlank() ? s : null;
  }

  private String resolvePassword(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      return null;
    }
    Map<String, Object> wrapper = Map.of("password", rawPassword);
    if (CredentialDecryptor.containsEncryptedValues(wrapper) && stretchedKey.length == 0) {
      throw new IllegalStateException(
          MASTER_KEY_ENV + " is required to decrypt the encrypted role password");
    }
    try {
      Map<String, Object> decrypted =
          CredentialDecryptor.decryptMapValues(wrapper, stretchedKey, ROLE_CREDENTIAL_CONTEXT);
      return (String) decrypted.get("password");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt role password", e);
    }
  }

  private static String qualified(String schema, String name) {
    return schema == null || schema.isBlank() ? name : schema + "." + name;
  }

  private static String redact(String text) {
    return text == null ? null : PASSWORD_LITERAL.matcher(text).replaceAll("$1'***'");
  }

  private static String requireProperty(AdapterConfig config, String key) {
    String value = config.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required postgis saga handler property missing: " + key);
    }
    return value;
  }

  @Override
  public void close() {
    if (connectionProvider != null) {
      connectionProvider.close();
      connectionProvider = null;
    }
    if (stretchedKey != null) {
      Arrays.fill(stretchedKey, (byte) 0);
    }
  }
}

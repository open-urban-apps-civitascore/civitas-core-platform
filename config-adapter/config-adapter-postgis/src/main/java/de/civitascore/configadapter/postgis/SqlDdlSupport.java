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

import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.postgis.SchemaGrant;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.GrantReconcilePlan;
import de.civitascore.configadapter.postgis.ddl.GrantReconciler.SchemaRevoke;
import de.civitascore.configadapter.postgis.dialect.PostgisDialect;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared JDBC/DDL plumbing for {@link PostgisAdapter} and {@link PostgisSagaHandler}: pool and
 * dialect initialization from the {@code postgis.jdbc.*} properties, transactional DDL execution
 * with per-statement savepoints, grant-plan rendering, {@code ENC(...)} role-password resolution,
 * and {@code PASSWORD '…'} redaction. The two front classes keep only their event/saga-specific
 * dispatch and error wrapping.
 */
final class SqlDdlSupport implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(SqlDdlSupport.class);

  static final String MASTER_KEY_ENV = "CIVITAS_MASTER_KEY";

  private static final String JDBC_URL_KEY = "postgis.jdbc.url";
  private static final String JDBC_USER_KEY = "postgis.jdbc.user";
  private static final String JDBC_PASSWORD_KEY = "postgis.jdbc.password";
  private static final String JDBC_MAX_POOL_SIZE_KEY = "postgis.jdbc.maxPoolSize";
  private static final String JDBC_CONNECTION_TIMEOUT_MS_KEY = "postgis.jdbc.connectionTimeoutMs";

  private static final String DEFAULT_MAX_POOL_SIZE = "5";
  private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

  /** Masks {@code PASSWORD '…'} literals so plaintext never reaches logs or error messages. */
  private static final Pattern PASSWORD_LITERAL =
      Pattern.compile("(?i)(PASSWORD\\s+)'(?:[^']|'')*'");

  private ConnectionProvider connectionProvider;
  private SqlDialect dialect;
  private byte[] stretchedKey = new byte[0];

  /** Read within the transaction to build the statement list, possibly using the connection. */
  @FunctionalInterface
  interface StatementPlanner {
    List<String> plan(Connection connection) throws SQLException;
  }

  /**
   * Resolves the {@code postgis.jdbc.*} settings, builds the connection pool and dialect (unless a
   * test seam injected them), and loads the {@code CIVITAS_MASTER_KEY}.
   *
   * @return the JDBC URL, for the caller's log line
   */
  String initialize(AdapterConfig config) {
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

    this.stretchedKey = CryptoKeyLoader.loadAndStretchKeyFromEnv(MASTER_KEY_ENV);
    if (this.stretchedKey.length == 0) {
      logger.warn("{} not set — encrypted role passwords cannot be decrypted", MASTER_KEY_ENV);
    }
    return jdbcUrl;
  }

  SqlDialect dialect() {
    return dialect;
  }

  /** Test seam — inject a pre-configured provider before {@link #initialize(AdapterConfig)}. */
  void setConnectionProvider(ConnectionProvider connectionProvider) {
    this.connectionProvider = connectionProvider;
  }

  /** Test seam — inject an alternate dialect. */
  void setDialect(SqlDialect dialect) {
    this.dialect = dialect;
  }

  void runDdl(List<String> statements, boolean absorbDuplicate, boolean absorbMissing)
      throws SQLException {
    runDdl(connection -> statements, absorbDuplicate, absorbMissing);
  }

  /** Runs the planned statements in one transaction; rolls back and rethrows on failure. */
  void runDdl(StatementPlanner planner, boolean absorbDuplicate, boolean absorbMissing)
      throws SQLException {
    try (Connection connection = connectionProvider.getConnection()) {
      connection.setAutoCommit(false);
      try {
        List<String> statements = planner.plan(connection);
        try (Statement stmt = connection.createStatement()) {
          for (String sql : statements) {
            executeOne(connection, stmt, sql, absorbDuplicate, absorbMissing);
          }
        }
        connection.commit();
      } catch (SQLException | RuntimeException e) {
        safeRollback(connection);
        throw e;
      }
    }
  }

  /**
   * Executes one DDL statement, wrapped in a savepoint. An absorbed duplicate/missing object rolls
   * back to the savepoint rather than the whole transaction: PostgreSQL aborts the entire
   * transaction on any error, so without the savepoint the following statements in a
   * multi-statement plan (schema → table → index, or role → grants) would fail with "current
   * transaction is aborted". The savepoint keeps the idempotency-by-absorption contract working
   * inside one transaction.
   */
  private void executeOne(
      Connection connection,
      Statement stmt,
      String sql,
      boolean absorbDuplicate,
      boolean absorbMissing)
      throws SQLException {
    Savepoint savepoint = connection.setSavepoint();
    try {
      logger.debug("Executing DDL: {}", Encode.forJava(redact(sql)));
      stmt.execute(sql);
      connection.releaseSavepoint(savepoint);
    } catch (SQLException e) {
      if (absorbDuplicate && dialect.isDuplicate(e)) {
        connection.rollback(savepoint);
        logger.info(
            "PostGIS object already exists (SQLState {}), treating create as success (idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      if (absorbMissing && dialect.isMissing(e)) {
        connection.rollback(savepoint);
        logger.info(
            "PostGIS object not found (SQLState {}), treating delete as success (already gone, idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      throw e;
    }
  }

  /** Renders a reconcile plan into GRANT / REVOKE / REVOKE GRANT OPTION FOR statements. */
  void appendGrantStatements(List<String> statements, String roleName, GrantReconcilePlan plan) {
    for (SchemaGrant grant : plan.toGrant()) {
      statements.add(
          dialect.grantOnSchema(
              roleName, grant.schema(), grant.effectivePrivileges(), grant.isWithGrantOption()));
    }
    for (SchemaRevoke revoke : plan.toRevoke()) {
      statements.add(dialect.revokeOnSchema(roleName, revoke.schema(), revoke.privileges()));
    }
    for (SchemaRevoke revoke : plan.toRevokeGrantOption()) {
      statements.add(
          dialect.revokeGrantOptionOnSchema(roleName, revoke.schema(), revoke.privileges()));
    }
  }

  /**
   * Resolves a role password that may be an encrypted {@code ENC(...)} value.
   *
   * @throws IllegalStateException when the master key is missing or decryption fails — callers wrap
   *     this into their own error model
   */
  String resolvePassword(String rawPassword) {
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
          CredentialDecryptor.decryptMapValues(
              wrapper, stretchedKey, PostgisAdapter.ROLE_CREDENTIAL_CONTEXT);
      return (String) decrypted.get("password");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt role password", e);
    }
  }

  static String redact(String text) {
    return text == null ? null : PASSWORD_LITERAL.matcher(text).replaceAll("$1'***'");
  }

  private void safeRollback(Connection connection) {
    try {
      connection.rollback();
    } catch (SQLException rollbackEx) {
      logger.warn("Rollback failed: {}", Encode.forJava(String.valueOf(rollbackEx.getMessage())));
    }
  }

  private static String requireProperty(AdapterConfig config, String key) {
    String value = config.getProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Required postgis property missing: " + key);
    }
    return value;
  }

  /** Releases the JDBC connection pool and zeroes the key. Safe to call multiple times. */
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

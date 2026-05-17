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
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.AdapterOperation;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigValue;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.postgis.PostgisConfigValue;
import de.civitascore.configadapter.model.postgis.TableConfig;
import de.civitascore.configadapter.postgis.ddl.TableDdlBuilder;
import de.civitascore.configadapter.postgis.dialect.PostgisDialect;
import de.civitascore.configadapter.postgis.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostGIS / PostgreSQL adapter that processes configuration messages and manages SQL tables via
 * DDL. First scope is table CREATE; UPDATE and DELETE are placeholders.
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>SQLState {@code 42P07} / {@code 42P06} on CREATE → success (idempotent: object already
 *       exists)
 *   <li>SQLState {@code 42P01} / {@code 3F000} on DROP → success (idempotent: object already gone)
 *   <li>SQLState class {@code 08*} → {@link RetryableAdapterException} ({@code
 *       POSTGIS_CONNECTION_ERROR})
 *   <li>Other SQLExceptions → {@link FatalAdapterException} ({@code POSTGIS_DDL_ERROR})
 * </ul>
 */
public class PostgisAdapter extends AbstractConfigAdapter {

  private static final Logger logger = LoggerFactory.getLogger(PostgisAdapter.class);

  public static final String ADAPTER_NAME = "postgis";

  private static final String JDBC_URL_KEY = "jdbc.url";
  private static final String JDBC_USER_KEY = "jdbc.user";
  private static final String JDBC_PASSWORD_KEY = "jdbc.password";
  private static final String JDBC_MAX_POOL_SIZE_KEY = "jdbc.maxPoolSize";
  private static final String JDBC_CONNECTION_TIMEOUT_MS_KEY = "jdbc.connectionTimeoutMs";

  private static final String DEFAULT_MAX_POOL_SIZE = "5";
  private static final String DEFAULT_CONNECTION_TIMEOUT_MS = "5000";

  private ConnectionProvider connectionProvider;
  private SqlDialect dialect;
  private TableDdlBuilder ddlBuilder;

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

    TableConfig table = extractTableConfig(event);

    switch (operation) {
      case CREATE -> handleCreate(event, table);
      case DELETE -> handleDelete(event, table);
      case UPDATE ->
          throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
      default -> throw new FatalAdapterException(AdapterErrorCode.UNSUPPORTED_OPERATION, operation);
    }
  }

  private void handleCreate(ConfigEvent event, TableConfig table)
      throws FatalAdapterException, RetryableAdapterException {
    List<String> statements = ddlBuilder.buildCreate(table);
    executeDdl(AdapterOperation.TABLE_CREATE, statements);
    String message = "Table " + table.qualifiedName() + " created successfully";
    logger.info(Encode.forJava(message));
    publishSuccessResult(event, message, table.qualifiedName());
  }

  private void handleDelete(ConfigEvent event, TableConfig table)
      throws FatalAdapterException, RetryableAdapterException {
    List<String> statements = ddlBuilder.buildDrop(table);
    executeDdl(AdapterOperation.TABLE_DELETE, statements);
    String message = "Table " + table.qualifiedName() + " deleted successfully";
    logger.info(Encode.forJava(message));
    publishSuccessResult(event, message, table.qualifiedName());
  }

  private void executeDdl(AdapterOperation operation, List<String> statements)
      throws FatalAdapterException, RetryableAdapterException {
    try (Connection connection = connectionProvider.getConnection()) {
      connection.setAutoCommit(false);
      try (Statement stmt = connection.createStatement()) {
        for (String sql : statements) {
          executeOne(stmt, sql, operation);
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
            AdapterErrorCode.POSTGIS_CONNECTION_ERROR, e, ADAPTER_NAME, e.getMessage());
      }
      throw new FatalAdapterException(
          AdapterErrorCode.POSTGIS_DDL_ERROR,
          e,
          operation.getDescription() + " failed: " + e.getMessage());
    }
  }

  private void executeOne(Statement stmt, String sql, AdapterOperation operation)
      throws SQLException {
    try {
      logger.debug("Executing DDL: {}", Encode.forJava(sql));
      stmt.execute(sql);
    } catch (SQLException e) {
      if (operation == AdapterOperation.TABLE_CREATE && dialect.isDuplicate(e)) {
        logger.info(
            "PostGIS object already exists (SQLState {}), treating create as success (idempotent)",
            Encode.forJava(String.valueOf(e.getSQLState())));
        return;
      }
      if (operation == AdapterOperation.TABLE_DELETE && dialect.isMissing(e)) {
        logger.info(
            "PostGIS object not found (SQLState {}), treating delete as success (already deleted, idempotent)",
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

  private String requireProperty(String key) {
    String value = getAdapterProperty(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          "Required postgis adapter property missing: " + getName() + "." + key);
    }
    return value;
  }

  private TableConfig extractTableConfig(ConfigEvent event) throws FatalAdapterException {
    ConfigValue value = event.payload().config().value();
    if (value instanceof TableConfig table) {
      return table;
    }
    throw new FatalAdapterException(
        AdapterErrorCode.INVALID_PAYLOAD,
        "Expected TableConfig payload, got: "
            + (value == null ? "null" : value.getClass().getSimpleName()));
  }
}

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import de.civitascore.configadapter.connector.ConnectorValueException;
import de.civitascore.configadapter.connector.SqlConnectorValues;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.postgresql.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads up to {@value #ROWS} rows of the configured table, as CSV.
 *
 * <p>Table and columns reach the database only as bind parameters of {@code to_regclass} and {@code
 * parse_ident}, which apply the parser's quoting, case folding and search path. The query uses the
 * quoted names the catalog returns and runs in a read-only transaction that is always rolled back.
 * The {@code where} clause is the only configured SQL: it runs only when the request allows it,
 * wrapped as the pipeline wraps it, and {@link SqlConnectorValues} keeps it to one statement.
 */
final class SqlProber implements ConnectionProber {

  private static final Logger LOG = LoggerFactory.getLogger(SqlProber.class);

  static final int ROWS = 5;

  /** Tables, views, materialized views, foreign tables and partitioned tables. */
  private static final Set<String> READABLE_KINDS = Set.of("r", "v", "m", "f", "p");

  /**
   * PgJDBC prefers URL parameters over connect properties, so these would override the test's
   * limits. A {@code socketTimeout=0} waits forever on a host that never answers.
   */
  private static final Set<String> TIMEOUT_PARAMETERS =
      Set.of("connecttimeout", "sockettimeout", "logintimeout");

  /**
   * The database cuts each value to one character more than the preview holds, so a wide value such
   * as a PostGIS polygon costs no more than the preview.
   */
  static final int VALUE_CHARS = ResultText.MAX_PREVIEW_BYTES + 1;

  /** The CSV kept: the preview and the margin that masking reads past it. */
  private static final int KEPT_CHARS = ResultText.MAX_PREVIEW_BYTES + ResultText.MASK_MARGIN;

  /**
   * Caps what the driver buffers of one single-row fetch, as a last guard for the heap. With the
   * values cut, only a table of hundreds of columns can reach it.
   */
  private static final String MAX_RESULT_BUFFER = "16M";

  /** PostgreSQL's code for a statement that the query timeout cancelled. */
  private static final String QUERY_CANCELED = "57014";

  /** pgjdbc's code for a connection it could not open, a run-out login timeout included. */
  private static final String UNABLE_TO_CONNECT = "08001";

  private static final String RESOLVE_TABLE =
      "SELECT c.oid, c.relkind, quote_ident(n.nspname) || '.' || quote_ident(c.relname)"
          + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
          + " WHERE c.oid = to_regclass(?)";

  private static final String RESOLVE_COLUMN =
      "SELECT quote_ident(a.attname) FROM pg_attribute a, parse_ident(?) AS ident"
          + " WHERE a.attrelid = ? AND a.attnum > 0 AND NOT a.attisdropped"
          + " AND cardinality(ident) = 1 AND a.attname = ident[1]";

  private static final String ALL_COLUMNS =
      "SELECT quote_ident(attname) FROM pg_attribute"
          + " WHERE attrelid = ? AND attnum > 0 AND NOT attisdropped ORDER BY attnum";

  private final DestinationCheck guard;
  private final int timeoutSeconds;

  SqlProber(DestinationCheck guard, Duration timeout) {
    this.guard = guard;
    this.timeoutSeconds = (int) Math.max(1, timeout.toSeconds());
  }

  @Override
  public String connectionType() {
    return "sql";
  }

  @Override
  public ProbeResult probe(
      Map<String, Object> connector, String apiCall, boolean applyWhere, Cancellation cancellation)
      throws ConnectionTestFailure {
    SqlConnectorValues values;
    try {
      values = SqlConnectorValues.read(connector);
    } catch (ConnectorValueException e) {
      throw ConnectionTestFailure.invalid(e.getMessage(), e);
    }
    String jdbcUrl = withoutTimeouts(values.jdbcUrl());
    for (String host : hosts(jdbcUrl)) {
      guard.check(host);
    }
    try (Connection connection = connect(jdbcUrl, connector)) {
      cancellation.register(() -> connection.abort(Runnable::run));
      try {
        // The driver's default readOnlyMode sends BEGIN READ ONLY, and no DSN parameter changes it.
        connection.setAutoCommit(false);
        connection.setReadOnly(true);
        Table table = resolveTable(connection, values.table());
        List<String> columns =
            values.allColumns()
                ? allColumns(connection, table)
                : resolveColumns(connection, table, values.columns());
        String where = applyWhere ? values.where() : "";
        Rows rows = readRows(connection, table, columns, where);
        String message =
            "Connected and read " + rows.count() + " row(s) from " + table.name() + ".";
        if (where.isEmpty() && !values.where().isEmpty()) {
          message +=
              " The where clause is not part of the test; it runs as saved or with a password"
                  + " entered.";
        }
        return new ProbeResult(true, message, rows.csv(), rows.cut(), rows.count(), null);
      } finally {
        rollbackQuietly(connection);
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  /** The JDBC URL without the DSN's own timeouts, which the test sets instead. */
  static String withoutTimeouts(String jdbcUrl) {
    int query = jdbcUrl.indexOf('?');
    if (query < 0) {
      return jdbcUrl;
    }
    String kept =
        Arrays.stream(jdbcUrl.substring(query + 1).split("&"))
            .filter(pair -> !pair.isEmpty())
            .filter(pair -> !TIMEOUT_PARAMETERS.contains(parameterName(pair)))
            .collect(Collectors.joining("&"));
    return kept.isEmpty() ? jdbcUrl.substring(0, query) : jdbcUrl.substring(0, query + 1) + kept;
  }

  private static String parameterName(String pair) {
    int equals = pair.indexOf('=');
    return (equals < 0 ? pair : pair.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
  }

  /** The hosts of a JDBC URL; PgJDBC accepts a comma-separated list for fail-over. */
  private static List<String> hosts(String jdbcUrl) throws ConnectionTestFailure {
    Properties parsed = Driver.parseURL(jdbcUrl, null);
    if (parsed == null) {
      throw ConnectionTestFailure.invalid("The dsn is not a valid PostgreSQL connection URL.");
    }
    return List.of(parsed.getProperty("PGHOST", "").split(","));
  }

  private Connection connect(String jdbcUrl, Map<String, Object> connector)
      throws ConnectionTestFailure {
    Properties properties = new Properties();
    putIfPresent(properties, "user", connector.get("user"));
    putIfPresent(properties, "password", connector.get("password"));
    String seconds = String.valueOf(timeoutSeconds);
    properties.setProperty("connectTimeout", seconds);
    properties.setProperty("loginTimeout", seconds);
    properties.setProperty("socketTimeout", seconds);
    properties.setProperty("ApplicationName", "civitas-connection-test");
    properties.setProperty("maxResultBuffer", MAX_RESULT_BUFFER);
    long begin = System.nanoTime();
    try {
      // The driver instance, not DriverManager: its login timeout is global to the JVM.
      Connection connection = new Driver().connect(jdbcUrl, properties);
      if (connection == null) {
        throw ConnectionTestFailure.invalid("The dsn is not a valid PostgreSQL connection URL.");
      }
      return connection;
    } catch (SQLException e) {
      // A run-out login timeout carries no cause, so only its time tells it from a refusal.
      boolean loginTimedOut =
          UNABLE_TO_CONNECT.equals(e.getSQLState())
              && System.nanoTime() - begin >= Duration.ofSeconds(timeoutSeconds).toNanos();
      throw loginTimedOut ? ConnectionTestFailure.timeout(databaseMessage(e), e) : failure(e);
    }
  }

  private static Table resolveTable(Connection connection, String configured)
      throws SQLException, ConnectionTestFailure {
    try (PreparedStatement lookup = connection.prepareStatement(RESOLVE_TABLE)) {
      lookup.setString(1, configured);
      try (ResultSet result = lookup.executeQuery()) {
        if (!result.next()) {
          throw new ConnectionTestFailure(
              "The table '" + configured + "' does not exist or is not visible to the user.");
        }
        if (!READABLE_KINDS.contains(result.getString(2))) {
          throw new ConnectionTestFailure("'" + configured + "' is not a table or view.");
        }
        return new Table(result.getLong(1), result.getString(3));
      }
    }
  }

  private static List<String> resolveColumns(
      Connection connection, Table table, List<String> configured)
      throws SQLException, ConnectionTestFailure {
    List<String> columns = new ArrayList<>(configured.size());
    try (PreparedStatement lookup = connection.prepareStatement(RESOLVE_COLUMN)) {
      for (String column : configured) {
        lookup.setString(1, column);
        lookup.setLong(2, table.oid());
        try (ResultSet result = lookup.executeQuery()) {
          if (!result.next()) {
            throw new ConnectionTestFailure(
                "The column '"
                    + column
                    + "' does not exist in "
                    + table.name()
                    + "."
                    + caseHint(column));
          }
          columns.add(result.getString(1));
        } catch (SQLException e) {
          // parse_ident rejects a string that is not an identifier
          if ("22023".equals(e.getSQLState()) || "22P02".equals(e.getSQLState())) {
            throw ConnectionTestFailure.invalid("'" + column + "' is not a valid column name.", e);
          }
          throw e;
        }
      }
    }
    return columns;
  }

  /** PostgreSQL folds an unquoted name to lower case, so {@code valueText} finds no such column. */
  private static String caseHint(String column) {
    return column.startsWith("\"") || column.equals(column.toLowerCase(Locale.ROOT))
        ? ""
        : " Unquoted names are folded to lower case; write \"" + column + "\" to keep the case.";
  }

  private static List<String> allColumns(Connection connection, Table table) throws SQLException {
    List<String> columns = new ArrayList<>();
    try (PreparedStatement lookup = connection.prepareStatement(ALL_COLUMNS)) {
      lookup.setLong(1, table.oid());
      try (ResultSet result = lookup.executeQuery()) {
        while (result.next()) {
          columns.add(result.getString(1));
        }
      }
    }
    return columns;
  }

  /**
   * Every name in the statement comes from the catalog, quoted by {@code quote_ident}; a where
   * clause, when applied, is the configured text. Each value is read as its text form, which every
   * type has, cut to {@value #VALUE_CHARS} characters.
   *
   * @param where the where clause to apply, or {@code ""}
   */
  private Rows readRows(Connection connection, Table table, List<String> columns, String where)
      throws SQLException {
    List<String> cutColumns =
        columns.stream()
            .map(column -> "left(" + column + "::text, " + VALUE_CHARS + ") AS " + column)
            .toList();
    String sql =
        "SELECT "
            + String.join(", ", cutColumns)
            + " FROM "
            + table.name()
            // As NiFi's QueryDatabaseTableRecord appends its Additional WHERE Clause.
            + (where.isEmpty() ? "" : " WHERE (" + where + ")")
            + " LIMIT ?";
    try (PreparedStatement query = connection.prepareStatement(sql)) {
      query.setInt(1, ROWS);
      query.setMaxRows(ROWS);
      // One row per fetch, so that the result buffer limit applies to a row, not to all five.
      query.setFetchSize(1);
      query.setQueryTimeout(timeoutSeconds);
      try (ResultSet result = query.executeQuery()) {
        return csv(result);
      }
    }
  }

  /** The rows as RFC 4180 CSV with a header line. */
  private static Rows csv(ResultSet result) throws SQLException {
    ResultSetMetaData meta = result.getMetaData();
    int count = meta.getColumnCount();
    StringBuilder csv = new StringBuilder();
    for (int i = 1; i <= count; i++) {
      appendField(csv, meta.getColumnLabel(i), i == 1);
    }
    csv.append("\r\n");
    int rows = 0;
    boolean cut = false;
    boolean full = false;
    while (result.next()) {
      rows++;
      // Past the preview and its masking margin the rows are only counted, so that wide rows cost
      // no more memory than the preview.
      for (int i = 1; i <= count && !full; i++) {
        String value = result.getString(i);
        cut |= value != null && value.length() >= VALUE_CHARS;
        appendField(csv, value, i == 1);
        full = csv.length() > KEPT_CHARS;
      }
      if (full) {
        csv.setLength(KEPT_CHARS);
      } else {
        csv.append("\r\n");
      }
    }
    return new Rows(csv.toString(), rows, cut || full);
  }

  /** NULL is an empty field, an empty string a quoted one, so that the two can be told apart. */
  private static void appendField(StringBuilder csv, String value, boolean first) {
    if (!first) {
      csv.append(',');
    }
    if (value == null) {
      return;
    }
    if (value.isEmpty()
        || value.indexOf(',') >= 0
        || value.indexOf('"') >= 0
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0) {
      csv.append('"').append(value.replace("\"", "\"\"")).append('"');
    } else {
      csv.append(value);
    }
  }

  /** The database's answer, or a timeout when the database did not answer in time. */
  private static ConnectionTestFailure failure(SQLException e) {
    if (ConnectionTestFailure.timedOut(e) || QUERY_CANCELED.equals(e.getSQLState())) {
      return ConnectionTestFailure.timeout(databaseMessage(e), e);
    }
    // The server's own text can quote a row value, for example a value that a cast refused.
    return e.getCause() == null && e.getSQLState() != null
        ? ConnectionTestFailure.answeredBySource(
            databaseMessage(e), "The database answered with SQLSTATE " + e.getSQLState() + ".", e)
        : new ConnectionTestFailure(databaseMessage(e), e);
  }

  /** The database's own message, or a fixed sentence for a failure below the driver. */
  private static String databaseMessage(SQLException e) {
    if (e.getCause() != null) {
      return "The database did not answer: " + ConnectionTestFailure.describe(e);
    }
    String message = e.getMessage();
    return message == null || message.isBlank()
        ? "The database refused the connection (" + e.getSQLState() + ")."
        : message;
  }

  private static void putIfPresent(Properties properties, String key, Object value) {
    if (value != null && !String.valueOf(value).isEmpty()) {
      properties.setProperty(key, String.valueOf(value));
    }
  }

  private static void rollbackQuietly(Connection connection) {
    try {
      if (!connection.isClosed()) {
        connection.rollback();
      }
    } catch (SQLException e) {
      LOG.debug("Rolling back the test transaction failed; the connection is closed anyway", e);
    }
  }

  /**
   * A relation as the catalog knows it.
   *
   * @param oid the relation's OID
   * @param name the schema-qualified name, quoted by the database
   */
  private record Table(long oid, String name) {}

  /**
   * The rows read.
   *
   * @param csv the rows as CSV with a header line
   * @param count the number of rows
   * @param cut whether a value was longer than the database sent of it
   */
  private record Rows(String csv, int count, boolean cut) {}
}

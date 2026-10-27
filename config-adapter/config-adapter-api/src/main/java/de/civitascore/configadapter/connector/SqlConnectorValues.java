/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connector;

import static de.civitascore.configadapter.connector.ConnectorValues.trimmedNonBlank;
import static de.civitascore.configadapter.connector.ConnectorValues.trimmedString;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The values of a SQL connector, read and checked for every consumer of a SQL source. Only
 * PostgreSQL is supported, and credentials stay in the connector document.
 *
 * @param jdbcUrl the JDBC URL of the {@code dsn}, without credentials
 * @param table the table, as configured
 * @param columns the columns, as configured; empty or {@code *} means all
 * @param where the additional WHERE clause, or {@code ""}
 */
public record SqlConnectorValues(String jdbcUrl, String table, List<String> columns, String where) {

  /**
   * A {@code :name} bind placeholder, not a PostgreSQL {@code ::} cast. The lookbehind skips the
   * second colon of {@code ::}, the trailing letter the first.
   */
  private static final Pattern NAMED_PLACEHOLDER = Pattern.compile("(?<!:):[A-Za-z_]");

  /**
   * A PostgreSQL positional {@code $1} or numeric {@code :1} bind placeholder (not a {@code ::}).
   */
  private static final Pattern POSITIONAL_PLACEHOLDER = Pattern.compile("\\$\\d|(?<!:):\\d");

  /**
   * Query parameters allowed in a tenant SQL {@code dsn}. It is an allowlist because parameters
   * such as {@code socketFactory} or {@code sslfactory} load a class, a code-execution risk.
   * Cert-file TLS ({@code sslcert}, {@code sslkey}, {@code sslrootcert}) is excluded, since the
   * platform cannot provision the files.
   */
  private static final Set<String> ALLOWED_DSN_PARAMS =
      Set.of(
          "sslmode",
          "connecttimeout",
          "sockettimeout",
          "logintimeout",
          "tcpkeepalive",
          "applicationname",
          "currentschema",
          "readonly");

  public SqlConnectorValues {
    columns = List.copyOf(columns);
  }

  /**
   * Reads the SQL values of a connector document. Table and DSN are required, the driver must be
   * PostgreSQL, and a WHERE clause must be literal SQL.
   *
   * @throws ConnectorValueException if a value cannot be used
   */
  public static SqlConnectorValues read(Map<String, Object> connector)
      throws ConnectorValueException {
    String where = trimmedString(connector.get("where"));
    if (containsBindPlaceholder(where)) {
      throw new ConnectorValueException(
          "SQL 'where' must be literal SQL without bind placeholders (':name' or '?')");
    }
    // The driver runs each statement of a query, so a ';' would end the SELECT and start another
    // statement. Refused inside literals too, a false positive that favours safety.
    if (where.indexOf(';') >= 0) {
      throw new ConnectorValueException("SQL 'where' must be a single condition without ';'");
    }
    String dsn = trimmedString(connector.get("dsn"));
    // The JDBC URL takes the host after the last '@' of the authority, also behind a '#'.
    if (dsn.indexOf('#') >= 0) {
      throw new ConnectorValueException("SQL 'dsn' must not contain a fragment ('#')");
    }
    rejectUnknownDsnParams(dsn);

    String table = trimmedString(connector.get("table"));
    if (table.isEmpty()) {
      throw new ConnectorValueException("SQL source requires a non-empty 'table'");
    }

    String driver = trimmedString(connector.get("driver"));
    if (!driver.isEmpty()
        && !"postgres".equalsIgnoreCase(driver)
        && !"postgresql".equalsIgnoreCase(driver)) {
      throw new ConnectorValueException("SQL source driver not supported: " + driver);
    }

    // Without a DSN a consumer would fall back to its default database and read the wrong data.
    if (dsn.isEmpty()) {
      throw new ConnectorValueException("SQL source requires a non-empty 'dsn'");
    }
    return new SqlConnectorValues(
        postgresDsnToJdbcUrl(dsn), table, trimmedNonBlank(connector.get("columns")), where);
  }

  /** Whether the connector names no column or only {@code *}, which means all columns. */
  public boolean allColumns() {
    return columns.isEmpty() || columns.equals(List.of("*"));
  }

  /**
   * Converts a portal {@code dsn} ({@code postgres://[user[:pw]@]host:port/db[?params]}) to a JDBC
   * URL without the userinfo, as credentials are bound separately. Returns {@code null} if blank.
   */
  static String postgresDsnToJdbcUrl(Object dsn) {
    if (dsn == null) {
      return null;
    }
    String text = String.valueOf(dsn).trim();
    if (text.isEmpty()) {
      return null;
    }
    String withoutScheme = text.replaceFirst("(?i)^(postgres|postgresql)://", "");
    // Userinfo is stripped from the authority only, so an '@' in a query value survives.
    int authorityEnd = indexOfAny(withoutScheme, '/', '?');
    String authority = authorityEnd < 0 ? withoutScheme : withoutScheme.substring(0, authorityEnd);
    String rest = authorityEnd < 0 ? "" : withoutScheme.substring(authorityEnd);
    int at = authority.lastIndexOf('@');
    if (at >= 0) {
      authority = authority.substring(at + 1);
    }
    return "jdbc:postgresql://" + authority + rest;
  }

  private static void rejectUnknownDsnParams(String dsn) throws ConnectorValueException {
    int query = dsn.indexOf('?');
    if (query < 0) {
      return;
    }
    Set<String> seen = new HashSet<>();
    for (String pair : dsn.substring(query + 1).split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String name = (eq < 0 ? pair : pair.substring(0, eq)).trim().toLowerCase(Locale.ROOT);
      if (!ALLOWED_DSN_PARAMS.contains(name)) {
        throw new ConnectorValueException(
            "SQL source dsn parameter not allowed: '"
                + name
                + "'; permitted parameters are "
                + ALLOWED_DSN_PARAMS);
      }
      // The driver takes the last value of a name, a check of the parameters may read the first.
      if (!seen.add(name)) {
        throw new ConnectorValueException(
            "SQL source dsn parameter '" + name + "' appears more than once");
      }
    }
  }

  /**
   * Whether a WHERE clause holds a bind placeholder: {@code :name}, {@code ?}, {@code $1} or {@code
   * :1}. NiFi appends the clause verbatim without binding, so a placeholder would be invalid SQL.
   * Quoted literals and {@code ::} casts are ignored.
   *
   * <p>The bare {@code ?} check also rejects the jsonb operators {@code ?}, {@code ?|} and {@code
   * ?&amp;}, a deliberate false positive that favours safety.
   */
  private static boolean containsBindPlaceholder(String where) {
    if (where.isEmpty()) {
      return false;
    }
    String sansLiterals = where.replaceAll("'(?:[^']|'')*'", "");
    return sansLiterals.indexOf('?') >= 0
        || NAMED_PLACEHOLDER.matcher(sansLiterals).find()
        || POSITIONAL_PLACEHOLDER.matcher(sansLiterals).find();
  }

  /** The first index of any of {@code chars} in {@code text}, or -1 if none is present. */
  private static int indexOfAny(String text, char... chars) {
    int best = -1;
    for (char c : chars) {
      int index = text.indexOf(c);
      if (index >= 0 && (best < 0 || index < best)) {
        best = index;
      }
    }
    return best;
  }
}

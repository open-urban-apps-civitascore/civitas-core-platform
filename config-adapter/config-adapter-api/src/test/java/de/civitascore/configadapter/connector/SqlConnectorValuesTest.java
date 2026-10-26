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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlConnectorValuesTest {

  private static Map<String, Object> connector(String key, Object value) {
    Map<String, Object> map = new HashMap<>();
    map.put("dsn", "postgres://host:5432/db");
    map.put("table", "readings");
    map.put(key, value);
    return map;
  }

  private static ConnectorValueException rejected(Map<String, Object> connector) {
    return assertThrows(ConnectorValueException.class, () -> SqlConnectorValues.read(connector));
  }

  @Test
  void dsnToJdbcUrlStripsUserinfoButKeepsAtSignInQueryParameter() {
    assertEquals(
        "jdbc:postgresql://host:5432/db?applicationname=x@y",
        SqlConnectorValues.postgresDsnToJdbcUrl("postgres://u:p@host:5432/db?applicationname=x@y"));
  }

  @Test
  void dsnToJdbcUrlAcceptsPostgresqlScheme() {
    assertEquals(
        "jdbc:postgresql://host:5432/db",
        SqlConnectorValues.postgresDsnToJdbcUrl("postgresql://u:p@host:5432/db"));
  }

  @Test
  void dsnToJdbcUrlKeepsDsnWithoutUserinfo() {
    assertEquals(
        "jdbc:postgresql://host/db?sslmode=require",
        SqlConnectorValues.postgresDsnToJdbcUrl("postgres://host/db?sslmode=require"));
  }

  @Test
  void dsnToJdbcUrlOfNullOrBlankIsNull() {
    assertNull(SqlConnectorValues.postgresDsnToJdbcUrl(null));
    assertNull(SqlConnectorValues.postgresDsnToJdbcUrl("  "));
  }

  @Test
  void readBuildsValuesFromConnector() throws ConnectorValueException {
    Map<String, Object> connector = connector("columns", List.of(" a ", "b"));
    connector.put("where", " x > 1 ");
    connector.put("driver", "PostgreSQL");

    SqlConnectorValues values = SqlConnectorValues.read(connector);

    assertEquals("jdbc:postgresql://host:5432/db", values.jdbcUrl());
    assertEquals("readings", values.table());
    assertEquals(List.of("a", "b"), values.columns());
    assertEquals("x > 1", values.where());
  }

  @ParameterizedTest
  @ValueSource(strings = {"id = :id", "id = ?", "id = $1", "id = :1"})
  void readRejectsWhereWithBindPlaceholder(String where) {
    ConnectorValueException e = rejected(connector("where", where));

    assertTrue(e.getMessage().contains("bind placeholders"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sslmode=require&sslmode=disable", "SSLMODE=require&sslmode=disable"})
  void readRejectsARepeatedDsnParameter(String query) {
    ConnectorValueException e =
        rejected(connector("dsn", "postgres://db.example:5432/city?" + query));

    assertTrue(e.getMessage().contains("more than once"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"x > 1; SELECT 1", "x > 1;", "note = 'a;b'"})
  void readRejectsWhereWithStatementSeparator(String where) {
    ConnectorValueException e = rejected(connector("where", where));

    assertTrue(e.getMessage().contains("';'"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"created_at::date = '2026-01-01'", "note = 'why?'", "t > '12:00:00'", "x = 1"})
  void readAcceptsCastsAndPlaceholderCharactersInsideLiterals(String where)
      throws ConnectorValueException {
    assertEquals(where, SqlConnectorValues.read(connector("where", where)).where());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sslfactory=x.Y", "socketFactory=x.Y", "SSLFactory=x.Y"})
  void readRejectsDsnParameterOutsideAllowlist(String param) {
    Map<String, Object> connector = connector("dsn", "postgres://host/db?sslmode=require&" + param);

    ConnectorValueException e = rejected(connector);

    assertTrue(e.getMessage().contains("not allowed"), e.getMessage());
    assertTrue(e.getMessage().toLowerCase().contains(param.split("=")[0].toLowerCase()));
  }

  @Test
  void readAcceptsAllowedDsnParameters() throws ConnectorValueException {
    SqlConnectorValues values =
        SqlConnectorValues.read(
            connector("dsn", "postgres://host/db?sslmode=require&connecttimeout=5"));

    assertEquals("jdbc:postgresql://host/db?sslmode=require&connecttimeout=5", values.jdbcUrl());
  }

  @Test
  void readRejectsBlankTable() {
    ConnectorValueException e = rejected(connector("table", "  "));

    assertTrue(e.getMessage().contains("'table'"), e.getMessage());
  }

  @Test
  void readRejectsUnsupportedDriver() {
    ConnectorValueException e = rejected(connector("driver", "mysql"));

    assertTrue(e.getMessage().contains("mysql"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "postgres", "postgresql", "POSTGRES"})
  void readAcceptsPostgresDriverOrNone(String driver) throws ConnectorValueException {
    assertEquals("readings", SqlConnectorValues.read(connector("driver", driver)).table());
  }

  /** Everything after '#' would otherwise name the host the driver connects to. */
  @Test
  void readRejectsDsnWithFragment() {
    ConnectorValueException e =
        rejected(connector("dsn", "postgres://db.example:5432#@attacker.example/city"));

    assertTrue(e.getMessage().contains("'dsn'"), e.getMessage());
  }

  @Test
  void readRejectsBlankDsn() {
    ConnectorValueException e = rejected(connector("dsn", " "));

    assertTrue(e.getMessage().contains("'dsn'"), e.getMessage());
  }

  @Test
  void allColumnsIsTrueForEmptyAndStar() {
    assertTrue(new SqlConnectorValues("u", "t", List.of(), "").allColumns());
    assertTrue(new SqlConnectorValues("u", "t", List.of("*"), "").allColumns());
  }

  @Test
  void allColumnsIsFalseForNamedColumn() {
    assertFalse(new SqlConnectorValues("u", "t", List.of("a"), "").allColumns());
  }
}

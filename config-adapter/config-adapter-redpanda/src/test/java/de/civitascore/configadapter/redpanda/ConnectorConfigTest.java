/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.redpanda;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectorConfigTest {

  @Test
  @DisplayName("SQL config with query renders sql_raw input")
  void sqlWithQuery_rendersSqlRaw() {
    ConnectorConfig.Sql sql =
        new ConnectorConfig.Sql(
            "postgres",
            "postgres://user:pass@db:5432/testdb",
            "sql-user",
            "ENC(secret)",
            "SELECT * FROM test_table",
            null,
            null,
            null);

    Map<String, Object> input = sql.toInputMap("sql-datasource");

    assertEquals("sql-datasource", input.get("label"));
    assertTrue(input.containsKey("sql_raw"));
    assertFalse(input.containsKey("sql_select"));

    @SuppressWarnings("unchecked")
    Map<String, Object> sqlRaw = (Map<String, Object>) input.get("sql_raw");
    assertEquals("postgres", sqlRaw.get("driver"));
    assertEquals("postgres://user:pass@db:5432/testdb", sqlRaw.get("dsn"));
    assertEquals("sql-user", sqlRaw.get("user"));
    assertEquals("ENC(secret)", sqlRaw.get("password"));
    assertEquals("SELECT * FROM test_table", sqlRaw.get("query"));
  }

  @Test
  @DisplayName("SQL config with table and columns renders sql_select input")
  void sqlWithTableAndColumns_rendersSqlSelect() {
    ConnectorConfig.Sql sql =
        new ConnectorConfig.Sql(
            "postgres",
            "postgres://user:pass@db:5432/testdb",
            "sql-user",
            "ENC(secret)",
            null,
            "public.device_definitions",
            List.of("*"),
            "device_id = 1");

    Map<String, Object> input = sql.toInputMap("sql-datasource");

    assertEquals("sql-datasource", input.get("label"));
    assertTrue(input.containsKey("sql_select"));
    assertFalse(input.containsKey("sql_raw"));

    @SuppressWarnings("unchecked")
    Map<String, Object> sqlSelect = (Map<String, Object>) input.get("sql_select");
    assertEquals("postgres", sqlSelect.get("driver"));
    assertEquals("postgres://user:pass@db:5432/testdb", sqlSelect.get("dsn"));
    assertEquals("sql-user", sqlSelect.get("user"));
    assertEquals("ENC(secret)", sqlSelect.get("password"));
    assertEquals("public.device_definitions", sqlSelect.get("table"));
    assertEquals(List.of("*"), sqlSelect.get("columns"));
    assertEquals("device_id = 1", sqlSelect.get("where"));
  }

  @Test
  @DisplayName("SQL config without optional credentials omits user and password")
  void sqlWithoutOptionalCredentials_omitsUserAndPassword() {
    ConnectorConfig.Sql sql =
        new ConnectorConfig.Sql(
            "postgres",
            "postgres://db:5432/testdb",
            null,
            null,
            null,
            "public.device_definitions",
            List.of("*"),
            null);

    Map<String, Object> input = sql.toInputMap("sql-datasource");

    @SuppressWarnings("unchecked")
    Map<String, Object> sqlSelect = (Map<String, Object>) input.get("sql_select");
    assertNotNull(sqlSelect);
    assertFalse(sqlSelect.containsKey("user"));
    assertFalse(sqlSelect.containsKey("password"));
  }
}

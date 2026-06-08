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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AdapterConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostgisSagaHandlerTest {

  private PostgisSagaHandler handler;
  private AdapterConfig mockConfig;
  private ConnectionProvider mockConnectionProvider;
  private Connection mockConnection;
  private Statement mockStatement;

  @BeforeEach
  void setUp() throws SQLException {
    handler = new PostgisSagaHandler();
    mockConfig = mock(AdapterConfig.class);
    mockConnectionProvider = mock(ConnectionProvider.class);
    mockConnection = mock(Connection.class);
    mockStatement = mock(Statement.class);

    when(mockConnectionProvider.getConnection()).thenReturn(mockConnection);
    when(mockConnection.createStatement()).thenReturn(mockStatement);

    when(mockConfig.getProperty("postgis.jdbc.url")).thenReturn("jdbc:postgresql://h/db");
    when(mockConfig.getProperty("postgis.jdbc.user")).thenReturn("user");
    when(mockConfig.getProperty("postgis.jdbc.password", "")).thenReturn("pw");
    when(mockConfig.getProperty("postgis.jdbc.maxPoolSize", "5")).thenReturn("5");
    when(mockConfig.getProperty("postgis.jdbc.connectionTimeoutMs", "5000")).thenReturn("5000");

    handler.setConnectionProvider(mockConnectionProvider);
    handler.initialize(mockConfig);
  }

  @Test
  void adapterNameIsPostgis() {
    assertEquals("postgis", handler.adapter());
  }

  @Nested
  class ForwardOperations {

    @Test
    void createTableExecutesDdlAndReturnsCompensationData() throws Exception {
      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_TABLE",
                  Map.of(
                      "tableConfig",
                      Map.of(
                          "resourceType",
                          "sql-table",
                          "schema",
                          "iot",
                          "name",
                          "readings",
                          "columns",
                          List.of(Map.of("name", "id", "type", "BIGINT", "nullable", false))))));

      assertEquals("STEP_COMPLETED", result.type());
      assertEquals("iot", result.compensationData().get("schema"));
      assertEquals("readings", result.compensationData().get("table"));
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(anyString());
      verify(mockConnection).commit();
    }

    @Test
    void createSchemaReturnsSchemaIdentifier() {
      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_SCHEMA",
                  Map.of("schemaConfig", Map.of("resourceType", "sql-schema", "name", "iot"))));

      assertEquals("STEP_COMPLETED", result.type());
      assertEquals("iot", result.compensationData().get("schema"));
    }

    @Test
    void createRoleWithGrantsExecutesCreateAndGrant() throws Exception {
      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_ROLE",
                  Map.of(
                      "roleConfig",
                      Map.of(
                          "resourceType",
                          "sql-role",
                          "name",
                          "analyst",
                          "canLogin",
                          true,
                          "grants",
                          List.of(Map.of("schema", "iot", "privileges", List.of("USAGE")))))));

      assertEquals("STEP_COMPLETED", result.type());
      assertEquals("analyst", result.compensationData().get("role"));

      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.times(2)).execute(sql.capture());
      assertTrue(
          sql.getAllValues().stream().anyMatch(s -> s.startsWith("CREATE ROLE \"analyst\"")));
      assertTrue(
          sql.getAllValues().stream().anyMatch(s -> s.startsWith("GRANT USAGE ON SCHEMA \"iot\"")));
    }

    @Test
    void encryptedPasswordWithoutMasterKeyFailsStep() {
      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_ROLE",
                  Map.of(
                      "roleConfig",
                      Map.of(
                          "resourceType",
                          "sql-role",
                          "name",
                          "analyst",
                          "canLogin",
                          true,
                          "password",
                          "ENC(Zm9vYmFy)"))));

      assertEquals("STEP_FAILED", result.type());
      assertTrue(result.error().contains("CIVITAS_MASTER_KEY"));
    }

    @Test
    void duplicateObjectIsAbsorbedAsSuccess() throws Exception {
      when(mockStatement.execute(anyString())).thenThrow(new SQLException("role exists", "42710"));

      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_ROLE",
                  Map.of("roleConfig", Map.of("resourceType", "sql-role", "name", "analyst"))));

      assertEquals("STEP_COMPLETED", result.type());
      verify(mockConnection).commit();
    }

    @Test
    void unknownOperationFailsStep() {
      SagaCommandResult result = handler.handle(execute("FROBNICATE", Map.of()));

      assertEquals("STEP_FAILED", result.type());
      assertTrue(result.error().contains("Unknown postgis operation"));
    }

    @Test
    void sqlErrorRollsBackAndFailsStep() throws Exception {
      when(mockStatement.execute(anyString())).thenThrow(new SQLException("boom", "42601"));

      SagaCommandResult result =
          handler.handle(
              execute(
                  "CREATE_SCHEMA",
                  Map.of("schemaConfig", Map.of("resourceType", "sql-schema", "name", "x"))));

      assertEquals("STEP_FAILED", result.type());
      verify(mockConnection).rollback();
      verify(mockConnection, never()).commit();
    }
  }

  @Nested
  class CompensationOperations {

    @Test
    void dropTableCompensationReturnsCompensationCompleted() throws Exception {
      SagaCommandResult result =
          handler.handle(compensate("DROP_TABLE", Map.of("schema", "iot", "table", "readings")));

      assertEquals("COMPENSATION_COMPLETED", result.type());
      verify(mockStatement).execute(anyString());
      verify(mockConnection).commit();
    }

    @Test
    void dropRoleCompensationAbsorbsMissingRole() throws Exception {
      when(mockStatement.execute(anyString())).thenThrow(new SQLException("role missing", "42704"));

      SagaCommandResult result = handler.handle(compensate("DROP_ROLE", Map.of("role", "analyst")));

      assertEquals("COMPENSATION_COMPLETED", result.type());
      verify(mockConnection).commit();
    }

    @Test
    void dropSchemaAsForwardStepReturnsStepCompleted() throws Exception {
      SagaCommandResult result =
          handler.handle(execute("DROP_SCHEMA", Map.of("schema", "iot", "cascade", true)));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement).execute(sql.capture());
      assertTrue(sql.getValue().contains("CASCADE"));
    }
  }

  private static SagaCommandMessage execute(String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        "EXECUTE_STEP", "msg-1", "saga-1", "step-1", "postgis", operation, payload);
  }

  private static SagaCommandMessage compensate(String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        "COMPENSATE_STEP", "msg-1", "saga-1", "step-1", "postgis", operation, payload);
  }
}

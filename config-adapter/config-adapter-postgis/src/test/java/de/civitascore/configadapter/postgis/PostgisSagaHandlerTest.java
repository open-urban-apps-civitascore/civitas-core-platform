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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Nested
  class SinkProvisioning {

    @Test
    void provisionSinkCreatesSchemaTableAndRole() throws Exception {
      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", postgisSinkTrigger()));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeast(3)).execute(sql.capture());
      List<String> executed = sql.getAllValues();
      assertTrue(executed.stream().anyMatch(s -> s.startsWith("CREATE SCHEMA \"ds_42\"")));
      assertTrue(
          executed.stream()
              .anyMatch(s -> s.startsWith("CREATE TABLE \"ds_42\".\"sensor_readings\"")));
      assertTrue(executed.stream().anyMatch(s -> s.startsWith("CREATE ROLE \"ds_42_geo\"")));
      assertTrue(executed.stream().anyMatch(s -> s.startsWith("GRANT USAGE ON SCHEMA \"ds_42\"")));
      // schema USAGE alone can't read rows — the read role also needs table-level SELECT
      assertTrue(
          executed.stream()
              .anyMatch(
                  s -> s.equals("GRANT SELECT ON \"ds_42\".\"sensor_readings\" TO \"ds_42_geo\"")),
          "read role must be granted SELECT on the sink table");
      verify(mockConnection).commit();
    }

    @Test
    void provisionSinkAltersSchemaOwnerAfterCreatingRole() throws Exception {
      // ALTER SCHEMA … OWNER TO requires the target role to exist, so the owner change must come
      // after CREATE ROLE.
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of(
                          "schema", "ds_42",
                          "owner", "ds_42_geo",
                          "tableName", "sensor_readings",
                          "columns",
                              List.of(Map.of("name", "id", "type", "BIGINT", "nullable", false)),
                          "readRole", Map.of("name", "ds_42_geo", "canLogin", true)))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      List<String> executed = sql.getAllValues();
      int createRoleIdx = indexOfFirst(executed, s -> s.startsWith("CREATE ROLE \"ds_42_geo\""));
      int alterOwnerIdx =
          indexOfFirst(executed, s -> s.startsWith("ALTER SCHEMA \"ds_42\" OWNER TO"));
      assertTrue(createRoleIdx >= 0, "role should be created");
      assertTrue(alterOwnerIdx >= 0, "schema owner should be altered");
      assertTrue(createRoleIdx < alterOwnerIdx, "owner change must run after the role is created");
    }

    private int indexOfFirst(List<String> statements, java.util.function.Predicate<String> match) {
      for (int i = 0; i < statements.size(); i++) {
        if (match.test(statements.get(i))) {
          return i;
        }
      }
      return -1;
    }

    @Test
    void provisionSinkReturnsProvisionedSinksAsCompensationData() {
      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", postgisSinkTrigger()));

      assertEquals("STEP_COMPLETED", result.type());
      assertTrue(result.compensationData().containsKey("provisionedSinks"));
    }

    @Test
    void provisionSinkWithoutColumnsFails() {
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of("schema", "ds_42", "tableName", "no_cols"))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_FAILED", result.type());
      assertTrue(result.error().contains("at least one column"));
    }

    @Test
    void provisionSinkDerivesColumnsFromDataStructureWhenNoneConfigured() throws Exception {
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of("tableName", "sensor_observations"),
                      "dataStructure",
                      Map.of(
                          "$id",
                          "urn:core:datastructure:08e34478",
                          "title",
                          "Observation",
                          "definitions",
                          Map.of(
                              "Observation",
                              Map.of(
                                  "type",
                                  "object",
                                  "properties",
                                  Map.of(
                                      "station_id", Map.of("type", "string"),
                                      "temperature", Map.of("type", "string")),
                                  "required",
                                  List.of("station_id", "temperature")))))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      String createTable =
          sql.getAllValues().stream()
              .filter(s -> s.startsWith("CREATE TABLE"))
              .findFirst()
              .orElseThrow();
      assertTrue(createTable.contains("\"station_id\" TEXT NOT NULL"));
      assertTrue(createTable.contains("\"temperature\" TEXT NOT NULL"));
    }

    @Test
    void provisionSinkDerivesPrimaryKeyFromXCorePrimaryKeyMarker() throws Exception {
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of("tableName", "sensor_observations"),
                      "dataStructure",
                      Map.of(
                          "properties",
                          Map.of(
                              "station_id", Map.of("type", "string", "x-core-primaryKey", true),
                              "temperature", Map.of("type", "string"))))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      String createTable =
          sql.getAllValues().stream()
              .filter(s -> s.startsWith("CREATE TABLE"))
              .findFirst()
              .orElseThrow();
      assertTrue(createTable.contains("PRIMARY KEY (\"station_id\")"));
    }

    @Test
    void explicitConfigurationPrimaryKeyOverridesSchemaMarker() throws Exception {
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of(
                          "tableName", "sensor_observations", "primaryKey", List.of("temperature")),
                      "dataStructure",
                      Map.of(
                          "properties",
                          Map.of(
                              "station_id", Map.of("type", "string", "x-core-primaryKey", true),
                              "temperature", Map.of("type", "string"))))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      String createTable =
          sql.getAllValues().stream()
              .filter(s -> s.startsWith("CREATE TABLE"))
              .findFirst()
              .orElseThrow();
      assertTrue(createTable.contains("PRIMARY KEY (\"temperature\")"));
    }

    @Test
    void derivedPrimaryKeyAbsentFromExplicitColumnsIsDropped() throws Exception {
      // Explicit configuration.columns are used as-is; the schema still marks a property that is
      // not among those columns. The derived PK must be filtered out so CREATE TABLE stays valid.
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of(
                          "tableName",
                          "sensor_observations",
                          "columns",
                          List.of(Map.of("name", "temperature", "type", "TEXT"))),
                      "dataStructure",
                      Map.of(
                          "properties",
                          Map.of(
                              "station_id", Map.of("type", "string", "x-core-primaryKey", true),
                              "temperature", Map.of("type", "string"))))));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      String createTable =
          sql.getAllValues().stream()
              .filter(s -> s.startsWith("CREATE TABLE"))
              .findFirst()
              .orElseThrow();
      assertTrue(createTable.contains("\"temperature\" TEXT"));
      assertFalse(
          createTable.contains("PRIMARY KEY"), "expected no primary key clause: " + createTable);
    }

    @Test
    void provisionSinkWithoutPostgisSinkFails() {
      Map<String, Object> trigger =
          Map.of("datasinks", List.of(Map.of("type", "KAFKA", "configuration", Map.of())));

      SagaCommandResult result = handler.handle(execute("PROVISION_SINK", trigger));

      assertEquals("STEP_FAILED", result.type());
      assertTrue(result.error().contains("at least one POSTGIS data sink"));
    }

    @Test
    void deprovisionSinkDropsRoleAndTableButNotSchema() throws Exception {
      SagaCommandResult result =
          handler.handle(compensate("DEPROVISION_SINK", postgisSinkTrigger()));

      assertEquals("COMPENSATION_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      List<String> executed = sql.getAllValues();
      assertTrue(executed.stream().anyMatch(s -> s.startsWith("DROP ROLE \"ds_42_geo\"")));
      assertTrue(
          executed.stream()
              .anyMatch(s -> s.startsWith("DROP TABLE \"ds_42\".\"sensor_readings\"")));
      assertTrue(executed.stream().noneMatch(s -> s.startsWith("DROP SCHEMA")));
      verify(mockConnection).commit();
    }

    @Test
    void deprovisionSinkAbsorbsMissingObjects() throws Exception {
      when(mockStatement.execute(anyString()))
          .thenThrow(new SQLException("undefined table", "42P01"));

      SagaCommandResult result = handler.handle(execute("DEPROVISION_SINK", postgisSinkTrigger()));

      assertEquals("STEP_COMPLETED", result.type());
      verify(mockConnection).commit();
    }

    @Test
    void deprovisionSinkWorksWithoutColumnDefinitions() throws Exception {
      // A delete trigger carries only the sink identifiers — dropping needs no column derivation.
      Map<String, Object> trigger =
          Map.of(
              "datasinks",
              List.of(
                  Map.of(
                      "type",
                      "POSTGIS",
                      "configuration",
                      Map.of("schema", "ds_42", "tableName", "sensor_readings"))));

      SagaCommandResult result = handler.handle(compensate("DEPROVISION_SINK", trigger));

      assertEquals("COMPENSATION_COMPLETED", result.type());
      ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
      verify(mockStatement, org.mockito.Mockito.atLeastOnce()).execute(sql.capture());
      assertTrue(
          sql.getAllValues().stream()
              .anyMatch(s -> s.startsWith("DROP TABLE \"ds_42\".\"sensor_readings\"")));
    }

    private Map<String, Object> postgisSinkTrigger() {
      return Map.of(
          "datasetId",
          "ds-42",
          "datasinks",
          List.of(
              Map.of(
                  "type",
                  "POSTGIS",
                  "configuration",
                  Map.of(
                      "schema",
                      "ds_42",
                      "tableName",
                      "sensor_readings",
                      "columns",
                      List.of(Map.of("name", "id", "type", "BIGINT", "nullable", false)),
                      "geometryColumns",
                      List.of(Map.of("name", "geom", "geometryType", "POINT", "srid", 4326)),
                      "primaryKey",
                      List.of("id"),
                      "readRole",
                      Map.of(
                          "name",
                          "ds_42_geo",
                          "canLogin",
                          true,
                          "privileges",
                          List.of("USAGE"))))));
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

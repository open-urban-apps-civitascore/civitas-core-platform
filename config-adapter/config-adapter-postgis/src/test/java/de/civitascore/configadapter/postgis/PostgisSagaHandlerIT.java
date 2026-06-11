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

import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.configuration.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration2.MapConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for {@link PostgisSagaHandler} against a real PostgreSQL/PostGIS container.
 * Exercises the saga forward operations and their compensations end-to-end against the database.
 */
class PostgisSagaHandlerIT extends AbstractPostgisIT {

  private PostgisSagaHandler handler;

  @BeforeEach
  void setUp() {
    Map<String, Object> props = new HashMap<>();
    props.put("postgis.jdbc.url", jdbcUrl());
    props.put("postgis.jdbc.user", username());
    props.put("postgis.jdbc.password", password());
    props.put("postgis.jdbc.maxPoolSize", "2");
    props.put("postgis.jdbc.connectionTimeoutMs", "5000");

    handler = new PostgisSagaHandler();
    handler.initialize(new AppConfig(new MapConfiguration(props)));
  }

  @AfterEach
  void tearDown() {
    if (handler != null) {
      handler.close();
    }
  }

  @Test
  void createTableThenCompensateDropsIt() throws Exception {
    SagaCommandResult created =
        handler.handle(
            execute(
                "CREATE_TABLE",
                Map.of(
                    "tableConfig",
                    Map.of(
                        "resourceType",
                        "sql-table",
                        "name",
                        "saga_widgets_it",
                        "columns",
                        List.of(Map.of("name", "id", "type", "BIGINT", "nullable", false))))));

    assertEquals("STEP_COMPLETED", created.type());
    assertTrue(tableExists(null, "saga_widgets_it"));

    SagaCommandResult compensated =
        handler.handle(compensate("DROP_TABLE", Map.of("table", "saga_widgets_it")));

    assertEquals("COMPENSATION_COMPLETED", compensated.type());
    assertFalse(tableExists(null, "saga_widgets_it"));
  }

  @Test
  void createSchemaThenCompensateDropsIt() throws Exception {
    SagaCommandResult created =
        handler.handle(
            execute(
                "CREATE_SCHEMA",
                Map.of(
                    "schemaConfig",
                    Map.of("resourceType", "sql-schema", "name", "saga_schema_it"))));

    assertEquals("STEP_COMPLETED", created.type());
    assertTrue(schemaExists("saga_schema_it"));

    SagaCommandResult compensated =
        handler.handle(compensate("DROP_SCHEMA", Map.of("schema", "saga_schema_it")));

    assertEquals("COMPENSATION_COMPLETED", compensated.type());
    assertFalse(schemaExists("saga_schema_it"));
  }

  @Test
  void createRoleWithGrantThenCompensateDropsRole() throws Exception {
    executeSql("CREATE SCHEMA IF NOT EXISTS \"saga_grant_it\"");

    SagaCommandResult created =
        handler.handle(
            execute(
                "CREATE_ROLE",
                Map.of(
                    "roleConfig",
                    Map.of(
                        "resourceType",
                        "sql-role",
                        "name",
                        "saga_role_it",
                        "grants",
                        List.of(
                            Map.of("schema", "saga_grant_it", "privileges", List.of("USAGE")))))));

    assertEquals("STEP_COMPLETED", created.type());
    assertTrue(roleExists("saga_role_it"));
    assertTrue(hasSchemaPrivilege("saga_role_it", "saga_grant_it", "USAGE"));

    // role owns granted privileges → revoke them before drop so the compensation succeeds cleanly
    executeSql("REVOKE ALL ON SCHEMA \"saga_grant_it\" FROM \"saga_role_it\"");

    SagaCommandResult compensated =
        handler.handle(compensate("DROP_ROLE", Map.of("role", "saga_role_it")));

    assertEquals("COMPENSATION_COMPLETED", compensated.type());
    assertFalse(roleExists("saga_role_it"));
  }

  @Test
  void dropMissingTableCompensationIsIdempotent() {
    SagaCommandResult compensated =
        handler.handle(compensate("DROP_TABLE", Map.of("table", "never_existed_saga_it")));

    assertEquals("COMPENSATION_COMPLETED", compensated.type());
  }

  @Test
  void provisionSinkCreatesObjectsThenDeprovisionRemovesTableAndRole() throws Exception {
    Map<String, Object> trigger = postgisSinkTrigger();

    SagaCommandResult provisioned = handler.handle(execute("PROVISION_SINK", trigger));
    assertEquals("STEP_COMPLETED", provisioned.type());
    assertTrue(schemaExists("sink_it"), "schema should be created");
    assertTrue(tableExists("sink_it", "observations"), "table should be created");
    assertTrue(roleExists("sink_it_geo"), "read role should be created");
    assertTrue(
        hasSchemaPrivilege("sink_it_geo", "sink_it", "USAGE"),
        "read role should hold USAGE on the schema");

    // GeoServer's read role must be able to use the schema before we can drop it cleanly.
    executeSql("REVOKE ALL ON SCHEMA \"sink_it\" FROM \"sink_it_geo\"");

    SagaCommandResult deprovisioned = handler.handle(compensate("DEPROVISION_SINK", trigger));
    assertEquals("COMPENSATION_COMPLETED", deprovisioned.type());
    assertFalse(tableExists("sink_it", "observations"), "table should be dropped");
    assertFalse(roleExists("sink_it_geo"), "read role should be dropped");
    assertTrue(schemaExists("sink_it"), "schema is intentionally left in place (may be shared)");
  }

  private static Map<String, Object> postgisSinkTrigger() {
    return Map.of(
        "datasetId",
        "ds-sink-it",
        "dataSinks",
        List.of(
            Map.of(
                "dataSinkType",
                "POSTGIS",
                "configuration",
                Map.of(
                    "schema",
                    "sink_it",
                    "tableName",
                    "observations",
                    "columns",
                    List.of(Map.of("name", "id", "type", "BIGINT", "nullable", false)),
                    "geometryColumns",
                    List.of(Map.of("name", "geom", "geometryType", "POINT", "srid", 4326)),
                    "primaryKey",
                    List.of("id"),
                    "readRole",
                    Map.of("name", "sink_it_geo", "privileges", List.of("USAGE"))))));
  }

  // --- Helpers ---

  private static SagaCommandMessage execute(String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        "EXECUTE_STEP", "msg-1", "saga-it-1", "step-1", "postgis", operation, payload);
  }

  private static SagaCommandMessage compensate(String operation, Map<String, Object> payload) {
    return new SagaCommandMessage(
        "COMPENSATE_STEP", "msg-1", "saga-it-1", "step-1", "postgis", operation, payload);
  }

  private static boolean schemaExists(String schema) throws SQLException {
    return count("SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?", schema)
        > 0;
  }

  private static boolean roleExists(String role) throws SQLException {
    return count("SELECT count(*) FROM pg_roles WHERE rolname = ?", role) > 0;
  }

  private static boolean hasSchemaPrivilege(String role, String schema, String privilege)
      throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps =
            connection.prepareStatement("SELECT has_schema_privilege(?, ?, ?)")) {
      ps.setString(1, role);
      ps.setString(2, schema);
      ps.setString(3, privilege);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  private static int count(String sql, String param) throws SQLException {
    try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
        PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, param);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }
}

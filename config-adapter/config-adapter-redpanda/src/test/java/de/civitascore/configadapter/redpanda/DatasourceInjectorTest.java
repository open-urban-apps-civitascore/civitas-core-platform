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

import static de.civitascore.configadapter.redpanda.RedpandaTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DatasourceInjectorTest {

  // pipeline model only carries a label — connector type comes from the datasource
  // { "input": { "label": "${<uuid>}" } }
  private static Map<String, Object> inputWithLabel(String datasourceId) {
    return Map.of("label", "${" + datasourceId + "}");
  }

  // ─── MQTT ────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("MQTT datasource injection")
  class MqttInjection {

    @Test
    @DisplayName("resolves MQTT placeholder with full configuration map")
    void resolve_mqttPlaceholder_injectsFullMqttInput() throws Exception {
      String datasourceId = "06bfb6a4-3f05-445c-8ed7-e42275c571ad";

      Datasource datasource =
          createMqttDatasourceWithDetails(
              datasourceId,
              List.of("tcp://broker.local:1883"),
              List.of("sensor/#"),
              "civitas-client-1",
              1,
              "mqttuser",
              "ENC(secret)");

      Map<String, Object> pipelineData =
          Map.of(
              "input", inputWithLabel(datasourceId),
              "output", Map.of("http_client", Map.of("url", "http://frost")));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) result.get("input");
      assertNotNull(input);

      @SuppressWarnings("unchecked")
      Map<String, Object> mqtt = (Map<String, Object>) input.get("mqtt");
      assertNotNull(mqtt);
      assertEquals(List.of("tcp://broker.local:1883"), mqtt.get("urls"));
      assertEquals(List.of("sensor/#"), mqtt.get("topics"));
      assertEquals("civitas-client-1", mqtt.get("client_id"));
      assertEquals(1, mqtt.get("qos"));
      assertEquals("mqttuser", mqtt.get("user"));
      assertEquals("ENC(secret)", mqtt.get("password"));
      assertNotNull(result.get("output"));
    }

    @Test
    @DisplayName("builds URL from host+port when urls list is absent")
    void resolve_mqttWithHostPort_buildsUrl() throws Exception {
      String datasourceId = "ds-host-port";

      Datasource datasource = new Datasource();
      datasource.setId(datasourceId);
      datasource.setType("mqtt");
      datasource.setHost("broker.example.com");
      datasource.setPort(1883);
      datasource.handleUnknownProperty("configuration", Map.of("topics", List.of("data/#")));

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      List<String> urls =
          (List<String>)
              ((Map<String, Object>) ((Map<String, Object>) result.get("input")).get("mqtt"))
                  .get("urls");

      assertEquals(1, urls.size());
      assertTrue(urls.get(0).contains("broker.example.com"));
    }
  }

  // ─── SQL ─────────────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("SQL datasource injection")
  class SqlInjection {

    @Test
    @DisplayName("resolves SQL placeholder with pre-built DSN")
    void resolve_sqlPlaceholderWithDsn_injectsSqlRaw() throws Exception {
      String datasourceId = "ds-sql-1";

      Datasource datasource =
          createSqlDatasource(
              datasourceId,
              "postgres://user:pass@db:5432/mydb?sslmode=disable",
              "SELECT * FROM sensors");

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      Map<String, Object> sqlRaw =
          (Map<String, Object>) ((Map<String, Object>) result.get("input")).get("sql_raw");

      assertNotNull(sqlRaw);
      assertEquals("postgres", sqlRaw.get("driver"));
      assertEquals("postgres://user:pass@db:5432/mydb?sslmode=disable", sqlRaw.get("dsn"));
      assertEquals("SELECT * FROM sensors", sqlRaw.get("query"));
    }

    @Test
    @DisplayName("builds DSN from host/port/database/user when dsn is absent")
    void resolve_sqlWithHostConfig_buildsDsn() throws Exception {
      String datasourceId = "ds-sql-host";

      Datasource datasource = new Datasource();
      datasource.setId(datasourceId);
      datasource.setType("sql");
      datasource.setHost("db.local");
      datasource.setPort(5432);
      datasource.handleUnknownProperty(
          "configuration",
          Map.of(
              "database", "sensordb",
              "username", "reader",
              "password", "secret"));

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      Map<String, Object> sqlRaw =
          (Map<String, Object>) ((Map<String, Object>) result.get("input")).get("sql_raw");

      assertNotNull(sqlRaw);
      String dsn = (String) sqlRaw.get("dsn");
      assertNotNull(dsn);
      assertTrue(dsn.contains("db.local"));
      assertTrue(dsn.contains("sensordb"));
      assertTrue(dsn.contains("reader"));
    }

    @Test
    @DisplayName("builds DSN without password when password is null")
    void resolve_sqlWithoutPassword_buildsDsnWithoutPassword() throws Exception {
      String datasourceId = "ds-sql-nopass";

      Datasource datasource = new Datasource();
      datasource.setId(datasourceId);
      datasource.setType("sql");
      datasource.setHost("db.local");
      datasource.setPort(5432);
      datasource.handleUnknownProperty(
          "configuration", Map.of("database", "testdb", "username", "admin"));

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      Map<String, Object> sqlRaw =
          (Map<String, Object>) ((Map<String, Object>) result.get("input")).get("sql_raw");
      String dsn = (String) sqlRaw.get("dsn");
      assertNotNull(dsn);
      assertTrue(dsn.contains("admin@db.local"));
      assertFalse(dsn.contains(":null@"));
    }
  }

  // ─── Edge Cases ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("returns unchanged when no label present")
    void resolve_noPlaceholder_returnsUnchanged() throws Exception {
      Map<String, Object> pipelineData =
          Map.of("input", Map.of("generate", Map.of("interval", "1s", "count", 1)));

      Datasource ds = new Datasource();
      ds.setId("ds-1");
      ds.setType("mqtt");

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(ds));

      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("returns unchanged when datasources list is empty")
    void resolve_emptyDatasources_returnsUnchanged() throws Exception {
      Map<String, Object> pipelineData = Map.of("input", inputWithLabel("some-id"));

      assertSame(pipelineData, DatasourceInjector.resolve(pipelineData, List.of()));
    }

    @Test
    @DisplayName("throws FatalAdapterException when datasource ID not found")
    void resolve_unknownDatasourceId_throwsFatalException() {
      Map<String, Object> pipelineData = Map.of("input", inputWithLabel("unknown-id"));

      Datasource ds = new Datasource();
      ds.setId("other-id");
      ds.setType("mqtt");

      assertThrows(
          FatalAdapterException.class, () -> DatasourceInjector.resolve(pipelineData, List.of(ds)));
    }

    @Test
    @DisplayName("returns null when pipelineData is null")
    void resolve_nullPipelineData_returnsNull() throws Exception {
      assertNull(DatasourceInjector.resolve(null, List.of()));
    }

    @Test
    @DisplayName("nested label (non-spec format) is not resolved")
    void resolve_nestedLabel_notResolved() throws Exception {
      String datasourceId = "ds-nested";

      Datasource datasource =
          createMqttDatasource(datasourceId, List.of("tcp://broker:1883"), List.of("t/#"));

      // label nested inside connector — this is NOT the supported format
      Map<String, Object> pipelineData =
          Map.of("input", Map.of("mqtt", Map.of("label", "${" + datasourceId + "}")));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      assertSame(pipelineData, result, "nested label must not be resolved — use top-level label");
    }

    @Test
    @DisplayName("returns unchanged for unsupported datasource type")
    void resolve_unsupportedDatasourceType_returnsUnchanged() throws Exception {
      String datasourceId = "ds-ftp";
      Datasource ds = new Datasource();
      ds.setId(datasourceId);
      ds.setType("ftp");

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));
      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(ds));
      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("rejects placeholder with extra closing brace")
    void extractPlaceholder_withExtraClosingBrace_returnsEmpty() throws Exception {
      Map<String, Object> pipelineData = Map.of("input", Map.of("label", "${test}}"));

      Datasource ds = new Datasource();
      ds.setId("test");
      ds.setType("mqtt");
      ds.setHost("broker.local");
      ds.handleUnknownProperty("configuration", Map.of("topics", List.of("t/#")));

      // ${test}} does not match strict regex — returns unchanged
      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(ds));
      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("rejects placeholder with extra opening brace")
    void extractPlaceholder_withExtraOpeningBrace_returnsEmpty() throws Exception {
      Map<String, Object> pipelineData = Map.of("input", Map.of("label", "${{test}"));

      Datasource ds = new Datasource();
      ds.setId("test");
      ds.setType("mqtt");

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(ds));
      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("rejects placeholder with special chars in UUID")
    void extractPlaceholder_withSpecialCharsInUuid_returnsEmpty() throws Exception {
      Map<String, Object> pipelineData = Map.of("input", Map.of("label", "${te.st}"));

      Datasource ds = new Datasource();
      ds.setId("te.st");
      ds.setType("mqtt");

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(ds));
      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("resolves correct datasource from multiple datasources")
    void resolve_multipleDatasources_resolvesCorrectOne() throws Exception {
      String targetId = "target-ds";

      Datasource mqttDs =
          createMqttDatasource(targetId, List.of("tcp://target:1883"), List.of("t/#"));

      Datasource sqlDs = createSqlDatasource("other-ds", "postgres://...", null);

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(targetId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(sqlDs, mqttDs));

      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) result.get("input");
      assertTrue(input.containsKey("mqtt"));

      @SuppressWarnings("unchecked")
      Map<String, Object> mqttInput = (Map<String, Object>) input.get("mqtt");
      assertEquals(List.of("tcp://target:1883"), mqttInput.get("urls"));
    }
  }
}

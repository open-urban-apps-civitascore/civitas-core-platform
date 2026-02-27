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
    void resolve_mqttPlaceholder_injectsFullMqttInput() {
      String datasourceId = "06bfb6a4-3f05-445c-8ed7-e42275c571ad";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "type",
              "mqtt",
              "configuration",
              Map.of(
                  "urls",
                  List.of("tcp://broker.local:1883"),
                  "topics",
                  List.of("sensor/#"),
                  "client_id",
                  "civitas-client-1",
                  "qos",
                  1,
                  "user",
                  "mqttuser",
                  "password",
                  "ENC(secret)"));

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
    @DisplayName("resolves MQTT placeholder using connectorType (portal-backend naming)")
    void resolve_mqttPlaceholder_withConnectorType_injectsMqtt() {
      String datasourceId = "ds-mqtt-portal";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "connectorType",
              "MQTT",
              "configuration",
              Map.of(
                  "urls", List.of("tcp://broker:1883"),
                  "topics", List.of("test/#")));

      Map<String, Object> pipelineData = Map.of("input", inputWithLabel(datasourceId));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      @SuppressWarnings("unchecked")
      Map<String, Object> input = (Map<String, Object>) result.get("input");
      assertTrue(input.containsKey("mqtt"));
    }

    @Test
    @DisplayName("builds URL from host+port when urls list is absent")
    void resolve_mqttWithHostPort_buildsUrl() {
      String datasourceId = "ds-host-port";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "type",
              "mqtt",
              "host",
              "broker.example.com",
              "port",
              1883,
              "configuration",
              Map.of("topics", List.of("data/#")));

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
    void resolve_sqlPlaceholderWithDsn_injectsSqlRaw() {
      String datasourceId = "ds-sql-1";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "type",
              "postgresql",
              "configuration",
              Map.of(
                  "dsn", "postgres://user:pass@db:5432/mydb?sslmode=disable",
                  "query", "SELECT * FROM sensors"));

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
    void resolve_sqlWithHostConfig_buildsDsn() {
      String datasourceId = "ds-sql-host";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "type",
              "sql",
              "host",
              "db.local",
              "port",
              5432,
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
  }

  // ─── Edge Cases ───────────────────────────────────────────────────────────────

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("returns unchanged when no label present")
    void resolve_noPlaceholder_returnsUnchanged() {
      Map<String, Object> pipelineData =
          Map.of("input", Map.of("generate", Map.of("interval", "1s", "count", 1)));

      Map<String, Object> result =
          DatasourceInjector.resolve(pipelineData, List.of(Map.of("id", "ds-1", "type", "mqtt")));

      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("returns unchanged when datasources list is empty")
    void resolve_emptyDatasources_returnsUnchanged() {
      Map<String, Object> pipelineData = Map.of("input", inputWithLabel("some-id"));

      assertSame(pipelineData, DatasourceInjector.resolve(pipelineData, List.of()));
    }

    @Test
    @DisplayName("returns unchanged when datasource ID not found")
    void resolve_unknownDatasourceId_returnsUnchanged() {
      Map<String, Object> pipelineData = Map.of("input", inputWithLabel("unknown-id"));

      Map<String, Object> result =
          DatasourceInjector.resolve(
              pipelineData, List.of(Map.of("id", "other-id", "type", "mqtt")));

      assertSame(pipelineData, result);
    }

    @Test
    @DisplayName("returns null when pipelineData is null")
    void resolve_nullPipelineData_returnsNull() {
      assertNull(DatasourceInjector.resolve(null, List.of()));
    }

    @Test
    @DisplayName("nested label (non-spec format) is not resolved")
    void resolve_nestedLabel_notResolved() {
      String datasourceId = "ds-nested";

      Map<String, Object> datasource =
          Map.of(
              "id",
              datasourceId,
              "type",
              "mqtt",
              "configuration",
              Map.of("urls", List.of("tcp://broker:1883"), "topics", List.of("t/#")));

      // label nested inside connector — this is NOT the supported format
      Map<String, Object> pipelineData =
          Map.of("input", Map.of("mqtt", Map.of("label", "${" + datasourceId + "}")));

      Map<String, Object> result = DatasourceInjector.resolve(pipelineData, List.of(datasource));

      assertSame(pipelineData, result, "nested label must not be resolved — use top-level label");
    }

    @Test
    @DisplayName("resolves correct datasource from multiple datasources")
    void resolve_multipleDatasources_resolvesCorrectOne() {
      String targetId = "target-ds";

      Map<String, Object> mqttDs =
          Map.of(
              "id",
              targetId,
              "type",
              "mqtt",
              "configuration",
              Map.of(
                  "urls", List.of("tcp://target:1883"),
                  "topics", List.of("t/#")));

      Map<String, Object> sqlDs =
          Map.of(
              "id", "other-ds",
              "type", "postgresql",
              "configuration", Map.of("dsn", "postgres://..."));

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

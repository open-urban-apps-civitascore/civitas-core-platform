/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.redpanda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PipelineInput}. */
class PipelineInputTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    PipelineInput input = new PipelineInput();
    assertNull(input.getMqtt());
    assertNull(input.getSqlRaw());
    assertNotNull(input.getAdditionalProperties());
    assertTrue(input.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenMqttSet_shouldStoreValue() {
    PipelineInput input = new PipelineInput();
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    input.setMqtt(mqtt);

    assertNotNull(input.getMqtt());
    assertEquals(List.of("tcp://broker:1883"), input.getMqtt().getUrls());
  }

  @Test
  void setters_whenSqlRawSet_shouldStoreValue() {
    PipelineInput input = new PipelineInput();
    SqlRawInput sqlRaw = new SqlRawInput();
    sqlRaw.setDriver("postgres");
    input.setSqlRaw(sqlRaw);

    assertNotNull(input.getSqlRaw());
    assertEquals("postgres", input.getSqlRaw().getDriver());
  }

  @Test
  void toApiMap_whenMqttSet_shouldContainMqttNestedMap() {
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    mqtt.setTopics(List.of("sensor/data"));
    PipelineInput input = new PipelineInput();
    input.setMqtt(mqtt);

    Map<String, Object> map = input.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> mqttMap = (Map<String, Object>) map.get("mqtt");
    assertNotNull(mqttMap);
    assertEquals(List.of("tcp://broker:1883"), mqttMap.get("urls"));
    assertEquals(List.of("sensor/data"), mqttMap.get("topics"));
    assertNull(map.get("sql_raw"));
  }

  @Test
  void toApiMap_whenSqlRawSet_shouldContainSqlRawNestedMap() {
    SqlRawInput sqlRaw = new SqlRawInput();
    sqlRaw.setDriver("postgres");
    sqlRaw.setQuery("SELECT 1");
    PipelineInput input = new PipelineInput();
    input.setSqlRaw(sqlRaw);

    Map<String, Object> map = input.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> sqlRawMap = (Map<String, Object>) map.get("sql_raw");
    assertNotNull(sqlRawMap);
    assertEquals("postgres", sqlRawMap.get("driver"));
    assertEquals("SELECT 1", sqlRawMap.get("query"));
    assertNull(map.get("mqtt"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    PipelineInput input = new PipelineInput();
    Map<String, Object> map = input.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    PipelineInput input = new PipelineInput();
    input.handleUnknownProperty("generate", Map.of("mapping", "root = {}"));

    Map<String, Object> map = input.toApiMap();
    assertNotNull(map.get("generate"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    PipelineInput input1 = new PipelineInput();
    input1.setMqtt(mqtt);
    MqttInput mqtt2 = new MqttInput();
    mqtt2.setUrls(List.of("tcp://broker:1883"));
    PipelineInput input2 = new PipelineInput();
    input2.setMqtt(mqtt2);

    assertEquals(input1, input2);
    assertEquals(input1.hashCode(), input2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    PipelineInput input = new PipelineInput();
    assertEquals(input, input);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    PipelineInput input = new PipelineInput();
    assertNotEquals(null, input);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    PipelineInput input = new PipelineInput();
    assertNotEquals("string", input);
  }

  @Test
  void equals_whenDifferentMqtt_shouldReturnFalse() {
    MqttInput mqtt1 = new MqttInput();
    mqtt1.setUrls(List.of("tcp://broker1:1883"));
    PipelineInput input1 = new PipelineInput();
    input1.setMqtt(mqtt1);

    MqttInput mqtt2 = new MqttInput();
    mqtt2.setUrls(List.of("tcp://broker2:1883"));
    PipelineInput input2 = new PipelineInput();
    input2.setMqtt(mqtt2);

    assertNotEquals(input1, input2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    PipelineInput input = new PipelineInput();
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    input.setMqtt(mqtt);

    String str = input.toString();
    assertTrue(str.contains("PipelineInput"));
    assertTrue(str.contains("mqtt="));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    SqlRawInput sqlRaw = new SqlRawInput();
    sqlRaw.setDriver("postgres");
    PipelineInput input = new PipelineInput();
    input.setSqlRaw(sqlRaw);

    String json = objectMapper.writeValueAsString(input);
    assertTrue(json.contains("\"sql_raw\""));
  }

  @Test
  void jsonDeserialization_withMqtt_shouldMapCorrectly() throws Exception {
    String json =
        """
                {
                  "mqtt": {
                    "urls": ["tcp://broker:1883"],
                    "topics": ["sensor/data"],
                    "client_id": "client-1",
                    "qos": 1
                  }
                }
                """;

    PipelineInput input = objectMapper.readValue(json, PipelineInput.class);
    assertNotNull(input.getMqtt());
    assertEquals(List.of("tcp://broker:1883"), input.getMqtt().getUrls());
    assertEquals(List.of("sensor/data"), input.getMqtt().getTopics());
    assertEquals("client-1", input.getMqtt().getClientId());
    assertEquals(1, input.getMqtt().getQos());
    assertNull(input.getSqlRaw());
  }

  @Test
  void jsonDeserialization_withSqlRaw_shouldMapCorrectly() throws Exception {
    String json =
        """
                {
                  "sql_raw": {
                    "driver": "postgres",
                    "dsn": "postgres://user:pass@host:5432/db",
                    "query": "SELECT * FROM sensors"
                  }
                }
                """;

    PipelineInput input = objectMapper.readValue(json, PipelineInput.class);
    assertNull(input.getMqtt());
    assertNotNull(input.getSqlRaw());
    assertEquals("postgres", input.getSqlRaw().getDriver());
    assertEquals("postgres://user:pass@host:5432/db", input.getSqlRaw().getDsn());
    assertEquals("SELECT * FROM sensors", input.getSqlRaw().getQuery());
  }

  @Test
  void jsonRoundTrip_withMqtt_shouldPreserveValues() throws Exception {
    MqttInput mqtt = new MqttInput();
    mqtt.setUrls(List.of("tcp://broker:1883"));
    mqtt.setTopics(List.of("sensor/#"));
    mqtt.setClientId("client-1");
    mqtt.setQos(1);
    PipelineInput original = new PipelineInput();
    original.setMqtt(mqtt);

    String json = objectMapper.writeValueAsString(original);
    PipelineInput deserialized = objectMapper.readValue(json, PipelineInput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonRoundTrip_withSqlRaw_shouldPreserveValues() throws Exception {
    SqlRawInput sqlRaw = new SqlRawInput();
    sqlRaw.setDriver("postgres");
    sqlRaw.setDsn("postgres://user:pass@host:5432/db");
    sqlRaw.setQuery("SELECT * FROM sensors");
    PipelineInput original = new PipelineInput();
    original.setSqlRaw(sqlRaw);

    String json = objectMapper.writeValueAsString(original);
    PipelineInput deserialized = objectMapper.readValue(json, PipelineInput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "generate": {"mapping": "root = {}"}
                }
                """;

    PipelineInput input = objectMapper.readValue(json, PipelineInput.class);
    assertNull(input.getMqtt());
    assertNull(input.getSqlRaw());
    assertNotNull(input.getAdditionalProperties().get("generate"));
  }
}

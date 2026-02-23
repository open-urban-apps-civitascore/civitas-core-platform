/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.redpanda;

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

/** Unit tests for {@link MqttInput}. */
class MqttInputTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    MqttInput input = new MqttInput();
    assertNull(input.getUrls());
    assertNull(input.getTopics());
    assertNull(input.getClientId());
    assertNull(input.getQos());
    assertNull(input.getConnectTimeout());
    assertNull(input.getKeepalive());
    assertNull(input.getUsername());
    assertNull(input.getPassword());
    assertNotNull(input.getAdditionalProperties());
    assertTrue(input.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.setTopics(List.of("sensor/data"));
    input.setClientId("client-1");
    input.setQos(1);
    input.setConnectTimeout("30s");
    input.setKeepalive(60);
    input.setUsername("user");
    input.setPassword("pass");

    assertEquals(List.of("tcp://broker:1883"), input.getUrls());
    assertEquals(List.of("sensor/data"), input.getTopics());
    assertEquals("client-1", input.getClientId());
    assertEquals(1, input.getQos());
    assertEquals("30s", input.getConnectTimeout());
    assertEquals(60, input.getKeepalive());
    assertEquals("user", input.getUsername());
    assertEquals("pass", input.getPassword());
  }

  @Test
  void toApiMap_whenAllFieldsSet_shouldContainAll() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.setTopics(List.of("sensor/data"));
    input.setClientId("client-1");
    input.setQos(1);
    input.setConnectTimeout("30s");
    input.setKeepalive(60);
    input.setUsername("user");
    input.setPassword("pass");

    Map<String, Object> map = input.toApiMap();
    assertEquals(List.of("tcp://broker:1883"), map.get("urls"));
    assertEquals(List.of("sensor/data"), map.get("topics"));
    assertEquals("client-1", map.get("client_id"));
    assertEquals(1, map.get("qos"));
    assertEquals("30s", map.get("connect_timeout"));
    assertEquals(60, map.get("keepalive"));
    assertEquals("user", map.get("username"));
    assertEquals("pass", map.get("password"));
    assertEquals(8, map.size());
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    MqttInput input = new MqttInput();
    Map<String, Object> map = input.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_whenPartialFields_shouldOmitNull() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.setTopics(List.of("sensor/#"));

    Map<String, Object> map = input.toApiMap();
    assertEquals(2, map.size());
    assertNull(map.get("client_id"));
    assertNull(map.get("qos"));
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.handleUnknownProperty("clean_session", true);

    Map<String, Object> map = input.toApiMap();
    assertEquals(List.of("tcp://broker:1883"), map.get("urls"));
    assertEquals(true, map.get("clean_session"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    MqttInput input1 = new MqttInput();
    input1.setUrls(List.of("tcp://broker:1883"));
    input1.setTopics(List.of("sensor/data"));
    MqttInput input2 = new MqttInput();
    input2.setUrls(List.of("tcp://broker:1883"));
    input2.setTopics(List.of("sensor/data"));

    assertEquals(input1, input2);
    assertEquals(input1.hashCode(), input2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    MqttInput input = new MqttInput();
    assertEquals(input, input);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    MqttInput input = new MqttInput();
    assertNotEquals(null, input);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    MqttInput input = new MqttInput();
    assertNotEquals("string", input);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    MqttInput input1 = new MqttInput();
    input1.setQos(0);
    MqttInput input2 = new MqttInput();
    input2.setQos(1);

    assertNotEquals(input1, input2);
  }

  @Test
  void toString_shouldNotLeakPassword() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.setPassword("sensitive-password");

    String str = input.toString();
    assertTrue(str.contains("MqttInput"));
    assertTrue(str.contains("[PRESENT]"));
    assertTrue(!str.contains("sensitive-password"));
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    MqttInput input = new MqttInput();
    input.setUrls(List.of("tcp://broker:1883"));
    input.setClientId("client-1");

    String str = input.toString();
    assertTrue(str.contains("MqttInput"));
    assertTrue(str.contains("client-1"));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    MqttInput input = new MqttInput();
    input.setClientId("client-1");
    input.setConnectTimeout("30s");

    String json = objectMapper.writeValueAsString(input);
    assertTrue(json.contains("\"client_id\""));
    assertTrue(json.contains("\"connect_timeout\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapSnakeCaseFields() throws Exception {
    String json =
        """
                {
                  "urls": ["tcp://broker:1883"],
                  "topics": ["sensor/data"],
                  "client_id": "client-1",
                  "qos": 1,
                  "connect_timeout": "30s",
                  "keepalive": 60,
                  "username": "user",
                  "password": "pass"
                }
                """;

    MqttInput input = objectMapper.readValue(json, MqttInput.class);
    assertEquals(List.of("tcp://broker:1883"), input.getUrls());
    assertEquals(List.of("sensor/data"), input.getTopics());
    assertEquals("client-1", input.getClientId());
    assertEquals(1, input.getQos());
    assertEquals("30s", input.getConnectTimeout());
    assertEquals(60, input.getKeepalive());
    assertEquals("user", input.getUsername());
    assertEquals("pass", input.getPassword());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    MqttInput original = new MqttInput();
    original.setUrls(List.of("tcp://broker:1883", "tcp://broker2:1883"));
    original.setTopics(List.of("sensor/#"));
    original.setClientId("client-1");
    original.setQos(2);
    original.setConnectTimeout("30s");
    original.setKeepalive(60);
    original.setUsername("user");
    original.setPassword("pass");

    String json = objectMapper.writeValueAsString(original);
    MqttInput deserialized = objectMapper.readValue(json, MqttInput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "urls": ["tcp://broker:1883"],
                  "will_active": true
                }
                """;

    MqttInput input = objectMapper.readValue(json, MqttInput.class);
    assertEquals(List.of("tcp://broker:1883"), input.getUrls());
    assertEquals(true, input.getAdditionalProperties().get("will_active"));
  }
}

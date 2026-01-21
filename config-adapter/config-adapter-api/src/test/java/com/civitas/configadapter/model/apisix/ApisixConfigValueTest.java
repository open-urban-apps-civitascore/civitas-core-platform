/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.configadapter.model.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for ApisixConfigValue */
class ApisixConfigValueTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldCreateEmptyData() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNotNull(value.data());
    assertTrue(value.data().isEmpty());
  }

  @Test
  void constructor_whenValidMap_shouldPopulateData() {
    Map<String, Object> data =
        Map.of("type", "roundrobin", "nodes", Map.of("backend1:8080", 1, "backend2:8080", 2));

    ApisixConfigValue value = new ApisixConfigValue(data);
    assertNotNull(value.data());
    assertEquals("roundrobin", value.getType());
    assertNotNull(value.getNodes());
    assertEquals(2, value.getNodes().size());
  }

  @Test
  void constructor_whenNullMap_shouldCreateEmptyData() {
    ApisixConfigValue value = new ApisixConfigValue(null);
    assertNotNull(value.data());
    assertTrue(value.data().isEmpty());
  }

  @Test
  void setProperty_whenCalled_shouldStoreValue() {
    ApisixConfigValue value = new ApisixConfigValue();
    value.setProperty("type", "chash");
    value.setProperty("nodes", Map.of("backend:8080", 1));

    assertEquals("chash", value.get("type"));
    assertNotNull(value.get("nodes"));
  }

  @Test
  void getType_whenTypeExists_shouldReturnType() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    assertEquals("roundrobin", value.getType());
  }

  @Test
  void getType_whenTypeNotSet_shouldReturnNull() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNull(value.getType());
  }

  @Test
  void getNodes_whenNodesExist_shouldReturnNodes() {
    Map<String, Object> nodes = Map.of("backend1:8080", 1, "backend2:8080", 2);
    ApisixConfigValue value = new ApisixConfigValue(Map.of("nodes", nodes));
    assertEquals(nodes, value.getNodes());
  }

  @Test
  void getNodes_whenNodesNotSet_shouldReturnNull() {
    ApisixConfigValue value = new ApisixConfigValue();
    assertNull(value.getNodes());
  }

  @Test
  void get_whenKeyExists_shouldReturnValue() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "ewma", "scheme", "https"));
    assertEquals("ewma", value.get("type"));
    assertEquals("https", value.get("scheme"));
  }

  @Test
  void get_whenKeyNotExists_shouldReturnNull() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "ewma"));
    assertNull(value.get("nonexistent"));
  }

  @Test
  void has_whenKeyExists_shouldReturnTrue() {
    ApisixConfigValue value =
        new ApisixConfigValue(Map.of("type", "roundrobin", "nodes", Map.of("backend:8080", 1)));
    assertTrue(value.has("type"));
    assertTrue(value.has("nodes"));
  }

  @Test
  void has_whenKeyNotExists_shouldReturnFalse() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    assertFalse(value.has("scheme"));
    assertFalse(value.has("timeout"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    Map<String, Object> data = Map.of("type", "roundrobin");
    ApisixConfigValue value1 = new ApisixConfigValue(data);
    ApisixConfigValue value2 = new ApisixConfigValue(data);

    assertEquals(value1, value2);
    assertEquals(value1.hashCode(), value2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    assertEquals(value, value);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    assertNotEquals(null, value);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    assertNotEquals("string", value);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ApisixConfigValue value1 = new ApisixConfigValue(Map.of("type", "roundrobin"));
    ApisixConfigValue value2 = new ApisixConfigValue(Map.of("type", "chash"));

    assertNotEquals(value1, value2);
  }

  @Test
  void toString_whenCalled_shouldContainClassName() {
    ApisixConfigValue value = new ApisixConfigValue(Map.of("type", "roundrobin"));
    String str = value.toString();
    assertTrue(str.contains("ApisixConfigValue"));
    assertTrue(str.contains("type"));
    assertTrue(str.contains("roundrobin"));
  }

  @Test
  void jsonSerialization_whenValidData_shouldProduceValidJson() throws Exception {
    Map<String, Object> data = new HashMap<>();
    data.put("type", "roundrobin");
    data.put("nodes", Map.of("backend1:8080", 1, "backend2:8080", 2));
    data.put("scheme", "http");

    ApisixConfigValue value = new ApisixConfigValue(data);

    String json = objectMapper.writeValueAsString(value);
    assertNotNull(json);
    assertTrue(json.contains("roundrobin"));
    assertTrue(json.contains("backend1:8080"));
  }

  @Test
  void jsonDeserialization_whenValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "roundrobin",
                  "scheme": "https"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("roundrobin", value.getType());
    assertEquals("https", value.get("scheme"));
  }

  @Test
  void jsonDeserialization_whenMinimalJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "roundrobin"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("roundrobin", value.getType());
  }

  @Test
  void jsonDeserialization_whenGrpcScheme_shouldParseCorrectly() throws Exception {
    String json =
        """
                {
                  "resourceType": "apisix-upstream",
                  "type": "least_conn",
                  "scheme": "grpc"
                }
                """;

    ApisixConfigValue value = objectMapper.readValue(json, ApisixConfigValue.class);

    assertEquals("least_conn", value.getType());
    assertEquals("grpc", value.get("scheme"));
  }

  @Test
  void getType_whenAllLoadBalancingTypes_shouldReturnCorrectly() {
    String[] types = {"roundrobin", "chash", "ewma", "least_conn"};

    for (String type : types) {
      ApisixConfigValue value = new ApisixConfigValue(Map.of("type", type));
      assertEquals(type, value.getType());
    }
  }
}

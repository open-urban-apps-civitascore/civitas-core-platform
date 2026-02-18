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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BasicAuth}. */
class BasicAuthTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    BasicAuth auth = new BasicAuth();
    assertNull(auth.getUsername());
    assertNull(auth.getPassword());
    assertNotNull(auth.getAdditionalProperties());
    assertTrue(auth.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");

    assertEquals("admin", auth.getUsername());
    assertEquals("secret", auth.getPassword());
  }

  @Test
  void toApiMap_whenAllFieldsSet_shouldContainAll() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");

    Map<String, Object> map = auth.toApiMap();
    assertEquals("admin", map.get("username"));
    assertEquals("secret", map.get("password"));
    assertEquals(2, map.size());
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    BasicAuth auth = new BasicAuth();
    Map<String, Object> map = auth.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_whenPartialFields_shouldOmitNull() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");

    Map<String, Object> map = auth.toApiMap();
    assertEquals(1, map.size());
    assertEquals("admin", map.get("username"));
    assertNull(map.get("password"));
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.handleUnknownProperty("custom_key", "custom_value");

    Map<String, Object> map = auth.toApiMap();
    assertEquals("admin", map.get("username"));
    assertEquals("custom_value", map.get("custom_key"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    BasicAuth auth1 = new BasicAuth();
    auth1.setUsername("admin");
    auth1.setPassword("secret");
    BasicAuth auth2 = new BasicAuth();
    auth2.setUsername("admin");
    auth2.setPassword("secret");

    assertEquals(auth1, auth2);
    assertEquals(auth1.hashCode(), auth2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    assertEquals(auth, auth);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    BasicAuth auth = new BasicAuth();
    assertNotEquals(null, auth);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    BasicAuth auth = new BasicAuth();
    assertNotEquals("string", auth);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    BasicAuth auth1 = new BasicAuth();
    auth1.setUsername("admin");
    BasicAuth auth2 = new BasicAuth();
    auth2.setUsername("other");

    assertNotEquals(auth1, auth2);
  }

  @Test
  void equals_whenDifferentAdditionalProperties_shouldReturnFalse() {
    BasicAuth auth1 = new BasicAuth();
    auth1.setUsername("admin");
    BasicAuth auth2 = new BasicAuth();
    auth2.setUsername("admin");
    auth2.handleUnknownProperty("extra", "val");

    assertNotEquals(auth1, auth2);
  }

  @Test
  void toString_shouldNotLeakPassword() {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("sensitive-password");

    String str = auth.toString();
    assertTrue(str.contains("BasicAuth"));
    assertTrue(str.contains("admin"));
    assertTrue(str.contains("[PRESENT]"));
    assertTrue(!str.contains("sensitive-password"));
  }

  @Test
  void jsonSerialization_shouldProduceValidJson() throws Exception {
    BasicAuth auth = new BasicAuth();
    auth.setUsername("admin");
    auth.setPassword("secret");

    String json = objectMapper.writeValueAsString(auth);
    assertNotNull(json);
    assertTrue(json.contains("\"username\""));
    assertTrue(json.contains("\"password\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "username": "admin",
                  "password": "secret"
                }
                """;

    BasicAuth auth = objectMapper.readValue(json, BasicAuth.class);
    assertEquals("admin", auth.getUsername());
    assertEquals("secret", auth.getPassword());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    BasicAuth original = new BasicAuth();
    original.setUsername("admin");
    original.setPassword("secret");

    String json = objectMapper.writeValueAsString(original);
    BasicAuth deserialized = objectMapper.readValue(json, BasicAuth.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "username": "admin",
                  "password": "secret",
                  "extra_field": "extra_value"
                }
                """;

    BasicAuth auth = objectMapper.readValue(json, BasicAuth.class);
    assertEquals("admin", auth.getUsername());
    assertEquals("extra_value", auth.getAdditionalProperties().get("extra_field"));
  }
}

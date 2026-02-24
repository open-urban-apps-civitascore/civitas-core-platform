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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqlRawInput}. */
class SqlRawInputTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    SqlRawInput input = new SqlRawInput();
    assertNull(input.getDriver());
    assertNull(input.getDsn());
    assertNull(input.getQuery());
    assertNull(input.getArgsMapping());
    assertNotNull(input.getAdditionalProperties());
    assertTrue(input.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    SqlRawInput input = new SqlRawInput();
    input.setDriver("postgres");
    input.setDsn("postgres://user:pass@host:5432/db");
    input.setQuery("SELECT * FROM sensors");
    input.setArgsMapping("root = []");

    assertEquals("postgres", input.getDriver());
    assertEquals("postgres://user:pass@host:5432/db", input.getDsn());
    assertEquals("SELECT * FROM sensors", input.getQuery());
    assertEquals("root = []", input.getArgsMapping());
  }

  @Test
  void toApiMap_whenAllFieldsSet_shouldContainAll() {
    SqlRawInput input = new SqlRawInput();
    input.setDriver("postgres");
    input.setDsn("postgres://user:pass@host:5432/db");
    input.setQuery("SELECT * FROM sensors");
    input.setArgsMapping("root = []");

    Map<String, Object> map = input.toApiMap();
    assertEquals("postgres", map.get("driver"));
    assertEquals("postgres://user:pass@host:5432/db", map.get("dsn"));
    assertEquals("SELECT * FROM sensors", map.get("query"));
    assertEquals("root = []", map.get("args_mapping"));
    assertEquals(4, map.size());
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    SqlRawInput input = new SqlRawInput();
    Map<String, Object> map = input.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_whenPartialFields_shouldOmitNull() {
    SqlRawInput input = new SqlRawInput();
    input.setDriver("postgres");
    input.setQuery("SELECT 1");

    Map<String, Object> map = input.toApiMap();
    assertEquals(2, map.size());
    assertEquals("postgres", map.get("driver"));
    assertEquals("SELECT 1", map.get("query"));
    assertNull(map.get("dsn"));
    assertNull(map.get("args_mapping"));
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    SqlRawInput input = new SqlRawInput();
    input.setDriver("postgres");
    input.handleUnknownProperty("init_statement", "CREATE TABLE IF NOT EXISTS ...");

    Map<String, Object> map = input.toApiMap();
    assertEquals("postgres", map.get("driver"));
    assertEquals("CREATE TABLE IF NOT EXISTS ...", map.get("init_statement"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    SqlRawInput input1 = new SqlRawInput();
    input1.setDriver("postgres");
    input1.setQuery("SELECT 1");
    SqlRawInput input2 = new SqlRawInput();
    input2.setDriver("postgres");
    input2.setQuery("SELECT 1");

    assertEquals(input1, input2);
    assertEquals(input1.hashCode(), input2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    SqlRawInput input = new SqlRawInput();
    assertEquals(input, input);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    SqlRawInput input = new SqlRawInput();
    assertNotEquals(null, input);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    SqlRawInput input = new SqlRawInput();
    assertNotEquals("string", input);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    SqlRawInput input1 = new SqlRawInput();
    input1.setDriver("postgres");
    SqlRawInput input2 = new SqlRawInput();
    input2.setDriver("mysql");

    assertNotEquals(input1, input2);
  }

  @Test
  void toString_shouldNotLeakDsn() {
    SqlRawInput input = new SqlRawInput();
    input.setDriver("postgres");
    input.setDsn("postgres://user:secret@host:5432/db");
    input.setQuery("SELECT * FROM sensors");

    String str = input.toString();
    assertTrue(str.contains("SqlRawInput"));
    assertTrue(str.contains("postgres"));
    assertTrue(str.contains("[PRESENT]"));
    assertTrue(!str.contains("secret"));
  }

  @Test
  void jsonSerialization_shouldUseSnakeCaseForAnnotatedFields() throws Exception {
    SqlRawInput input = new SqlRawInput();
    input.setArgsMapping("root = []");

    String json = objectMapper.writeValueAsString(input);
    assertTrue(json.contains("\"args_mapping\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldMapSnakeCaseFields() throws Exception {
    String json =
        """
                {
                  "driver": "postgres",
                  "dsn": "postgres://user:pass@host:5432/db",
                  "query": "SELECT * FROM sensors",
                  "args_mapping": "root = []"
                }
                """;

    SqlRawInput input = objectMapper.readValue(json, SqlRawInput.class);
    assertEquals("postgres", input.getDriver());
    assertEquals("postgres://user:pass@host:5432/db", input.getDsn());
    assertEquals("SELECT * FROM sensors", input.getQuery());
    assertEquals("root = []", input.getArgsMapping());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    SqlRawInput original = new SqlRawInput();
    original.setDriver("postgres");
    original.setDsn("postgres://user:pass@host:5432/db");
    original.setQuery("SELECT * FROM sensors WHERE id > $1");
    original.setArgsMapping("root = [this.last_id]");

    String json = objectMapper.writeValueAsString(original);
    SqlRawInput deserialized = objectMapper.readValue(json, SqlRawInput.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "driver": "postgres",
                  "unsafe_dynamic_query": true
                }
                """;

    SqlRawInput input = objectMapper.readValue(json, SqlRawInput.class);
    assertEquals("postgres", input.getDriver());
    assertEquals(true, input.getAdditionalProperties().get("unsafe_dynamic_query"));
  }
}

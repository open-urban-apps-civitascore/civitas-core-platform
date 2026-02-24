/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.apisix.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ResponseFilter}. */
class ResponseFilterTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void toApiMap_withRegexAndReplace_shouldContainBoth() {
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("old-pattern");
    filter.setReplace("new-value");

    Map<String, Object> map = filter.toApiMap();
    assertEquals("old-pattern", map.get("regex"));
    assertEquals("new-value", map.get("replace"));
  }

  @Test
  void toApiMap_withNullRegex_shouldNotContainRegex() {
    ResponseFilter filter = new ResponseFilter();
    filter.setReplace("new-value");

    Map<String, Object> map = filter.toApiMap();
    assertNull(map.get("regex"));
    assertEquals("new-value", map.get("replace"));
  }

  @Test
  void toApiMap_withNullReplace_shouldNotContainReplace() {
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("pattern");

    Map<String, Object> map = filter.toApiMap();
    assertEquals("pattern", map.get("regex"));
    assertNull(map.get("replace"));
  }

  @Test
  void toApiMap_withAllNull_shouldReturnEmptyMap() {
    ResponseFilter filter = new ResponseFilter();
    Map<String, Object> map = filter.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("pattern");

    Map<String, Object> map = filter.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void jsonRoundtrip_shouldPreserveData() throws Exception {
    ResponseFilter original = new ResponseFilter();
    original.setRegex("pattern");
    original.setReplace("replacement");

    String json = objectMapper.writeValueAsString(original);
    ResponseFilter deserialized = objectMapper.readValue(json, ResponseFilter.class);

    assertEquals(original, deserialized);
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ResponseFilter f1 = new ResponseFilter();
    f1.setRegex("pattern");
    f1.setReplace("replacement");
    ResponseFilter f2 = new ResponseFilter();
    f2.setRegex("pattern");
    f2.setReplace("replacement");

    assertEquals(f1, f2);
    assertEquals(f1.hashCode(), f2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ResponseFilter f1 = new ResponseFilter();
    f1.setRegex("a");
    ResponseFilter f2 = new ResponseFilter();
    f2.setRegex("b");

    assertNotEquals(f1, f2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ResponseFilter filter = new ResponseFilter();
    assertEquals(filter, filter);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ResponseFilter filter = new ResponseFilter();
    assertNotEquals(null, filter);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ResponseFilter filter = new ResponseFilter();
    assertNotEquals("string", filter);
  }

  @Test
  void toString_shouldContainClassName() {
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("pattern");
    String str = filter.toString();
    assertTrue(str.contains("ResponseFilter"));
    assertTrue(str.contains("pattern"));
  }

  @Test
  void handleUnknownProperty_shouldStoreInAdditionalProperties() {
    ResponseFilter filter = new ResponseFilter();
    filter.handleUnknownProperty("scope", "global");

    assertEquals(Map.of("scope", "global"), filter.getAdditionalProperties());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("pattern");
    filter.handleUnknownProperty("scope", "global");

    Map<String, Object> map = filter.toApiMap();
    assertEquals("pattern", map.get("regex"));
    assertEquals("global", map.get("scope"));
  }

  @Test
  void jsonRoundtrip_withUnknownProperties_shouldPreserveAll() throws Exception {
    String json =
        """
                {"regex": "old", "replace": "new", "unknownField": "preserved"}
                """;

    ResponseFilter deserialized = objectMapper.readValue(json, ResponseFilter.class);
    assertEquals("old", deserialized.getRegex());
    assertEquals("new", deserialized.getReplace());
    assertEquals("preserved", deserialized.getAdditionalProperties().get("unknownField"));
  }

  @Test
  void equals_withDifferentAdditionalProperties_shouldReturnFalse() {
    ResponseFilter f1 = new ResponseFilter();
    f1.setRegex("pattern");
    f1.handleUnknownProperty("key", "value1");

    ResponseFilter f2 = new ResponseFilter();
    f2.setRegex("pattern");
    f2.handleUnknownProperty("key", "value2");

    assertNotEquals(f1, f2);
  }
}

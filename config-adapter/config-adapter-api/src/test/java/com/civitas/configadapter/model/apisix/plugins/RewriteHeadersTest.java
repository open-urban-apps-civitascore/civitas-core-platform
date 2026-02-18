/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.apisix.plugins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RewriteHeaders}. */
class RewriteHeadersTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void toApiMap_withSetAddRemove_shouldContainAll() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));
    headers.setAdd(Map.of("X-Extra", "extra"));
    headers.setRemove(List.of("X-Old"));

    Map<String, Object> map = headers.toApiMap();
    assertEquals(Map.of("X-Custom", "value"), map.get("set"));
    assertEquals(Map.of("X-Extra", "extra"), map.get("add"));
    assertEquals(List.of("X-Old"), map.get("remove"));
  }

  @Test
  void toApiMap_withNullSet_shouldNotContainSet() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setAdd(Map.of("X-Extra", "extra"));

    Map<String, Object> map = headers.toApiMap();
    assertNull(map.get("set"));
    assertEquals(Map.of("X-Extra", "extra"), map.get("add"));
  }

  @Test
  void toApiMap_withNullAdd_shouldNotContainAdd() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));

    Map<String, Object> map = headers.toApiMap();
    assertNull(map.get("add"));
    assertEquals(Map.of("X-Custom", "value"), map.get("set"));
  }

  @Test
  void toApiMap_withNullRemove_shouldNotContainRemove() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));

    Map<String, Object> map = headers.toApiMap();
    assertNull(map.get("remove"));
  }

  @Test
  void toApiMap_withAllNull_shouldReturnEmptyMap() {
    RewriteHeaders headers = new RewriteHeaders();
    Map<String, Object> map = headers.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_withEmptyMapsAndLists_shouldContainThem() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(new LinkedHashMap<>());
    headers.setAdd(new LinkedHashMap<>());
    headers.setRemove(List.of());

    Map<String, Object> map = headers.toApiMap();
    assertEquals(Map.of(), map.get("set"));
    assertEquals(Map.of(), map.get("add"));
    assertEquals(List.of(), map.get("remove"));
  }

  @Test
  void toApiMap_withAdditionalProperties_shouldIncludeThem() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));
    headers.handleUnknownProperty("custom", "extra");

    Map<String, Object> map = headers.toApiMap();
    assertEquals("extra", map.get("custom"));
  }

  @Test
  void toApiMap_additionalPropertiesShouldNotOverrideTypedFields() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Typed", "typed"));
    headers.handleUnknownProperty("set", Map.of("X-Overridden", "overridden"));

    Map<String, Object> map = headers.toApiMap();
    assertEquals(Map.of("X-Typed", "typed"), map.get("set"));
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));

    Map<String, Object> map = headers.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void jsonRoundtrip_shouldPreserveData() throws Exception {
    RewriteHeaders original = new RewriteHeaders();
    original.setSet(Map.of("X-Custom", "value"));
    original.setRemove(List.of("X-Old"));

    String json = objectMapper.writeValueAsString(original);
    RewriteHeaders deserialized = objectMapper.readValue(json, RewriteHeaders.class);

    assertEquals(original, deserialized);
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    RewriteHeaders h1 = new RewriteHeaders();
    h1.setSet(Map.of("X-Custom", "value"));
    RewriteHeaders h2 = new RewriteHeaders();
    h2.setSet(Map.of("X-Custom", "value"));

    assertEquals(h1, h2);
    assertEquals(h1.hashCode(), h2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    RewriteHeaders h1 = new RewriteHeaders();
    h1.setSet(Map.of("X-A", "a"));
    RewriteHeaders h2 = new RewriteHeaders();
    h2.setSet(Map.of("X-B", "b"));

    assertNotEquals(h1, h2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    RewriteHeaders headers = new RewriteHeaders();
    assertEquals(headers, headers);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    RewriteHeaders headers = new RewriteHeaders();
    assertNotEquals(null, headers);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    RewriteHeaders headers = new RewriteHeaders();
    assertNotEquals("string", headers);
  }

  @Test
  void toString_shouldContainClassName() {
    RewriteHeaders headers = new RewriteHeaders();
    assertTrue(headers.toString().contains("RewriteHeaders"));
  }
}

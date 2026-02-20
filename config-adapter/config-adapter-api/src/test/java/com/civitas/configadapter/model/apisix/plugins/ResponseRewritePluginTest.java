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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ResponseRewritePlugin}. */
class ResponseRewritePluginTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void toApiMap_withAllFields_shouldContainAllEntries() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(200);
    plugin.setBody("{\"status\":\"ok\"}");
    plugin.setBodyBase64(false);

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));
    plugin.setHeaders(headers);

    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("old");
    filter.setReplace("new");
    plugin.setFilters(List.of(filter));

    Map<String, Object> map = plugin.toApiMap();
    assertEquals(200, map.get("status_code"));
    assertEquals("{\"status\":\"ok\"}", map.get("body"));
    assertEquals(false, map.get("body_base64"));
    assertNotNull(map.get("headers"));
    assertNotNull(map.get("filters"));
  }

  @Test
  void toApiMap_withNullHeaders_shouldNotContainHeaders() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(404);

    Map<String, Object> map = plugin.toApiMap();
    assertEquals(404, map.get("status_code"));
    assertNull(map.get("headers"));
  }

  @Test
  void toApiMap_withNullFilters_shouldNotContainFilters() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setBody("test");

    Map<String, Object> map = plugin.toApiMap();
    assertNull(map.get("filters"));
  }

  @Test
  void toApiMap_withAdditionalProperties_shouldIncludeThem() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(200);
    plugin.handleUnknownProperty("vars", List.of("arg_version", "==", "v2"));

    Map<String, Object> map = plugin.toApiMap();
    assertEquals(200, map.get("status_code"));
    assertNotNull(map.get("vars"));
  }

  @Test
  void toApiMap_additionalPropertiesShouldNotOverrideTypedFields() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(200);
    plugin.handleUnknownProperty("status_code", 500);

    Map<String, Object> map = plugin.toApiMap();
    assertEquals(200, map.get("status_code"));
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(200);

    Map<String, Object> map = plugin.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void toApiMap_filterConversion_shouldDelegateToResponseFilter() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    ResponseFilter filter = new ResponseFilter();
    filter.setRegex("pattern");
    filter.setReplace("replacement");
    plugin.setFilters(List.of(filter));

    Map<String, Object> map = plugin.toApiMap();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> filters = (List<Map<String, Object>>) map.get("filters");
    assertNotNull(filters);
    assertEquals(1, filters.size());
    assertEquals("pattern", filters.get(0).get("regex"));
    assertEquals("replacement", filters.get(0).get("replace"));
  }

  @Test
  void jsonRoundtrip_shouldPreserveData() throws Exception {
    ResponseRewritePlugin original = new ResponseRewritePlugin();
    original.setStatusCode(201);
    original.setBody("created");
    original.setBodyBase64(false);

    String json = objectMapper.writeValueAsString(original);
    ResponseRewritePlugin deserialized = objectMapper.readValue(json, ResponseRewritePlugin.class);

    assertEquals(original, deserialized);
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ResponseRewritePlugin p1 = new ResponseRewritePlugin();
    p1.setStatusCode(200);
    ResponseRewritePlugin p2 = new ResponseRewritePlugin();
    p2.setStatusCode(200);

    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ResponseRewritePlugin p1 = new ResponseRewritePlugin();
    p1.setStatusCode(200);
    ResponseRewritePlugin p2 = new ResponseRewritePlugin();
    p2.setStatusCode(404);

    assertNotEquals(p1, p2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    assertEquals(plugin, plugin);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    assertNotEquals(null, plugin);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    assertNotEquals("string", plugin);
  }

  @Test
  void toString_shouldContainClassName() {
    ResponseRewritePlugin plugin = new ResponseRewritePlugin();
    plugin.setStatusCode(200);
    String str = plugin.toString();
    assertTrue(str.contains("ResponseRewritePlugin"));
    assertTrue(str.contains("200"));
  }
}

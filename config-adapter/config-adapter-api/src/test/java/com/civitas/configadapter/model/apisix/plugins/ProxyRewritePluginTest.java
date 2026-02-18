/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
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

/** Unit tests for {@link ProxyRewritePlugin}. */
class ProxyRewritePluginTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void toApiMap_withAllFields_shouldContainAllEntries() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/users");
    plugin.setRegexUri(List.of("/api/v1/(.*)", "/$1"));
    plugin.setHost("backend.example.com");

    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Custom", "value"));
    plugin.setHeaders(headers);

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("/users", map.get("uri"));
    assertEquals(List.of("/api/v1/(.*)", "/$1"), map.get("regex_uri"));
    assertEquals("backend.example.com", map.get("host"));
    assertNotNull(map.get("headers"));
  }

  @Test
  void toApiMap_withNullHeaders_shouldNotContainHeaders() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/test");

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("/test", map.get("uri"));
    assertNull(map.get("headers"));
  }

  @Test
  void toApiMap_withAdditionalProperties_shouldIncludeThem() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/test");
    plugin.handleUnknownProperty("custom_key", "custom_value");

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("/test", map.get("uri"));
    assertEquals("custom_value", map.get("custom_key"));
  }

  @Test
  void toApiMap_additionalPropertiesShouldNotOverrideTypedFields() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/typed");
    plugin.handleUnknownProperty("uri", "/overridden");

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("/typed", map.get("uri"));
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/test");

    Map<String, Object> map = plugin.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void toApiMap_withNestedHeaders_shouldRecursivelyConvert() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    RewriteHeaders headers = new RewriteHeaders();
    headers.setSet(Map.of("X-Header", "value"));
    headers.setRemove(List.of("X-Remove"));
    plugin.setHeaders(headers);

    Map<String, Object> map = plugin.toApiMap();
    @SuppressWarnings("unchecked")
    Map<String, Object> headersMap = (Map<String, Object>) map.get("headers");
    assertNotNull(headersMap);
    assertEquals(Map.of("X-Header", "value"), headersMap.get("set"));
    assertEquals(List.of("X-Remove"), headersMap.get("remove"));
  }

  @Test
  void jsonRoundtrip_shouldPreserveData() throws Exception {
    ProxyRewritePlugin original = new ProxyRewritePlugin();
    original.setUri("/test");
    original.setRegexUri(List.of("/api/(.*)", "/$1"));
    original.setHost("backend.local");

    String json = objectMapper.writeValueAsString(original);
    ProxyRewritePlugin deserialized = objectMapper.readValue(json, ProxyRewritePlugin.class);

    assertEquals(original, deserialized);
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ProxyRewritePlugin p1 = new ProxyRewritePlugin();
    p1.setUri("/test");
    ProxyRewritePlugin p2 = new ProxyRewritePlugin();
    p2.setUri("/test");

    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ProxyRewritePlugin p1 = new ProxyRewritePlugin();
    p1.setUri("/a");
    ProxyRewritePlugin p2 = new ProxyRewritePlugin();
    p2.setUri("/b");

    assertNotEquals(p1, p2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    assertEquals(plugin, plugin);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    assertNotEquals(null, plugin);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    assertNotEquals("string", plugin);
  }

  @Test
  void toString_shouldContainClassName() {
    ProxyRewritePlugin plugin = new ProxyRewritePlugin();
    plugin.setUri("/test");
    String str = plugin.toString();
    assertTrue(str.contains("ProxyRewritePlugin"));
    assertTrue(str.contains("/test"));
  }
}

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link ServerlessFunctionPlugin}. */
class ServerlessFunctionPluginTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void toApiMap_withPhaseAndFunctions_shouldContainBoth() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    plugin.setPhase("rewrite");
    plugin.setFunctions(List.of("return function() ngx.log(ngx.WARN, 'hello') end"));

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("rewrite", map.get("phase"));
    assertEquals(List.of("return function() ngx.log(ngx.WARN, 'hello') end"), map.get("functions"));
  }

  @Test
  void toApiMap_withAdditionalProperties_shouldIncludeThem() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    plugin.setPhase("rewrite");
    plugin.handleUnknownProperty("custom_key", "custom_value");

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("rewrite", map.get("phase"));
    assertEquals("custom_value", map.get("custom_key"));
  }

  @Test
  void toApiMap_additionalPropertiesShouldNotOverrideTypedFields() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    plugin.setPhase("rewrite");
    plugin.handleUnknownProperty("phase", "log");

    Map<String, Object> map = plugin.toApiMap();
    assertEquals("rewrite", map.get("phase"));
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    plugin.setPhase("rewrite");

    Map<String, Object> map = plugin.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyUnmodifiableMap() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    Map<String, Object> map = plugin.toApiMap();
    assertTrue(map.isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> map.put("key", "value"));
  }

  @Test
  void jsonRoundtrip_shouldPreserveData() throws Exception {
    ServerlessFunctionPlugin original = new ServerlessFunctionPlugin();
    original.setPhase("log");
    original.setFunctions(List.of("return function() end"));

    String json = objectMapper.writeValueAsString(original);
    ServerlessFunctionPlugin deserialized =
        objectMapper.readValue(json, ServerlessFunctionPlugin.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCapture() throws Exception {
    String json =
        """
        {
          "phase": "rewrite",
          "functions": ["return function() end"],
          "unknown_field": "captured"
        }
        """;

    ServerlessFunctionPlugin plugin = objectMapper.readValue(json, ServerlessFunctionPlugin.class);

    assertEquals("rewrite", plugin.getPhase());
    assertNotNull(plugin.getAdditionalProperties());
    assertEquals("captured", plugin.getAdditionalProperties().get("unknown_field"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ServerlessFunctionPlugin p1 = new ServerlessFunctionPlugin();
    p1.setPhase("rewrite");
    p1.setFunctions(List.of("return function() end"));
    ServerlessFunctionPlugin p2 = new ServerlessFunctionPlugin();
    p2.setPhase("rewrite");
    p2.setFunctions(List.of("return function() end"));

    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ServerlessFunctionPlugin p1 = new ServerlessFunctionPlugin();
    p1.setPhase("rewrite");
    ServerlessFunctionPlugin p2 = new ServerlessFunctionPlugin();
    p2.setPhase("log");

    assertNotEquals(p1, p2);
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    assertEquals(plugin, plugin);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    assertNotEquals(null, plugin);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    assertNotEquals("string", plugin);
  }

  @Test
  void toString_shouldContainClassName() {
    ServerlessFunctionPlugin plugin = new ServerlessFunctionPlugin();
    plugin.setPhase("rewrite");
    String str = plugin.toString();
    assertTrue(str.contains("ServerlessFunctionPlugin"));
    assertTrue(str.contains("rewrite"));
  }
}

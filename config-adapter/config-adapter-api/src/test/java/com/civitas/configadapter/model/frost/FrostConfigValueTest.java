/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.model.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrostConfigValueTest {

  @Nested
  @DisplayName("toApiMap")
  class ToApiMap {

    @Test
    @DisplayName("includes all non-null fields")
    void shouldIncludeAllSetFields() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Temperature Sensor");
      config.setDescription("A sensor");
      config.setEncodingType("application/geo+json");
      config.setDefinition("http://example.org/temp");
      config.setObservationType(
          "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement");
      config.setMetadata("sensor-doc-url");
      config.setProperties(Map.of("serial", "TMP-001"));
      config.setLocation(Map.of("type", "Point", "coordinates", new double[] {8.4, 49.0}));

      Map<String, Object> map = config.toApiMap();

      assertEquals("Temperature Sensor", map.get("name"));
      assertEquals("A sensor", map.get("description"));
      assertEquals("application/geo+json", map.get("encodingType"));
      assertEquals("http://example.org/temp", map.get("definition"));
      assertEquals("sensor-doc-url", map.get("metadata"));
      assertEquals(Map.of("serial", "TMP-001"), map.get("properties"));
      assertTrue(map.containsKey("location"));
      assertTrue(map.containsKey("observationType"));
    }

    @Test
    @DisplayName("omits null fields")
    void shouldOmitNullFields() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Minimal");

      Map<String, Object> map = config.toApiMap();

      assertEquals(1, map.size());
      assertEquals("Minimal", map.get("name"));
    }

    @Test
    @DisplayName("returns empty map when all fields are null")
    void shouldReturnEmptyMapForDefault() {
      FrostConfigValue config = new FrostConfigValue();

      Map<String, Object> map = config.toApiMap();

      assertTrue(map.isEmpty());
    }

    @Test
    @DisplayName("includes unitOfMeasurement as nested map")
    void shouldIncludeUnitOfMeasurement() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Datastream");
      config.setUnitOfMeasurement(new UnitOfMeasurement("Degree Celsius", "°C", "http://ucum.org"));

      Map<String, Object> map = config.toApiMap();

      @SuppressWarnings("unchecked")
      Map<String, Object> uom = (Map<String, Object>) map.get("unitOfMeasurement");
      assertEquals("Degree Celsius", uom.get("name"));
      assertEquals("°C", uom.get("symbol"));
      assertEquals("http://ucum.org", uom.get("definition"));
    }

    @Test
    @DisplayName("omits unitOfMeasurement when all its fields are null")
    void shouldOmitEmptyUnitOfMeasurement() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Datastream");
      config.setUnitOfMeasurement(new UnitOfMeasurement(null, null, null));

      Map<String, Object> map = config.toApiMap();

      assertFalse(map.containsKey("unitOfMeasurement"));
    }

    @Test
    @DisplayName("merges additional properties with putIfAbsent semantics")
    void shouldMergeAdditionalPropertiesWithoutOverriding() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Typed Name");
      // Simulate an unknown JSON property that collides with a typed field
      config.handleUnknownProperty("name", "Additional Name");
      config.handleUnknownProperty("customField", "customValue");

      Map<String, Object> map = config.toApiMap();

      // Typed field wins over additional property
      assertEquals("Typed Name", map.get("name"));
      // Additional property is included
      assertEquals("customValue", map.get("customField"));
    }

    @Test
    @DisplayName("returned map is unmodifiable")
    void shouldReturnUnmodifiableMap() {
      FrostConfigValue config = new FrostConfigValue();
      config.setName("Test");

      Map<String, Object> map = config.toApiMap();

      org.junit.jupiter.api.Assertions.assertThrows(
          UnsupportedOperationException.class, () -> map.put("new", "value"));
    }
  }

  @Nested
  @DisplayName("equals and hashCode")
  class EqualsHashCode {

    @Test
    @DisplayName("equal instances have same hashCode")
    void shouldHaveSameHashCodeForEqualInstances() {
      FrostConfigValue a = new FrostConfigValue();
      a.setName("Sensor");
      a.setDescription("Desc");

      FrostConfigValue b = new FrostConfigValue();
      b.setName("Sensor");
      b.setDescription("Desc");

      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("different values are not equal")
    void shouldNotBeEqualForDifferentValues() {
      FrostConfigValue a = new FrostConfigValue();
      a.setName("Sensor A");

      FrostConfigValue b = new FrostConfigValue();
      b.setName("Sensor B");

      assertNotEquals(a, b);
    }

    @Test
    @DisplayName("includes additional properties in equality")
    void shouldConsiderAdditionalPropertiesInEquals() {
      FrostConfigValue a = new FrostConfigValue();
      a.setName("Sensor");
      a.handleUnknownProperty("custom", "val1");

      FrostConfigValue b = new FrostConfigValue();
      b.setName("Sensor");
      b.handleUnknownProperty("custom", "val2");

      assertNotEquals(a, b);
    }
  }
}

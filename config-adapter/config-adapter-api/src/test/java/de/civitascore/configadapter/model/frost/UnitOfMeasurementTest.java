/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.frost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UnitOfMeasurement}. */
class UnitOfMeasurementTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_allFields_shouldStoreValues() {
    UnitOfMeasurement uom =
        new UnitOfMeasurement(
            "Degree Celsius", "\u00b0C", "http://unitsofmeasure.org/ucum.html#para-30");
    assertEquals("Degree Celsius", uom.name());
    assertEquals("\u00b0C", uom.symbol());
    assertEquals("http://unitsofmeasure.org/ucum.html#para-30", uom.definition());
  }

  @Test
  void constructor_nullFields_shouldAllowNull() {
    UnitOfMeasurement uom = new UnitOfMeasurement(null, null, null);
    assertNull(uom.name());
    assertNull(uom.symbol());
    assertNull(uom.definition());
  }

  @Test
  void jsonSerialization_shouldProduceCorrectJson() throws Exception {
    UnitOfMeasurement uom =
        new UnitOfMeasurement(
            "Degree Celsius", "\u00b0C", "http://unitsofmeasure.org/ucum.html#para-30");

    String json = objectMapper.writeValueAsString(uom);
    assertNotNull(json);

    UnitOfMeasurement deserialized = objectMapper.readValue(json, UnitOfMeasurement.class);
    assertEquals(uom, deserialized);
  }

  @Test
  void jsonDeserialization_fromOgcExample_shouldCreateRecord() throws Exception {
    String json =
        """
                {
                  "name": "Degree Celsius",
                  "symbol": "\u00b0C",
                  "definition": "http://unitsofmeasure.org/ucum.html#para-30"
                }
                """;

    UnitOfMeasurement uom = objectMapper.readValue(json, UnitOfMeasurement.class);
    assertEquals("Degree Celsius", uom.name());
    assertEquals("\u00b0C", uom.symbol());
    assertEquals("http://unitsofmeasure.org/ucum.html#para-30", uom.definition());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    UnitOfMeasurement original =
        new UnitOfMeasurement("Lux", "lx", "http://unitsofmeasure.org/ucum.html#para-30");

    String json = objectMapper.writeValueAsString(original);
    UnitOfMeasurement deserialized = objectMapper.readValue(json, UnitOfMeasurement.class);
    assertEquals(original, deserialized);
  }

  @Test
  void equals_sameValues_shouldBeEqual() {
    UnitOfMeasurement uom1 = new UnitOfMeasurement("Celsius", "\u00b0C", "http://example.com");
    UnitOfMeasurement uom2 = new UnitOfMeasurement("Celsius", "\u00b0C", "http://example.com");
    assertEquals(uom1, uom2);
    assertEquals(uom1.hashCode(), uom2.hashCode());
  }

  @Test
  void equals_differentValues_shouldNotBeEqual() {
    UnitOfMeasurement uom1 = new UnitOfMeasurement("Celsius", "\u00b0C", "http://example.com");
    UnitOfMeasurement uom2 = new UnitOfMeasurement("Fahrenheit", "\u00b0F", "http://example.com");
    assertNotEquals(uom1, uom2);
  }

  @Test
  void toApiMap_allFields_shouldContainAllEntries() {
    UnitOfMeasurement uom =
        new UnitOfMeasurement(
            "Degree Celsius", "\u00b0C", "http://unitsofmeasure.org/ucum.html#para-30");

    Map<String, Object> map = uom.toApiMap();
    assertNotNull(map);
    assertEquals(3, map.size());
    assertEquals("Degree Celsius", map.get("name"));
    assertEquals("\u00b0C", map.get("symbol"));
    assertEquals("http://unitsofmeasure.org/ucum.html#para-30", map.get("definition"));
  }

  @Test
  void toApiMap_partialFields_shouldOmitNullEntries() {
    UnitOfMeasurement uom = new UnitOfMeasurement("Lux", null, null);

    Map<String, Object> map = uom.toApiMap();
    assertNotNull(map);
    assertEquals(1, map.size());
    assertEquals("Lux", map.get("name"));
    assertNull(map.get("symbol"));
    assertNull(map.get("definition"));
  }

  @Test
  void toApiMap_allNull_shouldReturnEmptyMap() {
    UnitOfMeasurement uom = new UnitOfMeasurement(null, null, null);
    Map<String, Object> map = uom.toApiMap();
    assertNotNull(map);
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldReturnUnmodifiableMap() {
    UnitOfMeasurement uom =
        new UnitOfMeasurement("Degree Celsius", "\u00b0C", "http://example.com");

    Map<String, Object> map = uom.toApiMap();
    assertThrows(UnsupportedOperationException.class, () -> map.put("new-key", "value"));
  }
}

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

/** Unit tests for {@link ProcessorStep}. */
class ProcessorStepTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    ProcessorStep step = new ProcessorStep();
    assertNull(step.getMapping());
    assertNotNull(step.getAdditionalProperties());
    assertTrue(step.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root.temperature = this.temp_c * 9/5 + 32");

    assertEquals("root.temperature = this.temp_c * 9/5 + 32", step.getMapping());
  }

  @Test
  void toApiMap_whenMappingSet_shouldContainMapping() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");

    Map<String, Object> map = step.toApiMap();
    assertEquals("root = this", map.get("mapping"));
    assertEquals(1, map.size());
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    ProcessorStep step = new ProcessorStep();
    Map<String, Object> map = step.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    step.handleUnknownProperty("log", "debug");

    Map<String, Object> map = step.toApiMap();
    assertEquals("root = this", map.get("mapping"));
    assertEquals("debug", map.get("log"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ProcessorStep step1 = new ProcessorStep();
    step1.setMapping("root = this");
    ProcessorStep step2 = new ProcessorStep();
    step2.setMapping("root = this");

    assertEquals(step1, step2);
    assertEquals(step1.hashCode(), step2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    ProcessorStep step = new ProcessorStep();
    assertEquals(step, step);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    ProcessorStep step = new ProcessorStep();
    assertNotEquals(null, step);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    ProcessorStep step = new ProcessorStep();
    assertNotEquals("string", step);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ProcessorStep step1 = new ProcessorStep();
    step1.setMapping("root = this");
    ProcessorStep step2 = new ProcessorStep();
    step2.setMapping("root = deleted()");

    assertNotEquals(step1, step2);
  }

  @Test
  void toString_whenShortMapping_shouldTruncate() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");

    String str = step.toString();
    assertTrue(str.contains("ProcessorStep"));
    assertTrue(str.contains("root = this"));
  }

  @Test
  void toString_whenLongMapping_shouldTruncateAt50Chars() {
    ProcessorStep step = new ProcessorStep();
    String longMapping =
        "root.field = this.very_long_field_name_that_exceeds_fifty_characters_easily";
    step.setMapping(longMapping);

    String str = step.toString();
    assertTrue(str.contains("ProcessorStep"));
    assertTrue(str.contains("..."));
  }

  @Test
  void toString_whenNullMapping_shouldShowNull() {
    ProcessorStep step = new ProcessorStep();
    String str = step.toString();
    assertTrue(str.contains("null"));
  }

  @Test
  void jsonSerialization_shouldProduceValidJson() throws Exception {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");

    String json = objectMapper.writeValueAsString(step);
    assertNotNull(json);
    assertTrue(json.contains("\"mapping\""));
    assertTrue(json.contains("root = this"));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "mapping": "root.output = this.input"
                }
                """;

    ProcessorStep step = objectMapper.readValue(json, ProcessorStep.class);
    assertEquals("root.output = this.input", step.getMapping());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    ProcessorStep original = new ProcessorStep();
    original.setMapping("root.temperature = this.temp_c * 9/5 + 32");

    String json = objectMapper.writeValueAsString(original);
    ProcessorStep deserialized = objectMapper.readValue(json, ProcessorStep.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "mapping": "root = this",
                  "label": "transform-step"
                }
                """;

    ProcessorStep step = objectMapper.readValue(json, ProcessorStep.class);
    assertEquals("root = this", step.getMapping());
    assertEquals("transform-step", step.getAdditionalProperties().get("label"));
  }
}

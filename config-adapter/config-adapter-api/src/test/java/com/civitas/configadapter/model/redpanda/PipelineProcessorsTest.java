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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PipelineProcessors}. */
class PipelineProcessorsTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    this.objectMapper = new ObjectMapper();
  }

  @Test
  void constructor_whenNoArgs_shouldHaveNullFields() {
    PipelineProcessors processors = new PipelineProcessors();
    assertNull(processors.getProcessors());
    assertNotNull(processors.getAdditionalProperties());
    assertTrue(processors.getAdditionalProperties().isEmpty());
  }

  @Test
  void setters_whenCalled_shouldStoreValues() {
    PipelineProcessors processors = new PipelineProcessors();
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    processors.setProcessors(List.of(step));

    assertEquals(1, processors.getProcessors().size());
    assertEquals("root = this", processors.getProcessors().get(0).getMapping());
  }

  @Test
  void toApiMap_whenProcessorsSet_shouldContainNestedMaps() {
    ProcessorStep step1 = new ProcessorStep();
    step1.setMapping("root = this");
    ProcessorStep step2 = new ProcessorStep();
    step2.setMapping("root.count = this.count + 1");
    PipelineProcessors processors = new PipelineProcessors();
    processors.setProcessors(List.of(step1, step2));

    Map<String, Object> map = processors.toApiMap();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> processorList = (List<Map<String, Object>>) map.get("processors");
    assertNotNull(processorList);
    assertEquals(2, processorList.size());
    assertEquals("root = this", processorList.get(0).get("mapping"));
    assertEquals("root.count = this.count + 1", processorList.get(1).get("mapping"));
  }

  @Test
  void toApiMap_whenEmpty_shouldReturnEmptyMap() {
    PipelineProcessors processors = new PipelineProcessors();
    Map<String, Object> map = processors.toApiMap();
    assertTrue(map.isEmpty());
  }

  @Test
  void toApiMap_shouldIncludeAdditionalProperties() {
    PipelineProcessors processors = new PipelineProcessors();
    processors.setProcessors(List.of());
    processors.handleUnknownProperty("threads", 4);

    Map<String, Object> map = processors.toApiMap();
    assertNotNull(map.get("processors"));
    assertEquals(4, map.get("threads"));
  }

  @Test
  void equals_whenSameData_shouldReturnTrue() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    PipelineProcessors proc1 = new PipelineProcessors();
    proc1.setProcessors(List.of(step));
    PipelineProcessors proc2 = new PipelineProcessors();
    ProcessorStep step2 = new ProcessorStep();
    step2.setMapping("root = this");
    proc2.setProcessors(List.of(step2));

    assertEquals(proc1, proc2);
    assertEquals(proc1.hashCode(), proc2.hashCode());
  }

  @Test
  void equals_whenSameObject_shouldReturnTrue() {
    PipelineProcessors processors = new PipelineProcessors();
    assertEquals(processors, processors);
  }

  @Test
  void equals_whenNull_shouldReturnFalse() {
    PipelineProcessors processors = new PipelineProcessors();
    assertNotEquals(null, processors);
  }

  @Test
  void equals_whenDifferentClass_shouldReturnFalse() {
    PipelineProcessors processors = new PipelineProcessors();
    assertNotEquals("string", processors);
  }

  @Test
  void equals_whenDifferentData_shouldReturnFalse() {
    ProcessorStep step1 = new ProcessorStep();
    step1.setMapping("root = this");
    PipelineProcessors proc1 = new PipelineProcessors();
    proc1.setProcessors(List.of(step1));

    ProcessorStep step2 = new ProcessorStep();
    step2.setMapping("root = deleted()");
    PipelineProcessors proc2 = new PipelineProcessors();
    proc2.setProcessors(List.of(step2));

    assertNotEquals(proc1, proc2);
  }

  @Test
  void toString_whenCalled_shouldContainProcessorCount() {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    PipelineProcessors processors = new PipelineProcessors();
    processors.setProcessors(List.of(step));

    String str = processors.toString();
    assertTrue(str.contains("PipelineProcessors"));
    assertTrue(str.contains("1"));
  }

  @Test
  void toString_whenNullProcessors_shouldShowZero() {
    PipelineProcessors processors = new PipelineProcessors();
    String str = processors.toString();
    assertTrue(str.contains("0"));
  }

  @Test
  void jsonSerialization_shouldProduceValidJson() throws Exception {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root = this");
    PipelineProcessors processors = new PipelineProcessors();
    processors.setProcessors(List.of(step));

    String json = objectMapper.writeValueAsString(processors);
    assertNotNull(json);
    assertTrue(json.contains("\"processors\""));
    assertTrue(json.contains("\"mapping\""));
  }

  @Test
  void jsonDeserialization_fromValidJson_shouldCreateObject() throws Exception {
    String json =
        """
                {
                  "processors": [
                    {"mapping": "root = this"},
                    {"mapping": "root.count = this.count + 1"}
                  ]
                }
                """;

    PipelineProcessors processors = objectMapper.readValue(json, PipelineProcessors.class);
    assertEquals(2, processors.getProcessors().size());
    assertEquals("root = this", processors.getProcessors().get(0).getMapping());
    assertEquals("root.count = this.count + 1", processors.getProcessors().get(1).getMapping());
  }

  @Test
  void jsonRoundTrip_shouldPreserveValues() throws Exception {
    ProcessorStep step = new ProcessorStep();
    step.setMapping("root.temperature = this.temp_c * 9/5 + 32");
    PipelineProcessors original = new PipelineProcessors();
    original.setProcessors(List.of(step));

    String json = objectMapper.writeValueAsString(original);
    PipelineProcessors deserialized = objectMapper.readValue(json, PipelineProcessors.class);

    assertEquals(original, deserialized);
  }

  @Test
  void jsonDeserialization_withUnknownProperties_shouldCaptureAdditionalProperties()
      throws Exception {
    String json =
        """
                {
                  "processors": [],
                  "batch_policy": {"count": 10}
                }
                """;

    PipelineProcessors processors = objectMapper.readValue(json, PipelineProcessors.class);
    assertNotNull(processors.getProcessors());
    assertTrue(processors.getProcessors().isEmpty());
    assertNotNull(processors.getAdditionalProperties().get("batch_policy"));
  }
}

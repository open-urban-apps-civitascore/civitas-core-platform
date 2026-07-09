/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable.common.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowableTriggerConsumerHasPipelinesTest {

  @Mock private RuntimeService runtimeService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private FlowableTriggerConsumer consumer;

  @BeforeEach
  void setUp() {
    var historyService = mock(HistoryService.class);
    consumer = new FlowableTriggerConsumer(runtimeService, historyService);

    var query = mock(ProcessInstanceQuery.class);
    lenient().when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    lenient().when(query.processInstanceBusinessKey(anyString())).thenReturn(query);
    lenient().when(query.count()).thenReturn(0L);

    var histQuery = mock(HistoricProcessInstanceQuery.class);
    lenient().when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
    lenient().when(histQuery.processInstanceBusinessKey(anyString())).thenReturn(histQuery);
    lenient().when(histQuery.finished()).thenReturn(histQuery);
    lenient().when(histQuery.count()).thenReturn(0L);

    ProcessInstance mockInstance = mock(ProcessInstance.class);
    lenient().when(mockInstance.getId()).thenReturn("pi-1");
    lenient()
        .when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
        .thenReturn(mockInstance);
  }

  @Test
  void deriveHasPipelines_whenDataPipelinesPresent_setsTrue() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1",
                "dataPipelines", List.of(Map.of("id", "p-1"))));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(Boolean.TRUE, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenDataPipelinesEmpty_setsFalse() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1",
                "dataPipelines", List.of()));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(Boolean.FALSE, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenPipelineIdsPresent_setsTrue() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_DELETE",
                "datasetId", "ds-1",
                "pipelineIds", List.of("p-1")));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(Boolean.TRUE, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenNoPipelinesAtAll_setsFalse() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1"));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(Boolean.FALSE, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_explicitValueInPayload_isOverriddenByDerivation() throws Exception {
    // Payload says hasPipelines=true but no dataPipelines → derived value is false
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1",
                "hasPipelines", true));

    TriggerTestSupport.processTrigger(consumer, trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(Boolean.FALSE, captor.getValue().get("hasPipelines"));
  }

  @ParameterizedTest(name = "hasGeoSink={1} when datasinks={0}")
  @MethodSource("geoSinkCases")
  void deriveHasGeoSink(Object datasinks, boolean expected) throws Exception {
    TriggerTestSupport.processTrigger(consumer, triggerWith("datasinks", datasinks));

    assertEquals(expected, capturedVariables().get("hasGeoSink"));
  }

  static Stream<Arguments> geoSinkCases() {
    return Stream.of(
        Arguments.of(List.of(Map.of("type", "FROST"), Map.of("type", "POSTGIS")), true),
        Arguments.of(List.of(Map.of("type", "FROST")), false),
        Arguments.of(List.of(), false),
        Arguments.of(null, false));
  }

  @ParameterizedTest(name = "hasLayers={1} when layers={0}")
  @MethodSource("layersCases")
  void deriveHasLayers(Object layers, boolean expected) throws Exception {
    TriggerTestSupport.processTrigger(consumer, triggerWith("layers", layers));

    assertEquals(expected, capturedVariables().get("hasLayers"));
  }

  static Stream<Arguments> layersCases() {
    return Stream.of(
        Arguments.of(List.of(Map.of("layerName", "l-1")), true),
        Arguments.of(List.of(), false),
        Arguments.of(null, false));
  }

  /**
   * Builds a minimal DATASET_CREATE trigger, including {@code field} only when {@code value} is
   * set.
   */
  private byte[] triggerWith(String field, Object value) throws Exception {
    Map<String, Object> payload = new HashMap<>();
    payload.put("sagaType", "DATASET_CREATE");
    payload.put("datasetId", "ds-1");
    if (value != null) {
      payload.put(field, value);
    }
    return objectMapper.writeValueAsBytes(payload);
  }

  private Map<String, Object> capturedVariables() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    return captor.getValue();
  }
}

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
import java.util.List;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    var historyService = mock(org.flowable.engine.HistoryService.class);
    consumer = new FlowableTriggerConsumer(runtimeService, historyService);

    var query = mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
    lenient().when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    lenient().when(query.processInstanceBusinessKey(anyString())).thenReturn(query);
    lenient().when(query.count()).thenReturn(0L);

    var histQuery = mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
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

    consumer.processTrigger(trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(true, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenDataPipelinesEmpty_setsFalse() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1",
                "dataPipelines", List.of()));

    consumer.processTrigger(trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(false, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenPipelineIdsPresent_setsTrue() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_DELETE",
                "datasetId", "ds-1",
                "pipelineIds", List.of("p-1")));

    consumer.processTrigger(trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(true, captor.getValue().get("hasPipelines"));
  }

  @Test
  void deriveHasPipelines_whenNoPipelinesAtAll_setsFalse() throws Exception {
    byte[] trigger =
        objectMapper.writeValueAsBytes(
            Map.of(
                "sagaType", "DATASET_CREATE",
                "datasetId", "ds-1"));

    consumer.processTrigger(trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(false, captor.getValue().get("hasPipelines"));
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

    consumer.processTrigger(trigger);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), captor.capture());
    assertEquals(false, captor.getValue().get("hasPipelines"));
  }
}

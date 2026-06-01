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

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowableTriggerIdempotencyTest {

  @Mock private RuntimeService runtimeService;
  @Mock private HistoryService historyService;

  private FlowableTriggerConsumer consumer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    consumer = new FlowableTriggerConsumer(runtimeService, historyService);

    ProcessInstance mockInstance = mock(ProcessInstance.class);
    lenient().when(mockInstance.getId()).thenReturn("pi-1");
    lenient()
        .when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), anyMap()))
        .thenReturn(mockInstance);
  }

  @Test
  void replayedRecord_withActiveInstance_isRejected() throws Exception {
    String expectedBusinessKey = "de.civitascore.dataset.saga.trigger:0:42";

    ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
    when(query.processInstanceBusinessKey(expectedBusinessKey)).thenReturn(query);
    when(query.count()).thenReturn(1L);

    byte[] trigger =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1"));

    consumer.processTriggerRecord(
        new ConsumerRecord<>("de.civitascore.dataset.saga.trigger", 0, 42L, "ds-1", trigger));

    verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  void replayedRecord_withCompletedInstance_isRejected() throws Exception {
    String expectedBusinessKey = "de.civitascore.dataset.saga.trigger:0:42";

    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceBusinessKey(expectedBusinessKey)).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
    when(histQuery.processInstanceBusinessKey(expectedBusinessKey)).thenReturn(histQuery);
    when(histQuery.finished()).thenReturn(histQuery);
    when(histQuery.count()).thenReturn(1L);

    byte[] trigger =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1"));

    consumer.processTriggerRecord(
        new ConsumerRecord<>("de.civitascore.dataset.saga.trigger", 0, 42L, "ds-1", trigger));

    verify(runtimeService, never()).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  void newRecord_withNoExistingInstance_startsProcess() throws Exception {
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceBusinessKey(anyString())).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
    when(histQuery.processInstanceBusinessKey(anyString())).thenReturn(histQuery);
    when(histQuery.finished()).thenReturn(histQuery);
    when(histQuery.count()).thenReturn(0L);

    byte[] trigger =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1"));

    consumer.processTriggerRecord(
        new ConsumerRecord<>("de.civitascore.dataset.saga.trigger", 0, 42L, "ds-1", trigger));

    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }

  @Test
  void sameDatasetDifferentOffset_startsNewProcess() throws Exception {
    ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
    when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
    when(runtimeQuery.processInstanceBusinessKey(anyString())).thenReturn(runtimeQuery);
    when(runtimeQuery.count()).thenReturn(0L);

    HistoricProcessInstanceQuery histQuery = mock(HistoricProcessInstanceQuery.class);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(histQuery);
    when(histQuery.processInstanceBusinessKey(anyString())).thenReturn(histQuery);
    when(histQuery.finished()).thenReturn(histQuery);
    when(histQuery.count()).thenReturn(0L);

    byte[] trigger =
        objectMapper.writeValueAsBytes(Map.of("sagaType", "DATASET_UPDATE", "datasetId", "ds-1"));

    consumer.processTriggerRecord(
        new ConsumerRecord<>("de.civitascore.dataset.saga.trigger", 0, 99L, "ds-1", trigger));

    verify(runtimeService).startProcessInstanceByKey(anyString(), anyString(), anyMap());
  }
}

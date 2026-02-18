/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaType;
import com.civitas.configadapter.orchestrator.DatasetSagaOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaTriggerConsumerTest {

  private DatasetSagaOrchestrator orchestrator;
  private SagaTriggerConsumer consumer;
  private ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    var kafkaConsumer = mock(org.apache.kafka.clients.consumer.KafkaConsumer.class);
    orchestrator = mock(DatasetSagaOrchestrator.class);
    consumer = new SagaTriggerConsumer(kafkaConsumer, orchestrator);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private void invokeTrigger(Map<String, Object> trigger) throws Exception {
    byte[] json = objectMapper.writeValueAsBytes(trigger);
    Method method = SagaTriggerConsumer.class.getDeclaredMethod("processTrigger", byte[].class);
    method.setAccessible(true);
    method.invoke(consumer, json);
  }

  @Nested
  @DisplayName("processTrigger")
  class ProcessTrigger {

    @Test
    @DisplayName("starts saga for valid DATASET_CREATE trigger")
    void shouldStartSagaForValidTrigger() throws Exception {
      when(orchestrator.startSaga(any(), any(), any()))
          .thenReturn(Optional.of(mock(SagaContext.class)));

      Map<String, Object> trigger =
          Map.of(
              "sagaType", "DATASET_CREATE",
              "datasetId", "ds-1",
              "name", "Test Dataset");

      invokeTrigger(trigger);

      verify(orchestrator).startSaga(eq(SagaType.DATASET_CREATE), eq("ds-1"), eq(trigger));
    }

    @Test
    @DisplayName("ignores trigger with missing sagaType")
    void shouldIgnoreMissingSagaType() throws Exception {
      invokeTrigger(Map.of("datasetId", "ds-1"));

      verify(orchestrator, never()).startSaga(any(), any(), any());
    }

    @Test
    @DisplayName("ignores trigger with missing datasetId")
    void shouldIgnoreMissingDatasetId() throws Exception {
      invokeTrigger(Map.of("sagaType", "DATASET_CREATE"));

      verify(orchestrator, never()).startSaga(any(), any(), any());
    }

    @Test
    @DisplayName("ignores trigger with unknown sagaType")
    void shouldIgnoreUnknownSagaType() throws Exception {
      invokeTrigger(Map.of("sagaType", "UNKNOWN_TYPE", "datasetId", "ds-1"));

      verify(orchestrator, never()).startSaga(any(), any(), any());
    }

    @Test
    @DisplayName("handles DATASET_UPDATE trigger")
    void shouldHandleUpdateTrigger() throws Exception {
      when(orchestrator.startSaga(any(), any(), any())).thenReturn(Optional.empty());

      invokeTrigger(Map.of("sagaType", "DATASET_UPDATE", "datasetId", "ds-1"));

      verify(orchestrator).startSaga(eq(SagaType.DATASET_UPDATE), eq("ds-1"), any());
    }

    @Test
    @DisplayName("handles DATASET_DELETE trigger")
    void shouldHandleDeleteTrigger() throws Exception {
      when(orchestrator.startSaga(any(), any(), any())).thenReturn(Optional.empty());

      invokeTrigger(Map.of("sagaType", "DATASET_DELETE", "datasetId", "ds-1"));

      verify(orchestrator).startSaga(eq(SagaType.DATASET_DELETE), eq("ds-1"), any());
    }
  }
}

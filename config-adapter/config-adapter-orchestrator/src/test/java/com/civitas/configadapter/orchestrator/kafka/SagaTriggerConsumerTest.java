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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaType;
import com.civitas.configadapter.orchestrator.DatasetSagaOrchestrator;
import com.civitas.configadapter.util.BackoffCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaTriggerConsumerTest {

  private DatasetSagaOrchestrator orchestrator;
  private SagaTriggerConsumer consumer;
  private ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  private final KafkaConsumer<String, byte[]> kafkaConsumer = mock(KafkaConsumer.class);

  @BeforeEach
  void setUp() {
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

  @Nested
  @DisplayName("consumeLoop error handling")
  class ConsumeLoopErrorHandling {

    private static final BackoffCalculator FAST_BACKOFF = new BackoffCalculator(1L, 10L);

    @SuppressWarnings("unchecked")
    private final KafkaConsumer<String, byte[]> loopConsumer = mock(KafkaConsumer.class);

    private DatasetSagaOrchestrator loopOrchestrator;
    private SagaTriggerConsumer loopSut;

    @BeforeEach
    void setUpLoop() {
      loopOrchestrator = mock(DatasetSagaOrchestrator.class);
      TopicPartition tp = new TopicPartition(SagaTriggerConsumer.TRIGGER_TOPIC, 0);
      when(loopConsumer.assignment()).thenReturn(Set.of(tp));
      loopSut = new SagaTriggerConsumer(loopConsumer, loopOrchestrator, FAST_BACKOFF);
    }

    @Test
    @DisplayName("consumeLoop_malformedJson_commitsOffsetAndContinues")
    void consumeLoop_malformedJson_commitsOffsetAndContinues() {
      byte[] invalidJson = "not-valid-json".getBytes();
      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>(SagaTriggerConsumer.TRIGGER_TOPIC, 0, 0L, "key", invalidJson);
      TopicPartition tp = new TopicPartition(SagaTriggerConsumer.TRIGGER_TOPIC, 0);
      ConsumerRecords<String, byte[]> records =
          new ConsumerRecords<>(Map.of(tp, List.of(badRecord)));

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer).commitSync());
      loopSut.stop();

      verify(loopOrchestrator, never()).startSaga(any(), any(), any());
    }

    @Test
    @DisplayName("consumeLoop_orchestratorThrowsRuntimeException_retriesThenCommits")
    void consumeLoop_orchestratorThrowsRuntimeException_retriesThenCommits() throws Exception {
      Map<String, Object> trigger =
          Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1", "name", "Test");
      byte[] validJson = objectMapper.writeValueAsBytes(trigger);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(SagaTriggerConsumer.TRIGGER_TOPIC, 0, 0L, "key", validJson);
      TopicPartition tp = new TopicPartition(SagaTriggerConsumer.TRIGGER_TOPIC, 0);
      ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(Map.of(tp, List.of(record)));

      when(loopOrchestrator.startSaga(any(), any(), any()))
          .thenThrow(new RuntimeException("orchestrator error"));

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer).commitSync());
      loopSut.stop();

      // Orchestrator called multiple times due to retries
      verify(loopOrchestrator, atLeast(2)).startSaga(any(), any(), any());
    }

    @Test
    @DisplayName("consumeLoop_poisonPillThenValid_processesSecondRecordAndCommitsTwice")
    void consumeLoop_poisonPillThenValid_processesSecondRecordAndCommitsTwice() throws Exception {
      byte[] invalidJson = "not-valid-json".getBytes();
      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>(SagaTriggerConsumer.TRIGGER_TOPIC, 0, 0L, "key1", invalidJson);

      Map<String, Object> trigger =
          Map.of("sagaType", "DATASET_CREATE", "datasetId", "ds-1", "name", "Test");
      byte[] validJson = objectMapper.writeValueAsBytes(trigger);
      ConsumerRecord<String, byte[]> goodRecord =
          new ConsumerRecord<>(SagaTriggerConsumer.TRIGGER_TOPIC, 0, 1L, "key2", validJson);

      TopicPartition tp = new TopicPartition(SagaTriggerConsumer.TRIGGER_TOPIC, 0);
      ConsumerRecords<String, byte[]> records =
          new ConsumerRecords<>(Map.of(tp, List.of(badRecord, goodRecord)));

      when(loopOrchestrator.startSaga(any(), any(), any()))
          .thenReturn(Optional.of(mock(SagaContext.class)));

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer, times(2)).commitSync());
      loopSut.stop();

      verify(loopOrchestrator).startSaga(eq(SagaType.DATASET_CREATE), eq("ds-1"), any());
    }
  }
}

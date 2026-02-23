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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.civitas.configadapter.orchestrator.engine.SagaEngine;
import com.civitas.configadapter.util.BackoffCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SagaResultConsumerTest {

  private SagaEngine engine;
  private SagaResultConsumer consumer;
  private ObjectMapper objectMapper;

  @SuppressWarnings("unchecked")
  private final KafkaConsumer<String, byte[]> kafkaConsumer = mock(KafkaConsumer.class);

  @BeforeEach
  void setUp() {
    engine = mock(SagaEngine.class);
    consumer = new SagaResultConsumer(kafkaConsumer, engine);
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  private void invokeProcessRecord(Map<String, Object> message) throws Exception {
    byte[] json = objectMapper.writeValueAsBytes(message);
    Method method = SagaResultConsumer.class.getDeclaredMethod("processRecord", byte[].class);
    method.setAccessible(true);
    method.invoke(consumer, json);
  }

  @Nested
  @DisplayName("STEP_COMPLETED")
  class StepCompleted {

    @Test
    @DisplayName("routes to engine with resultData and compensationData")
    void shouldRouteWithData() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      msg.put("resultData", Map.of("projectId", "proj-123"));
      msg.put("compensationData", Map.of("projectId", "proj-123"));

      invokeProcessRecord(msg);

      verify(engine)
          .handleStepCompleted(
              eq("saga-1"),
              eq("create-project"),
              eq(Map.of("projectId", "proj-123")),
              eq(Map.of("projectId", "proj-123")));
    }

    @Test
    @DisplayName("uses empty maps when resultData and compensationData are absent")
    void shouldUseEmptyMapsForMissingData() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine)
          .handleStepCompleted(eq("saga-1"), eq("create-project"), eq(Map.of()), eq(Map.of()));
    }
  }

  @Nested
  @DisplayName("STEP_FAILED")
  class StepFailed {

    @Test
    @DisplayName("routes to engine with error message")
    void shouldRouteWithError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-route");
      msg.put("error", "Connection refused");

      invokeProcessRecord(msg);

      verify(engine).handleStepFailed("saga-1", "create-route", "Connection refused");
    }

    @Test
    @DisplayName("uses 'Unknown error' when error field is absent")
    void shouldUseDefaultError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-route");

      invokeProcessRecord(msg);

      verify(engine).handleStepFailed("saga-1", "create-route", "Unknown error");
    }
  }

  @Nested
  @DisplayName("COMPENSATION_COMPLETED")
  class CompensationCompleted {

    @Test
    @DisplayName("routes to engine")
    void shouldRouteToEngine() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "COMPENSATION_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine).handleCompensationCompleted("saga-1", "create-project");
    }
  }

  @Nested
  @DisplayName("COMPENSATION_FAILED")
  class CompensationFailed {

    @Test
    @DisplayName("routes to engine with error message")
    void shouldRouteWithError() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "COMPENSATION_FAILED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      msg.put("error", "FROST unreachable");

      invokeProcessRecord(msg);

      verify(engine).handleCompensationFailed("saga-1", "create-project", "FROST unreachable");
    }
  }

  @Nested
  @DisplayName("malformed messages")
  class Malformed {

    @Test
    @DisplayName("ignores message with missing type")
    void shouldIgnoreMissingType() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
      verify(engine, never()).handleStepFailed(any(), any(), any());
    }

    @Test
    @DisplayName("ignores message with missing sagaId")
    void shouldIgnoreMissingSagaId() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ignores message with missing stepId")
    void shouldIgnoreMissingStepId() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("ignores unknown message type")
    void shouldIgnoreUnknownType() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "UNKNOWN_TYPE");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");

      invokeProcessRecord(msg);

      verify(engine, never()).handleStepCompleted(any(), any(), any(), any());
      verify(engine, never()).handleStepFailed(any(), any(), any());
      verify(engine, never()).handleCompensationCompleted(any(), any());
      verify(engine, never()).handleCompensationFailed(any(), any(), any());
    }
  }

  @Nested
  @DisplayName("consumeLoop error handling")
  class ConsumeLoopErrorHandling {

    private static final BackoffCalculator FAST_BACKOFF = new BackoffCalculator(1L, 10L);

    @SuppressWarnings("unchecked")
    private final KafkaConsumer<String, byte[]> loopConsumer = mock(KafkaConsumer.class);

    private SagaEngine loopEngine;
    private SagaResultConsumer loopSut;

    @BeforeEach
    void setUpLoop() {
      loopEngine = mock(SagaEngine.class);
      TopicPartition tp = new TopicPartition("core.civitas.dataset.frost.result", 0);
      when(loopConsumer.assignment()).thenReturn(Set.of(tp));
      loopSut = new SagaResultConsumer(loopConsumer, loopEngine, FAST_BACKOFF);
    }

    @Test
    @DisplayName("consumeLoop_malformedJson_commitsOffsetAndContinues")
    void consumeLoop_malformedJson_commitsOffsetAndContinues() {
      byte[] invalidJson = "not-valid-json".getBytes();
      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>("core.civitas.dataset.frost.result", 0, 0L, "key", invalidJson);
      TopicPartition tp = new TopicPartition("core.civitas.dataset.frost.result", 0);
      ConsumerRecords<String, byte[]> records =
          new ConsumerRecords<>(Map.of(tp, List.of(badRecord)));

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer).commitSync());
      loopSut.stop();

      verify(loopEngine, never()).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("consumeLoop_engineThrowsRuntimeException_retriesThenCommits")
    void consumeLoop_engineThrowsRuntimeException_retriesThenCommits() throws Exception {
      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      byte[] validJson = objectMapper.writeValueAsBytes(msg);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>("core.civitas.dataset.frost.result", 0, 0L, "key", validJson);
      TopicPartition tp = new TopicPartition("core.civitas.dataset.frost.result", 0);
      ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(Map.of(tp, List.of(record)));

      doThrow(new RuntimeException("engine error"))
          .when(loopEngine)
          .handleStepCompleted(any(), any(), any(), any());

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer).commitSync());
      loopSut.stop();

      // Engine called multiple times due to retries (1 initial + up to 3 retries = 4)
      verify(loopEngine, atLeast(2)).handleStepCompleted(any(), any(), any(), any());
    }

    @Test
    @DisplayName("consumeLoop_poisonPillThenValid_processesSecondRecordAndCommitsTwice")
    void consumeLoop_poisonPillThenValid_processesSecondRecordAndCommitsTwice() throws Exception {
      byte[] invalidJson = "not-valid-json".getBytes();
      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>("core.civitas.dataset.frost.result", 0, 0L, "key1", invalidJson);

      Map<String, Object> msg = new HashMap<>();
      msg.put("type", "STEP_COMPLETED");
      msg.put("sagaId", "saga-1");
      msg.put("stepId", "create-project");
      byte[] validJson = objectMapper.writeValueAsBytes(msg);
      ConsumerRecord<String, byte[]> goodRecord =
          new ConsumerRecord<>("core.civitas.dataset.frost.result", 0, 1L, "key2", validJson);

      TopicPartition tp = new TopicPartition("core.civitas.dataset.frost.result", 0);
      ConsumerRecords<String, byte[]> records =
          new ConsumerRecords<>(Map.of(tp, List.of(badRecord, goodRecord)));

      when(loopConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      loopSut.start();
      await().atMost(2, SECONDS).untilAsserted(() -> verify(loopConsumer, times(2)).commitSync());
      loopSut.stop();

      verify(loopEngine).handleStepCompleted(eq("saga-1"), eq("create-project"), any(), any());
    }
  }
}

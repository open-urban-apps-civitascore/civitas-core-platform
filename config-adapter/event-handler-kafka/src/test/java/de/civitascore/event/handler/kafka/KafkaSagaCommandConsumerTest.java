/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.event.handler.kafka;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.util.BackoffCalculator;
import de.civitascore.configadapter.util.PayloadConverter;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KafkaSagaCommandConsumerTest {

  @SuppressWarnings("unchecked")
  private final KafkaConsumer<String, byte[]> mockConsumer = mock(KafkaConsumer.class);

  @SuppressWarnings("unchecked")
  private final KafkaProducer<String, byte[]> mockProducer = mock(KafkaProducer.class);

  private final SagaCommandHandler frostHandler = mock(SagaCommandHandler.class);

  @Test
  @DisplayName("start subscribes to execute and compensate topics for each handler")
  void shouldSubscribeToCorrectTopics() {
    when(frostHandler.adapter()).thenReturn("frost");
    SagaCommandHandler apisixHandler = mock(SagaCommandHandler.class);
    when(apisixHandler.adapter()).thenReturn("apisix");

    // Stop immediately after subscribing
    when(mockConsumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());

    try (KafkaSagaCommandConsumer consumer =
        new KafkaSagaCommandConsumer(
            mockConsumer, mockProducer, Map.of("frost", frostHandler, "apisix", apisixHandler))) {
      consumer.start();
      consumer.stop();
    }

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> topicCaptor = ArgumentCaptor.forClass(List.class);
    verify(mockConsumer).subscribe(topicCaptor.capture(), any(ConsumerRebalanceListener.class));

    List<String> topics = topicCaptor.getValue();
    assertEquals(4, topics.size());
    assertTrue(topics.contains("de.civitascore.dataset.frost.execute"));
    assertTrue(topics.contains("de.civitascore.dataset.frost.compensate"));
    assertTrue(topics.contains("de.civitascore.dataset.apisix.execute"));
    assertTrue(topics.contains("de.civitascore.dataset.apisix.compensate"));
  }

  @SuppressWarnings("unchecked")
  @Test
  @DisplayName("start is idempotent — second call is no-op")
  void shouldNotStartTwice() {
    when(mockConsumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());

    try (KafkaSagaCommandConsumer consumer =
        new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
      consumer.start();
      consumer.start();
      consumer.stop();
    }

    verify(mockConsumer, times(2)).subscribe(any(List.class), any(ConsumerRebalanceListener.class));
  }

  @Test
  @DisplayName("close calls stop, consumer.close(), and producer.close()")
  void shouldCloseAllResources() {
    try (KafkaSagaCommandConsumer consumer =
        new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
      // close() is called by try-with-resources
    }

    verify(mockConsumer).close();
    verify(mockProducer).close();
  }

  @Nested
  @DisplayName("Record processing")
  class RecordProcessing {

    @Test
    @DisplayName("routes command to handler and publishes result")
    @SuppressWarnings("unchecked")
    void shouldRouteCommandAndPublishResult() throws Exception {
      SagaCommandResult expectedResult =
          SagaCommandResult.success(
              "saga-001", "create-project", Map.of("projectId", "42"), Map.of("projectId", "42"));
      when(frostHandler.handle(any(SagaCommandMessage.class))).thenReturn(expectedResult);

      Future<Object> mockFuture = mock(Future.class);
      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);

      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "create-project");
      commandMap.put("adapter", "frost");
      commandMap.put("operation", "CREATE_PROJECT");
      commandMap.put("datasetName", "Test Dataset");
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      // First poll returns records, second poll triggers stop
      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(frostHandler).handle(any()));
        consumer.stop();
      }

      ArgumentCaptor<SagaCommandMessage> cmdCaptor =
          ArgumentCaptor.forClass(SagaCommandMessage.class);
      verify(frostHandler).handle(cmdCaptor.capture());

      SagaCommandMessage captured = cmdCaptor.getValue();
      assertEquals("EXECUTE_STEP", captured.type());
      assertEquals("saga-001", captured.sagaId());
      assertEquals("create-project", captured.stepId());
      assertEquals("frost", captured.adapter());
      assertEquals("CREATE_PROJECT", captured.operation());
      assertEquals("Test Dataset", captured.payload().get("datasetName"));

      // Verify result was published to correct topic
      ArgumentCaptor<ProducerRecord<String, byte[]>> recordCaptor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      verify(mockProducer).send(recordCaptor.capture());

      ProducerRecord<String, byte[]> published = recordCaptor.getValue();
      assertEquals("de.civitascore.dataset.frost.result", published.topic());
      assertEquals("saga-001", published.key());
    }

    @Test
    @DisplayName("ignores command without adapter field")
    void shouldIgnoreCommandWithoutAdapter() throws Exception {
      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "create-project");
      commandMap.put("operation", "CREATE_PROJECT");
      // no "adapter" field
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await()
            .atMost(2, SECONDS)
            .untilAsserted(() -> verify(mockConsumer, atLeast(2)).poll(any(Duration.class)));
        consumer.stop();
      }

      verify(frostHandler, never()).handle(any());
      verify(mockProducer, never()).send(any());
    }

    @Test
    @DisplayName("ignores command for unregistered adapter")
    void shouldIgnoreCommandForUnregisteredAdapter() throws Exception {
      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "deploy-pipeline");
      commandMap.put("adapter", "redpanda");
      commandMap.put("operation", "DEPLOY_PIPELINE");
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.redpanda.execute", 0, 0L, "saga-001", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.redpanda.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await()
            .atMost(2, SECONDS)
            .untilAsserted(() -> verify(mockConsumer, atLeast(2)).poll(any(Duration.class)));
        consumer.stop();
      }

      verify(frostHandler, never()).handle(any());
      verify(mockProducer, never()).send(any());
    }

    @Test
    @DisplayName("commits after each record")
    @SuppressWarnings("unchecked")
    void shouldCommitAfterEachRecord() throws Exception {
      SagaCommandResult result =
          SagaCommandResult.success("saga-001", "step-1", Map.of(), Map.of());
      when(frostHandler.handle(any(SagaCommandMessage.class))).thenReturn(result);

      Future<Object> mockFuture = mock(Future.class);
      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);

      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "step-1");
      commandMap.put("adapter", "frost");
      commandMap.put("operation", "CREATE_PROJECT");
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(mockConsumer).commitSync());
        consumer.stop();
      }
    }

    @Test
    @DisplayName("continues processing after deserialization error and commits offset")
    void shouldContinueAfterDeserializationError() throws Exception {
      // Invalid JSON
      byte[] invalidJson = "not-valid-json".getBytes();

      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", invalidJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, badRecord);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(mockConsumer).commitSync());
        consumer.stop();
      }

      // Should not crash — handler never called, producer never called
      verify(frostHandler, never()).handle(any());
      verify(mockProducer, never()).send(any());
    }

    @Test
    @DisplayName("retries when publishResult fails then commits")
    @SuppressWarnings("unchecked")
    void consumeLoop_publishResultFails_retriesThenCommits() throws Exception {
      SagaCommandResult result =
          SagaCommandResult.success("saga-001", "step-1", Map.of(), Map.of());
      when(frostHandler.handle(any(SagaCommandMessage.class))).thenReturn(result);

      Future<Object> mockFuture = mock(Future.class);
      when(mockFuture.get(any(Long.class), any(java.util.concurrent.TimeUnit.class)))
          .thenThrow(
              new java.util.concurrent.ExecutionException(new RuntimeException("broker down")));
      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);

      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "step-1");
      commandMap.put("adapter", "frost");
      commandMap.put("operation", "CREATE_PROJECT");
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());
      when(mockConsumer.assignment()).thenReturn(Set.of(tp));

      BackoffCalculator fastBackoff = new BackoffCalculator(1L, 10L);
      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(
              mockConsumer, mockProducer, Map.of("frost", frostHandler), fastBackoff)) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(mockConsumer).commitSync());
        consumer.stop();
      }

      // Handler called multiple times due to retries (publish failure = RuntimeException =
      // transient)
      verify(frostHandler, atLeast(2)).handle(any());
    }

    @Test
    @DisplayName("poison pill then valid record — processes second record and commits twice")
    @SuppressWarnings("unchecked")
    void consumeLoop_poisonPillThenValid_processesSecondAndCommitsTwice() throws Exception {
      SagaCommandResult result =
          SagaCommandResult.success("saga-001", "step-1", Map.of(), Map.of());
      when(frostHandler.handle(any(SagaCommandMessage.class))).thenReturn(result);

      Future<Object> mockFuture = mock(Future.class);
      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);

      byte[] invalidJson = "not-valid-json".getBytes();
      ConsumerRecord<String, byte[]> badRecord =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-001", invalidJson);

      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-002");
      commandMap.put("sagaId", "saga-001");
      commandMap.put("stepId", "step-1");
      commandMap.put("adapter", "frost");
      commandMap.put("operation", "CREATE_PROJECT");
      byte[] validJson = PayloadConverter.writeValueAsBytes(commandMap);
      ConsumerRecord<String, byte[]> goodRecord =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 1L, "saga-001", validJson);

      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records =
          new ConsumerRecords<>(Map.of(tp, List.of(badRecord, goodRecord)));

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      BackoffCalculator fastBackoff = new BackoffCalculator(1L, 10L);
      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(
              mockConsumer, mockProducer, Map.of("frost", frostHandler), fastBackoff)) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(mockConsumer, times(2)).commitSync());
        consumer.stop();
      }

      verify(frostHandler, times(1)).handle(any());
    }
  }

  @Nested
  @DisplayName("Result publishing")
  class ResultPublishing {

    @Test
    @DisplayName("serializes SagaCommandResult as JSON with sagaId as key")
    @SuppressWarnings("unchecked")
    void shouldSerializeResultWithSagaIdAsKey() throws Exception {
      SagaCommandResult expectedResult =
          SagaCommandResult.failure("saga-002", "create-project", "FROST error");
      when(frostHandler.handle(any(SagaCommandMessage.class))).thenReturn(expectedResult);

      Future<Object> mockFuture = mock(Future.class);
      when(mockProducer.send(any(ProducerRecord.class))).thenReturn(mockFuture);

      Map<String, Object> commandMap = new HashMap<>();
      commandMap.put("type", "EXECUTE_STEP");
      commandMap.put("messageId", "msg-001");
      commandMap.put("sagaId", "saga-002");
      commandMap.put("stepId", "create-project");
      commandMap.put("adapter", "frost");
      commandMap.put("operation", "CREATE_PROJECT");
      byte[] commandJson = PayloadConverter.writeValueAsBytes(commandMap);

      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              "de.civitascore.dataset.frost.execute", 0, 0L, "saga-002", commandJson);
      TopicPartition tp = new TopicPartition("de.civitascore.dataset.frost.execute", 0);
      ConsumerRecords<String, byte[]> records = consumerRecords(tp, record);

      when(mockConsumer.poll(any(Duration.class)))
          .thenReturn(records)
          .thenReturn(ConsumerRecords.empty());

      try (KafkaSagaCommandConsumer consumer =
          new KafkaSagaCommandConsumer(mockConsumer, mockProducer, Map.of("frost", frostHandler))) {
        consumer.start();
        await().atMost(2, SECONDS).untilAsserted(() -> verify(mockProducer).send(any()));
        consumer.stop();
      }

      ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
          ArgumentCaptor.forClass(ProducerRecord.class);
      verify(mockProducer).send(captor.capture());

      ProducerRecord<String, byte[]> published = captor.getValue();
      assertEquals("de.civitascore.dataset.frost.result", published.topic());
      assertEquals("saga-002", published.key());

      // Verify the JSON content
      Map<String, Object> resultMap = PayloadConverter.readMap(published.value());
      assertEquals("STEP_FAILED", resultMap.get("type"));
      assertEquals("saga-002", resultMap.get("sagaId"));
      assertEquals("create-project", resultMap.get("stepId"));
      assertEquals("FROST error", resultMap.get("error"));
    }
  }

  @SuppressWarnings("deprecation")
  private ConsumerRecords<String, byte[]> consumerRecords(
      TopicPartition tp, ConsumerRecord<String, byte[]> record) {
    return new ConsumerRecords<>(Map.of(tp, List.of(record)));
  }
}

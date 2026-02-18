/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.orchestrator.kafka;

import com.civitas.configadapter.orchestrator.engine.SagaEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer that listens for adapter result events and routes them to the {@link SagaEngine}.
 *
 * <p>Subscribes to adapter result topics and deserializes the response messages, then calls the
 * appropriate engine method based on the message type:
 *
 * <ul>
 *   <li>{@code STEP_COMPLETED} → {@link SagaEngine#handleStepCompleted}
 *   <li>{@code STEP_FAILED} → {@link SagaEngine#handleStepFailed}
 *   <li>{@code COMPENSATION_COMPLETED} → {@link SagaEngine#handleCompensationCompleted}
 *   <li>{@code COMPENSATION_FAILED} → {@link SagaEngine#handleCompensationFailed}
 * </ul>
 */
public class SagaResultConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(SagaResultConsumer.class);

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  /** Topics that adapters publish results to. */
  static final List<String> RESULT_TOPICS =
      List.of(
          "core.civitas.dataset.frost.result",
          "core.civitas.dataset.apisix.result",
          "core.civitas.dataset.redpanda.result");

  private final KafkaConsumer<String, byte[]> consumer;
  private final SagaEngine engine;
  private final ObjectMapper objectMapper;
  private static final long STOP_TIMEOUT_MS = 5000L;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;

  public SagaResultConsumer(KafkaConsumer<String, byte[]> consumer, SagaEngine engine) {
    this.consumer = consumer;
    this.engine = engine;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  /** Start consuming adapter results in a virtual thread. */
  public void start() {
    if (running.compareAndSet(false, true)) {
      consumer.subscribe(RESULT_TOPICS);
      consumerThread = Thread.ofVirtual().name("saga-result-consumer").start(this::consumeLoop);
      LOG.info("SagaResultConsumer started, subscribed to {}", RESULT_TOPICS);
    }
  }

  /** Stop consuming. */
  public void stop() {
    LOG.info("Stopping SagaResultConsumer");
    running.set(false);
    if (consumerThread != null) {
      try {
        consumerThread.join(STOP_TIMEOUT_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    consumer.close();
  }

  private void consumeLoop() {
    try {
      while (running.get()) {
        try {
          ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
          for (ConsumerRecord<String, byte[]> record : records) {
            try {
              processRecord(record.value());
              consumer.commitSync();
            } catch (IOException e) {
              LOG.error(
                  "Failed to process adapter result from topic {}: {}",
                  Encode.forJava(record.topic()),
                  Encode.forJava(String.valueOf(e.getMessage())),
                  e);
            }
          }
        } catch (WakeupException e) {
          if (running.get()) {
            LOG.warn("Unexpected WakeupException in SagaResultConsumer loop", e);
          }
        } catch (RuntimeException e) {
          if (running.get()) {
            LOG.error("Error in SagaResultConsumer loop", e);
          }
        }
      }
    } finally {
      LOG.info("SagaResultConsumer loop ended");
    }
  }

  @SuppressWarnings("unchecked")
  private void processRecord(byte[] value) throws IOException {
    Map<String, Object> message = objectMapper.readValue(value, MAP_TYPE);

    String type = (String) message.get("type");
    String sagaId = (String) message.get("sagaId");
    String stepId = (String) message.get("stepId");

    if (type == null || sagaId == null || stepId == null) {
      LOG.warn("Ignoring malformed adapter result: missing type, sagaId, or stepId");
      return;
    }

    switch (type) {
      case "STEP_COMPLETED" -> {
        Map<String, Object> resultData =
            message.containsKey("resultData")
                ? (Map<String, Object>) message.get("resultData")
                : Map.of();
        Map<String, Object> compensationData =
            message.containsKey("compensationData")
                ? (Map<String, Object>) message.get("compensationData")
                : Map.of();
        engine.handleStepCompleted(sagaId, stepId, resultData, compensationData);
        LOG.debug(
            "Routed STEP_COMPLETED to engine: sagaId={}, stepId={}",
            Encode.forJava(sagaId),
            Encode.forJava(stepId));
      }
      case "STEP_FAILED" -> {
        String error = (String) message.getOrDefault("error", "Unknown error");
        engine.handleStepFailed(sagaId, stepId, error);
        LOG.debug(
            "Routed STEP_FAILED to engine: sagaId={}, stepId={}",
            Encode.forJava(sagaId),
            Encode.forJava(stepId));
      }
      case "COMPENSATION_COMPLETED" -> {
        engine.handleCompensationCompleted(sagaId, stepId);
        LOG.debug(
            "Routed COMPENSATION_COMPLETED to engine: sagaId={}, stepId={}",
            Encode.forJava(sagaId),
            Encode.forJava(stepId));
      }
      case "COMPENSATION_FAILED" -> {
        String error = (String) message.getOrDefault("error", "Unknown error");
        engine.handleCompensationFailed(sagaId, stepId, error);
        LOG.debug(
            "Routed COMPENSATION_FAILED to engine: sagaId={}, stepId={}",
            Encode.forJava(sagaId),
            Encode.forJava(stepId));
      }
      default ->
          LOG.warn(
              "Unknown adapter result type: {} for sagaId={}",
              Encode.forJava(type),
              Encode.forJava(sagaId));
    }
  }
}

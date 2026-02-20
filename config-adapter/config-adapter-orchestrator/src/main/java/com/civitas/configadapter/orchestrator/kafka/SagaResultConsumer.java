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
import com.civitas.configadapter.util.BackoffCalculator;
import com.civitas.configadapter.util.ConsumerRecordRetry;
import com.civitas.configadapter.util.RetryConsumerLoop;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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

  private final KafkaPollingConsumer pollingConsumer;
  private final RetryConsumerLoop<String, byte[]> loop;
  private final SagaEngine engine;
  private final ObjectMapper objectMapper;

  public SagaResultConsumer(KafkaConsumer<String, byte[]> consumer, SagaEngine engine) {
    this(
        consumer,
        engine,
        new BackoffCalculator(
            ConsumerRecordRetry.DEFAULT_INITIAL_BACKOFF_MS,
            ConsumerRecordRetry.DEFAULT_MAX_BACKOFF_MS));
  }

  SagaResultConsumer(
      KafkaConsumer<String, byte[]> consumer,
      SagaEngine engine,
      BackoffCalculator backoffCalculator) {
    this.engine = engine;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    this.pollingConsumer = new KafkaPollingConsumer(consumer);
    this.loop =
        new RetryConsumerLoop<>(
            pollingConsumer,
            rec -> () -> processRecord(rec.value()),
            backoffCalculator,
            LOG,
            "saga-result-consumer");
    pollingConsumer.setRevocationCallback(loop::resetState);
  }

  /** Start consuming adapter results in a virtual thread. */
  public void start() {
    pollingConsumer.subscribe(RESULT_TOPICS);
    if (loop.start()) {
      LOG.info("SagaResultConsumer started, subscribed to {}", RESULT_TOPICS);
    }
  }

  /** Stop consuming. */
  public void stop() {
    loop.stop();
    pollingConsumer.delegate().close();
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

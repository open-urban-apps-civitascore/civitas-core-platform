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

import com.civitas.configadapter.model.saga.SagaContext;
import com.civitas.configadapter.model.saga.SagaType;
import com.civitas.configadapter.orchestrator.DatasetSagaOrchestrator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer that listens for dataset lifecycle trigger events from the portal-backend and
 * starts corresponding sagas via the {@link DatasetSagaOrchestrator}.
 *
 * <p>Subscribes to the trigger topic ({@code core.civitas.dataset.saga.trigger}) and expects
 * messages with:
 *
 * <ul>
 *   <li>{@code sagaType} — DATASET_CREATE, DATASET_UPDATE, DATASET_DELETE
 *   <li>{@code datasetId} — the dataset identifier
 *   <li>... additional trigger payload fields
 * </ul>
 */
public class SagaTriggerConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(SagaTriggerConsumer.class);

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  static final String TRIGGER_TOPIC = "core.civitas.dataset.saga.trigger";

  private final KafkaConsumer<String, byte[]> consumer;
  private final DatasetSagaOrchestrator orchestrator;
  private final ObjectMapper objectMapper;
  private static final long STOP_TIMEOUT_MS = 5000L;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;

  public SagaTriggerConsumer(
      KafkaConsumer<String, byte[]> consumer, DatasetSagaOrchestrator orchestrator) {
    this.consumer = consumer;
    this.orchestrator = orchestrator;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  /** Start consuming trigger events in a virtual thread. */
  public void start() {
    if (running.compareAndSet(false, true)) {
      consumer.subscribe(List.of(TRIGGER_TOPIC));
      consumerThread = Thread.ofVirtual().name("saga-trigger-consumer").start(this::consumeLoop);
      LOG.info("SagaTriggerConsumer started, subscribed to {}", TRIGGER_TOPIC);
    }
  }

  /** Stop consuming. */
  public void stop() {
    LOG.info("Stopping SagaTriggerConsumer");
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
              processTrigger(record.value());
              consumer.commitSync();
            } catch (IOException e) {
              LOG.error(
                  "Failed to process trigger event: {}",
                  Encode.forJava(String.valueOf(e.getMessage())),
                  e);
            }
          }
        } catch (WakeupException e) {
          if (running.get()) {
            LOG.warn("Unexpected WakeupException in SagaTriggerConsumer loop", e);
          }
        } catch (RuntimeException e) {
          if (running.get()) {
            LOG.error("Error in SagaTriggerConsumer loop", e);
          }
        }
      }
    } finally {
      LOG.info("SagaTriggerConsumer loop ended");
    }
  }

  private void processTrigger(byte[] value) throws IOException {
    Map<String, Object> trigger = objectMapper.readValue(value, MAP_TYPE);

    String sagaTypeStr = (String) trigger.get("sagaType");
    String datasetId = (String) trigger.get("datasetId");

    if (sagaTypeStr == null || datasetId == null) {
      LOG.warn("Ignoring malformed trigger: missing sagaType or datasetId");
      return;
    }

    SagaType sagaType;
    try {
      sagaType = SagaType.valueOf(sagaTypeStr);
    } catch (IllegalArgumentException e) {
      LOG.warn("Ignoring trigger with unknown sagaType: {}", Encode.forJava(sagaTypeStr));
      return;
    }

    Optional<SagaContext> result = orchestrator.startSaga(sagaType, datasetId, trigger);

    if (result.isPresent()) {
      LOG.info(
          "Started saga: type={}, datasetId={}, sagaId={}",
          sagaType,
          Encode.forJava(datasetId),
          result.get().sagaId());
    } else {
      LOG.warn(
          "Rejected duplicate saga trigger: type={}, datasetId={}",
          sagaType,
          Encode.forJava(datasetId));
    }
  }
}

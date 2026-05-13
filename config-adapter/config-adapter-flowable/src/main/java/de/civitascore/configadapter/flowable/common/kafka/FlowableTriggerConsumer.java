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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.model.saga.SagaType;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.flowable.engine.RuntimeService;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer that listens for dataset lifecycle trigger events and starts the corresponding
 * Flowable process. Drop-in replacement for the custom orchestrator's {@code SagaTriggerConsumer}.
 *
 * <p>Consumes from {@code de.civitascore.dataset.saga.trigger} and expects the same JSON format:
 * {@code sagaType} (DATASET_CREATE/UPDATE/DELETE), {@code datasetId}, plus trigger payload fields.
 */
public class FlowableTriggerConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(FlowableTriggerConsumer.class);
  static final String TRIGGER_TOPIC = "de.civitascore.dataset.saga.trigger";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final RuntimeService runtimeService;
  private final org.flowable.engine.HistoryService historyService;
  private final ObjectMapper objectMapper;
  private volatile boolean running;
  private Thread consumerThread;
  private KafkaConsumer<String, byte[]> kafkaConsumer;

  public FlowableTriggerConsumer(
      RuntimeService runtimeService, org.flowable.engine.HistoryService historyService) {
    this.runtimeService = runtimeService;
    this.historyService = historyService;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  }

  /**
   * Start consuming trigger events in a virtual thread.
   *
   * @param kafkaConsumer the Kafka consumer (caller manages creation and config)
   */
  public void start(KafkaConsumer<String, byte[]> kafkaConsumer) {
    this.kafkaConsumer = kafkaConsumer;
    kafkaConsumer.subscribe(List.of(TRIGGER_TOPIC));
    running = true;
    consumerThread = Thread.ofVirtual().name("flowable-trigger-consumer").start(this::pollLoop);
    LOG.info("FlowableTriggerConsumer started, subscribed to {}", TRIGGER_TOPIC);
  }

  /** Stop consuming. */
  public void stop() {
    running = false;
    if (kafkaConsumer != null) {
      kafkaConsumer.wakeup();
    }
    if (consumerThread != null) {
      try {
        consumerThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (kafkaConsumer != null) {
      kafkaConsumer.close();
    }
    LOG.info("FlowableTriggerConsumer stopped");
  }

  private void pollLoop() {
    try {
      while (running) {
        ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofSeconds(1));
        for (var record : records) {
          try {
            processTriggerRecord(record);
            // Commit offset after each successfully processed record to prevent re-processing
            kafkaConsumer.commitSync(
                Map.of(
                    new org.apache.kafka.common.TopicPartition(record.topic(), record.partition()),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(record.offset() + 1)));
          } catch (java.io.IOException e) {
            // Permanent payload error (malformed JSON, wrong types) — skip and commit
            LOG.error(
                "Skipping malformed trigger (permanent error): {}",
                Encode.forJava(e.getMessage()),
                e);
            kafkaConsumer.commitSync(
                Map.of(
                    new org.apache.kafka.common.TopicPartition(record.topic(), record.partition()),
                    new org.apache.kafka.clients.consumer.OffsetAndMetadata(record.offset() + 1)));
          } catch (Exception e) {
            // Transient error (Flowable DB, Kafka, etc.) — do NOT commit, retry on next poll
            LOG.error(
                "Failed to process trigger (transient error): {}",
                Encode.forJava(e.getMessage()),
                e);
            break;
          }
        }
      }
    } catch (org.apache.kafka.common.errors.WakeupException e) {
      if (running) {
        throw e;
      }
    }
  }

  /**
   * Process a single trigger record. Uses Kafka record metadata (topic+partition+offset) as a
   * deterministic, stable business key for durable idempotency.
   */
  void processTriggerRecord(org.apache.kafka.clients.consumer.ConsumerRecord<String, byte[]> record)
      throws IOException {
    Map<String, Object> trigger = objectMapper.readValue(record.value(), MAP_TYPE);

    Object rawSagaType = trigger.get("sagaType");
    Object rawDatasetId = trigger.get("datasetId");

    if (!(rawSagaType instanceof String sagaTypeStr)
        || !(rawDatasetId instanceof String datasetId)) {
      LOG.warn("Ignoring malformed trigger: missing or wrong type for sagaType/datasetId");
      return;
    }

    SagaType sagaType;
    try {
      sagaType = SagaType.valueOf(sagaTypeStr);
    } catch (IllegalArgumentException e) {
      LOG.warn("Ignoring trigger with unknown sagaType: {}", Encode.forJava(sagaTypeStr));
      return;
    }

    String processKey = sagaType.getKey();
    if (processKey == null) {
      LOG.warn("No process key for saga type: {}", sagaType);
      return;
    }

    // Deterministic business key from Kafka record metadata — stable across replays.
    // Same record = same topic+partition+offset = same business key.
    // New trigger for same dataset = different offset = different business key.
    String businessKey = record.topic() + ":" + record.partition() + ":" + record.offset();

    // Idempotency: check active (runtime) first, then completed (history).
    long active =
        runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).count();
    if (active > 0) {
      LOG.warn(
          "Ignoring duplicate trigger: businessKey={} has {} active instance(s)",
          Encode.forJava(businessKey),
          active);
      return;
    }
    long completed =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceBusinessKey(businessKey)
            .finished()
            .count();
    if (completed > 0) {
      LOG.warn(
          "Ignoring replayed trigger: businessKey={} already completed",
          Encode.forJava(businessKey));
      return;
    }

    Map<String, Object> variables = new HashMap<>(trigger);
    variables.remove("sagaType");
    variables.computeIfAbsent("sagaId", k -> UUID.randomUUID().toString());
    // Always derive — never trust external payload for this control flag
    variables.put("hasPipelines", deriveHasPipelines(trigger));

    var instance = runtimeService.startProcessInstanceByKey(processKey, businessKey, variables);

    LOG.info(
        "Started Flowable process: type={}, datasetId={}, processKey={}, instanceId={}",
        sagaType,
        Encode.forJava(datasetId),
        processKey,
        instance.getId());
  }

  /**
   * Process a trigger from raw bytes with a synthetic record key. Package-private for unit tests
   * that don't need real Kafka record metadata.
   */
  void processTrigger(byte[] value) throws IOException {
    processTriggerRecord(
        new org.apache.kafka.clients.consumer.ConsumerRecord<>(
            TRIGGER_TOPIC, 0, System.nanoTime(), null, value));
  }

  /**
   * Derives the hasPipelines flag from the trigger payload. Matches the behavior of
   * DatasetSagaOrchestrator's HAS_PIPELINES predicate: true if dataPipelines or pipelineIds is a
   * non-empty list.
   */
  private static boolean deriveHasPipelines(Map<String, Object> trigger) {
    Object dataPipelines = trigger.get("dataPipelines");
    if (dataPipelines instanceof List<?> list && !list.isEmpty()) {
      return true;
    }
    Object pipelineIds = trigger.get("pipelineIds");
    return pipelineIds instanceof List<?> idList && !idList.isEmpty();
  }
}

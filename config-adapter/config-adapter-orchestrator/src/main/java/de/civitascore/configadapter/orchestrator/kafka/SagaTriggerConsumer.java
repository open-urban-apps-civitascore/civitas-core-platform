/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.configadapter.orchestrator.DatasetSagaOrchestrator;
import de.civitascore.configadapter.util.BackoffCalculator;
import de.civitascore.configadapter.util.ConsumerRecordRetry;
import de.civitascore.configadapter.util.RetryConsumerLoop;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer that listens for dataset lifecycle trigger events from the portal-backend and
 * starts corresponding sagas via the {@link DatasetSagaOrchestrator}.
 *
 * <p>Subscribes to the trigger topic ({@code de.civitascore.dataset.saga.trigger}) and expects
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

  static final String TRIGGER_TOPIC = "de.civitascore.dataset.saga.trigger";

  private final KafkaPollingConsumer pollingConsumer;
  private final RetryConsumerLoop<String, byte[]> loop;
  private final DatasetSagaOrchestrator orchestrator;
  private final ObjectMapper objectMapper;

  public SagaTriggerConsumer(
      KafkaConsumer<String, byte[]> consumer, DatasetSagaOrchestrator orchestrator) {
    this(
        consumer,
        orchestrator,
        new BackoffCalculator(
            ConsumerRecordRetry.DEFAULT_INITIAL_BACKOFF_MS,
            ConsumerRecordRetry.DEFAULT_MAX_BACKOFF_MS));
  }

  SagaTriggerConsumer(
      KafkaConsumer<String, byte[]> consumer,
      DatasetSagaOrchestrator orchestrator,
      BackoffCalculator backoffCalculator) {
    this.orchestrator = orchestrator;
    this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    this.pollingConsumer = new KafkaPollingConsumer(consumer);
    this.loop =
        new RetryConsumerLoop<>(
            pollingConsumer,
            rec -> () -> processTrigger(rec.value()),
            backoffCalculator,
            LOG,
            "saga-trigger-consumer");
    pollingConsumer.setRevocationCallback(loop::resetState);
  }

  /** Start consuming trigger events in a virtual thread. */
  public void start() {
    pollingConsumer.subscribe(List.of(TRIGGER_TOPIC));
    if (loop.start()) {
      LOG.info("SagaTriggerConsumer started, subscribed to {}", TRIGGER_TOPIC);
    }
  }

  /** Stop consuming. */
  public void stop() {
    loop.stop();
    pollingConsumer.delegate().close();
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

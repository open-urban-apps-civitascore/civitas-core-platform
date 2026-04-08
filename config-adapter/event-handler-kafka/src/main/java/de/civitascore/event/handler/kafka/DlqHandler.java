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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.model.ConfigEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Dead Letter Queue (DLQ) operations for failed Kafka events. Responsible for sending
 * failed events to the DLQ topic with safe metadata (no PII, no stack traces) and optionally
 * delegating failure result publishing to the adapter.
 */
class DlqHandler {

  private static final Logger logger = LoggerFactory.getLogger(DlqHandler.class);

  private final String dlqTopic;
  private final long publishTimeoutMs;
  private final int maxRetries;
  private final KafkaProducer<String, CloudEvent> kafkaProducer;
  private final ConfigAdapter adapter;
  private final ObjectMapper objectMapper;

  DlqHandler(
      String dlqTopic,
      long publishTimeoutMs,
      int maxRetries,
      KafkaProducer<String, CloudEvent> kafkaProducer,
      ConfigAdapter adapter) {
    this.dlqTopic = dlqTopic;
    this.publishTimeoutMs = publishTimeoutMs;
    this.maxRetries = maxRetries;
    this.kafkaProducer = kafkaProducer;
    this.adapter = adapter;
    this.objectMapper = ObjectMapperFactory.createObjectMapper();
  }

  /**
   * Sends a failed event to the Dead Letter Queue (DLQ). Optionally delegates failure result
   * publishing to the adapter. This method is synchronous to ensure data safety — if DLQ send
   * fails, a RuntimeException is thrown so the event will be reprocessed on the next poll.
   *
   * @param record the original Kafka record
   * @param exception the adapter exception that caused the failure
   * @param publishFailure if true, delegates failure result publishing to the adapter
   * @throws RuntimeException if DLQ send fails, causing the event to be reprocessed
   */
  void sendToDLQ(
      ConsumerRecord<String, CloudEvent> record,
      AdapterException exception,
      boolean publishFailure) {
    CloudEvent originalEvent = record.value();

    try {
      CloudEvent dlqEvent =
          CloudEventBuilder.from(originalEvent)
              .withId(UUID.randomUUID().toString())
              .withExtension("dlqerrorcode", String.valueOf(exception.getNumericCode()))
              .withExtension("dlqerrormsg", exception.getSafeExternalMessage())
              .withExtension("dlqoriginaltopic", record.topic())
              .withExtension("dlqtimestamp", OffsetDateTime.now().toString())
              .withExtension("dlqretrycount", String.valueOf(maxRetries))
              .build();

      kafkaProducer
          .send(new ProducerRecord<>(dlqTopic, dlqEvent))
          .get(publishTimeoutMs, TimeUnit.MILLISECONDS);

      logger.info(
          "Sent event {} to DLQ topic {}",
          Encode.forJava(originalEvent.getId()),
          Encode.forJava(dlqTopic));

      if (publishFailure) {
        delegateFailureResultToAdapter(originalEvent, exception);
      }

    } catch (Exception e) {
      logger.error(
          "CRITICAL: Failed to send event {} to DLQ. Event will be reprocessed. Error: {}",
          Encode.forJava(originalEvent.getId()),
          Encode.forJava(String.valueOf(e.getMessage())),
          e);
      throw new RuntimeException("DLQ send failed - event will be reprocessed", e);
    }
  }

  private void delegateFailureResultToAdapter(
      CloudEvent originalEvent, AdapterException exception) {
    try {
      if (originalEvent.getData() == null) {
        logger.debug("Cannot publish failure result: original event has no data");
        return;
      }

      ConfigEvent configEvent =
          objectMapper.readValue(originalEvent.getData().toBytes(), ConfigEvent.class);

      adapter.publishFailureResult(configEvent, exception);

    } catch (Exception e) {
      logger.warn(
          "Failed to publish failure result for event {}: {}",
          Encode.forJava(originalEvent.getId()),
          Encode.forJava(String.valueOf(e.getMessage())));
    }
  }
}

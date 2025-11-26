/**
 * Copyright (c) 2012 - 2025 Data In Motion and others. All rights reserved.
 *
 * <p>This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * <p>SPDX-License-Identifier: EPL-2.0
 *
 * <p>Contributors: Data In Motion - initial API and implementation
 */
package com.civitas.event.handler.kafka;

import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.ConfigResultEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone Kafka implementation of EventPublisher. Publishes ConfigResultEvents to Kafka topics
 * as CloudEvents. This class can be used independently when separate consumer and publisher are
 * configured.
 */
public class KafkaEventPublisher implements EventPublisher, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  private final KafkaProducer<String, CloudEvent> kafkaProducer;

  public KafkaEventPublisher(ApplicationConfig config) {
    Properties producerProps = new Properties();
    producerProps.put(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092"));
    producerProps.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringSerializer.class.getName());
    producerProps.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
    producerProps.put(ProducerConfig.RETRIES_CONFIG, 3);

    this.kafkaProducer = new KafkaProducer<>(producerProps);

    logger.info(
        "Kafka event publisher initialized with bootstrap servers: {}",
        config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092"));
  }

  @Override
  public void publish(String topic, ConfigResultEvent resultEvent) {
    CloudEvent cloudEvent = convertToCloudEvent(resultEvent);
    try {
      ProducerRecord<String, CloudEvent> record =
          new ProducerRecord<>(topic, cloudEvent.getId(), cloudEvent);

      kafkaProducer.send(
          record,
          (metadata, exception) -> {
            if (exception != null) {
              logger.error(
                  "Failed to publish result event {} to topic {}",
                  cloudEvent.getId(),
                  topic,
                  exception);
            } else {
              logger.debug(
                  "Published result event {} to topic {} partition {} offset {}",
                  cloudEvent.getId(),
                  metadata.topic(),
                  metadata.partition(),
                  metadata.offset());
            }
          });
    } catch (Exception e) {
      logger.error("Error publishing result event {} to topic {}", cloudEvent.getId(), topic, e);
    }
  }

  /**
   * Converts a ConfigResultEvent to a CloudEvent for transmission over Kafka.
   *
   * @param resultEvent the configuration result event
   * @return a CloudEvent representation
   */
  private CloudEvent convertToCloudEvent(ConfigResultEvent resultEvent) {
    CloudEventBuilder builder =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create(resultEvent.source()))
            .withType("core.civitas.idm.processing.result")
            .withTime(resultEvent.timestamp())
            .withData("application/json", "{}".getBytes())
            .withExtension("correlationid", resultEvent.correlationId())
            .withExtension("originalmessageid", resultEvent.originalMessageId())
            .withExtension("status", resultEvent.status().name())
            .withExtension("operation", resultEvent.operation())
            .withExtension("targetresource", resultEvent.targetResource());

    if (resultEvent.message() != null) {
      builder.withExtension("message", resultEvent.message());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.SUCCESS
        && resultEvent.resourceId() != null) {
      builder.withExtension("resourceid", resultEvent.resourceId());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.FAILURE
        && resultEvent.errorCode() != null) {
      builder.withExtension("errorcode", resultEvent.errorCode());
    }

    if (resultEvent.status() == ConfigResultEvent.Status.FAILURE && resultEvent.message() != null) {
      builder.withExtension("errormessage", resultEvent.message());
    }

    return builder.build();
  }

  @Override
  public void close() {
    try {
      kafkaProducer.flush();
      kafkaProducer.close();
      logger.info("Kafka publisher closed");
    } catch (Exception e) {
      logger.error("Error closing Kafka publisher", e);
    }
  }
}

package com.civitas.event.handler.kafka;

import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventSerializer;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone Kafka implementation of EventPublisher. Publishes CloudEvents to Kafka topics. This
 * class can be used independently when separate consumer and publisher are configured.
 */
public class KafkaEventPublisher implements EventPublisher, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  private final KafkaProducer<String, CloudEvent> kafkaProducer;

  public KafkaEventPublisher(AppConfig config) {
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
  public void publish(String topic, CloudEvent event) {
    try {
      ProducerRecord<String, CloudEvent> record = new ProducerRecord<>(topic, event.getId(), event);

      kafkaProducer.send(
          record,
          (metadata, exception) -> {
            if (exception != null) {
              logger.error(
                  "Failed to publish event {} to topic {}", event.getId(), topic, exception);
            } else {
              logger.debug(
                  "Published event {} to topic {} partition {} offset {}",
                  event.getId(),
                  metadata.topic(),
                  metadata.partition(),
                  metadata.offset());
            }
          });
    } catch (Exception e) {
      logger.error("Error publishing event {} to topic {}", event.getId(), topic, e);
    }
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

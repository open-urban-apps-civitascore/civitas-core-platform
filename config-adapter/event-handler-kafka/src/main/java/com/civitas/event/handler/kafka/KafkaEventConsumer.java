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

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.messaging.EventConsumer;
import io.cloudevents.CloudEvent;
import io.cloudevents.kafka.CloudEventDeserializer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka implementation of EventConsumer. Consumes CloudEvents from Kafka topics and processes them
 * through the adapter.
 */
public class KafkaEventConsumer implements EventConsumer {

  private static final Logger logger = LoggerFactory.getLogger(KafkaEventConsumer.class);

  private static final String KAFKA_GROUP_ID = "kafka.group.id";
  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  private final KafkaConsumer<String, CloudEvent> kafkaConsumer;
  private final ConfigAdapter adapter;
  private final CloudEventProcessor processor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;

  public KafkaEventConsumer(ApplicationConfig config, ConfigAdapter adapter) {
    this.adapter = adapter;
    this.processor = new CloudEventProcessor(adapter);

    Properties props = new Properties();
    props.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092"));
    props.put(
        ConsumerConfig.GROUP_ID_CONFIG, config.getProperty(KAFKA_GROUP_ID, "config-adapter-group"));
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    this.kafkaConsumer = new KafkaConsumer<>(props);

    List<String> topicList = new ArrayList<>(adapter.getSubscribedTopics());
    kafkaConsumer.subscribe(topicList);

    logger.info(
        "Kafka event consumer initialized for adapter {} with {} topic(s): {}",
        adapter.getClass().getSimpleName(),
        topicList.size(),
        topicList);
  }

  @Override
  public void start() {
    if (running.compareAndSet(false, true)) {
      consumerThread = Thread.ofVirtual().start(this::consume);
      logger.info("Kafka event consumer started");
    }
  }

  private void consume() {
    logger.info("Starting consumption loop");
    while (running.get()) {
      try {
        ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofMillis(1000));

        if (records.isEmpty()) {
          continue;
        }

        boolean batchSuccess = true;
        for (var record : records) {
          try {
              processor.handleEvent(record.topic(), record.value());
          } catch (Exception e) {
              logger.error("Critical error processing event ID {}. Stopping consumer to prevent data loss.",
                      record.value().getId(), e);
              batchSuccess = false;
              running.set(false);
              break;
          }
        }

        if (batchSuccess && running.get()) {
          try {
              kafkaConsumer.commitSync();
          } catch (Exception commitException) {
              logger.error("Failed to commit offsets", commitException);
              running.set(false);
          }
        }
      } catch (Exception e) {
        logger.error("Error consuming messages", e);
        if (!running.get()) {
          break;
        }
      }
    }
    logger.info("Consumption loop ended");
  }

  public void stop() {
    logger.info("Stopping Kafka event consumer");
    running.set(false);
    if (consumerThread != null) {
      try {
        consumerThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void close() {
    stop();
    try {
      kafkaConsumer.close();
      logger.info("Kafka consumer closed");
    } catch (Exception e) {
      logger.error("Error closing Kafka consumer", e);
    }
    try {
      adapter.close();
      logger.info("Adapter closed");
    } catch (Exception e) {
      logger.error("Error closing adapter", e);
    }
  }
}

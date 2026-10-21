/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads connection test requests from Kafka and publishes their results. Auto-commit may replay a
 * few seconds after a crash, which the dispatcher drops as expired.
 */
public final class ConnectionTestConsumer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectionTestConsumer.class);

  private final Consumer<String, String> consumer;
  private final Producer<String, String> producer;
  private final String requestTopic;
  private final String resultTopic;
  private final ConnectionTestDispatcher dispatcher;
  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private volatile boolean running;
  private final AtomicBoolean closed = new AtomicBoolean();
  private Thread pollThread;

  ConnectionTestConsumer(
      Consumer<String, String> consumer,
      Producer<String, String> producer,
      String requestTopic,
      String resultTopic,
      ConnectionTestService service,
      ConnectionTestSettings settings,
      Clock clock) {
    this.consumer = consumer;
    this.producer = producer;
    this.requestTopic = requestTopic;
    this.resultTopic = resultTopic;
    this.dispatcher = new ConnectionTestDispatcher(service, this::publish, settings, clock);
  }

  /** Starts polling on a virtual thread and returns at once. */
  public void start() {
    if (running) {
      throw new IllegalStateException("ConnectionTestConsumer is already running");
    }
    consumer.subscribe(List.of(requestTopic));
    running = true;
    pollThread = Thread.ofVirtual().name("connection-test-consumer").start(this::pollLoop);
    LOG.info("Connection test consumer started, subscribed to {}", requestTopic);
  }

  @SuppressWarnings("PMD.AvoidCatchingGenericException") // Loop must survive transient poll errors
  private void pollLoop() {
    while (running) {
      try {
        for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofSeconds(1))) {
          handle(record);
        }
      } catch (WakeupException e) {
        // close() calls wakeup(); while still running it is unexpected and must propagate.
        if (running) {
          throw e;
        }
        return;
      } catch (RuntimeException e) {
        if (running) {
          LOG.error("Error in connection test consumer poll loop, continuing", e);
        }
      }
    }
  }

  void handle(ConsumerRecord<String, String> record) {
    ConnectionTestRequest request;
    try {
      request = mapper.readValue(record.value(), ConnectionTestRequest.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      LOG.warn(
          "Skipping a malformed connection test request at {}-{}@{}: {}",
          record.topic(),
          record.partition(),
          record.offset(),
          e.getClass().getSimpleName());
      return;
    }
    dispatcher.dispatch(request);
  }

  private void publish(ConnectionTestResult result) {
    String json;
    try {
      json = mapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      LOG.error(
          "Serializing the result of connection test {} failed",
          Encode.forJava(result.requestId()),
          e);
      return;
    }
    producer.send(
        new ProducerRecord<>(resultTopic, result.requestId(), json),
        (metadata, exception) -> {
          if (exception != null) {
            LOG.error(
                "Publishing the result of connection test {} failed",
                Encode.forJava(result.requestId()),
                exception);
          }
        });
  }

  @Override
  // try-with-resources would close the producer without a time limit.
  @SuppressWarnings("PMD.UseTryWithResources")
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    running = false;
    consumer.wakeup();
    if (pollThread != null) {
      try {
        pollThread.join(5000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      dispatcher.close();
      consumer.close();
    } finally {
      producer.close(Duration.ofSeconds(5));
    }
    LOG.info("Connection test consumer stopped");
  }
}

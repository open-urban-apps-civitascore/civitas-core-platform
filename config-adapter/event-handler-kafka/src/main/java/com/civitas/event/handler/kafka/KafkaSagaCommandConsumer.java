/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package com.civitas.event.handler.kafka;

import com.civitas.configadapter.adapter.SagaCommandHandler;
import com.civitas.configadapter.adapter.SagaCommandMessage;
import com.civitas.configadapter.adapter.SagaCommandResult;
import com.civitas.configadapter.util.BackoffCalculator;
import com.civitas.configadapter.util.ConsumerRecordRetry;
import com.civitas.configadapter.util.PayloadConverter;
import com.civitas.configadapter.util.PollingConsumer;
import com.civitas.configadapter.util.RetryConsumerLoop;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka consumer for saga command messages dispatched by the orchestrator. Runs parallel to the
 * existing {@link KafkaEventHandler} (which handles CloudEvents).
 *
 * <p>Subscribes to adapter execute and compensate topics (e.g. {@code
 * core.civitas.dataset.frost.execute}, {@code core.civitas.dataset.frost.compensate}), routes
 * commands to the matching {@link SagaCommandHandler}, and publishes the result to the adapter's
 * result topic (e.g. {@code core.civitas.dataset.frost.result}).
 *
 * <p>Messages are raw JSON (not CloudEvents), matching the format produced by {@code
 * KafkaSagaActionDispatcher}.
 */
public class KafkaSagaCommandConsumer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(KafkaSagaCommandConsumer.class);

  private static final String TOPIC_PREFIX = "core.civitas.dataset.";
  private static final long PUBLISH_TIMEOUT_MS = 5000L;

  private final KafkaPollingConsumer pollingConsumer;
  private final RetryConsumerLoop<String, byte[]> loop;
  private final KafkaProducer<String, byte[]> producer;
  private final Map<String, SagaCommandHandler> handlers;

  /**
   * Creates a new saga command consumer.
   *
   * @param consumer Kafka consumer for raw JSON messages
   * @param producer Kafka producer for publishing results
   * @param handlers map of adapter name → handler (e.g. "frost" → FrostSagaHandler)
   */
  public KafkaSagaCommandConsumer(
      KafkaConsumer<String, byte[]> consumer,
      KafkaProducer<String, byte[]> producer,
      Map<String, SagaCommandHandler> handlers) {
    this(
        consumer,
        producer,
        handlers,
        new BackoffCalculator(
            ConsumerRecordRetry.DEFAULT_INITIAL_BACKOFF_MS,
            ConsumerRecordRetry.DEFAULT_MAX_BACKOFF_MS));
  }

  KafkaSagaCommandConsumer(
      KafkaConsumer<String, byte[]> consumer,
      KafkaProducer<String, byte[]> producer,
      Map<String, SagaCommandHandler> handlers,
      BackoffCalculator backoffCalculator) {
    this.producer = producer;
    this.handlers = handlers;
    this.pollingConsumer = new KafkaPollingConsumer(consumer);
    this.loop =
        new RetryConsumerLoop<>(
            pollingConsumer,
            rec -> () -> processRecord(rec),
            backoffCalculator,
            LOG,
            "saga-command-consumer");
    pollingConsumer.setRevocationCallback(loop::resetState);
  }

  /** Start consuming saga commands in a virtual thread. */
  public void start() {
    List<String> topics = buildTopicList();
    pollingConsumer.subscribe(topics);
    if (loop.start()) {
      LOG.info(
          "KafkaSagaCommandConsumer started, subscribed to {} topics for adapters: {}",
          topics.size(),
          handlers.keySet());
    }
  }

  /** Stop consuming and release resources. */
  public void stop() {
    loop.stop();
  }

  @Override
  public void close() {
    stop();
    pollingConsumer.delegate().close();
    producer.close();
    LOG.info("KafkaSagaCommandConsumer closed");
  }

  private void processRecord(PollingConsumer.Record<String, byte[]> record) throws IOException {
    Map<String, Object> map = PayloadConverter.readMap(record.value());
    SagaCommandMessage command = SagaCommandMessage.fromMap(map);

    String adapter = command.adapter();
    if (adapter == null) {
      LOG.warn("Ignoring saga command without adapter field from topic {}", record.topic());
      return;
    }

    SagaCommandHandler handler = handlers.get(adapter);
    if (handler == null) {
      LOG.warn(
          "No handler registered for adapter '{}', ignoring command from topic {}",
          Encode.forJava(adapter),
          Encode.forJava(record.topic()));
      return;
    }

    LOG.debug(
        "Routing saga command to {}: sagaId={}, stepId={}, operation={}",
        Encode.forJava(adapter),
        Encode.forJava(command.sagaId()),
        Encode.forJava(command.stepId()),
        Encode.forJava(command.operation()));

    SagaCommandResult result = handler.handle(command);
    publishResult(adapter, result);
  }

  /**
   * Publishes the saga command result to the adapter's result topic. Serialization {@link
   * IOException} propagates as-is (permanent failure — no retry). Kafka send errors are wrapped as
   * {@link RuntimeException} (transient failure — retried).
   */
  private void publishResult(String adapter, SagaCommandResult result) throws IOException {
    String resultTopic = TOPIC_PREFIX + adapter + ".result";
    // Serialization IOException propagates → processOnce classifies as PERMANENT_FAILURE
    byte[] json = PayloadConverter.writeValueAsBytes(result);
    try {
      ProducerRecord<String, byte[]> record =
          new ProducerRecord<>(resultTopic, result.sagaId(), json);
      producer.send(record).get(PUBLISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);

      LOG.debug(
          "Published {} to {}: sagaId={}, stepId={}",
          Encode.forJava(result.type()),
          Encode.forJava(resultTopic),
          Encode.forJava(result.sagaId()),
          Encode.forJava(result.stepId()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted publishing to " + resultTopic, e);
    } catch (ExecutionException | TimeoutException e) {
      throw new RuntimeException("Failed to publish to " + resultTopic, e);
    }
  }

  private List<String> buildTopicList() {
    List<String> topics = new ArrayList<>();
    for (String adapter : handlers.keySet()) {
      topics.add(TOPIC_PREFIX + adapter + ".execute");
      topics.add(TOPIC_PREFIX + adapter + ".compensate");
    }
    return topics;
  }
}

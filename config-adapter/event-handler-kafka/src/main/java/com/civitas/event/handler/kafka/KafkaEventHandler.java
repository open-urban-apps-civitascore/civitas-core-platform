/**
 * This work and the accompanying materials are made available under the terms of the European Union
 * Public License (EU-PL) 1.2 which is available at
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to
 * all the individual contributors: Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 */
package com.civitas.event.handler.kafka;

import com.civitas.configadapter.Constants;
import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.ApplicationConfig;
import com.civitas.configadapter.exception.AdapterException;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.exception.RetryableAdapterException;
import com.civitas.configadapter.messaging.EventConsumer;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.AdapterErrorCode;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Kafka event handler with robust error handling including:
 *
 * <ul>
 *   <li>Blocking retries with exponential backoff for transient errors
 *   <li>Dead Letter Queue (DLQ) for non-recoverable errors
 *   <li>MDC-based correlation for structured logging
 *   <li>Safe external messages (no PII, no stack traces)
 * </ul>
 */
public class KafkaEventHandler implements EventConsumer, EventPublisher {

  public static final String HANDLER_NAME = "kafka";
  private static final Logger logger = LoggerFactory.getLogger(KafkaEventHandler.class);

  private static final String KAFKA_GROUP_ID = "kafka.group.id";
  private static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";

  // Retry configuration keys
  private static final String KAFKA_RETRY_MAX_ATTEMPTS = "kafka.retry.max.attempts";
  private static final String KAFKA_RETRY_INITIAL_BACKOFF_MS = "kafka.retry.initial.backoff.ms";
  private static final String KAFKA_DLQ_TOPIC = "kafka.dlq.topic";
  private static final String KAFKA_PUBLISH_TIMEOUT_MS = "kafka.publish.timeout.ms";
  private static final String KAFKA_MAX_POLL_INTERVAL_MS =
      "kafka." + ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG;

  // Default values
  private static final int DEFAULT_MAX_RETRIES = 3;
  private static final long DEFAULT_INITIAL_BACKOFF_MS = 1000L;
  private static final String DEFAULT_DLQ_TOPIC = "core.civitas.idm.dlq";
  private static final long DEFAULT_PUBLISH_TIMEOUT_MS = 5000L;

  // MDC keys
  private static final String MDC_CORRELATION_ID = "correlationId";
  private static final String MDC_EVENT_ID = "eventId";
  private static final String MDC_TOPIC = "topic";

  private KafkaConsumer<String, CloudEvent> kafkaConsumer;
  private KafkaProducer<String, CloudEvent> kafkaProducer;
  private ConfigAdapter adapter;
  private CloudEventProcessor processor;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;
  private boolean ready;

  // Retry configuration
  private int maxRetries;
  private long initialBackoffMs;
  private String dlqTopic;
  private long publishTimeoutMs;
  private BackoffCalculator backoffCalculator;

  public KafkaEventHandler() {}

  @Override
  public void initialize(ApplicationConfig config, ConfigAdapter adapter)
      throws FatalAdapterException {
    this.adapter = adapter;
    this.processor = new CloudEventProcessor(adapter);

    loadRetryConfiguration(config);

    this.backoffCalculator = new BackoffCalculator(this.initialBackoffMs, 30000L);

    validateRetryConfiguration(config);

    String bootstrapServers = config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092");
    String groupId = config.getProperty(KAFKA_GROUP_ID, "config-adapter-group");

    this.kafkaConsumer = new KafkaConsumer<>(createConsumerProperties(bootstrapServers, groupId));

    List<String> topicList = new ArrayList<>(adapter.getSubscribedTopics());
    subscribeToTopics(topicList);

    this.kafkaProducer = new KafkaProducer<>(createProducerProperties(bootstrapServers));

    adapter.setEventPublisher(this);

    logInitialization(topicList);
  }

  private void loadRetryConfiguration(ApplicationConfig config) {
    this.maxRetries =
        Integer.parseInt(
            config.getProperty(KAFKA_RETRY_MAX_ATTEMPTS, String.valueOf(DEFAULT_MAX_RETRIES)));
    this.initialBackoffMs =
        Long.parseLong(
            config.getProperty(
                KAFKA_RETRY_INITIAL_BACKOFF_MS, String.valueOf(DEFAULT_INITIAL_BACKOFF_MS)));
    this.dlqTopic = config.getProperty(KAFKA_DLQ_TOPIC, DEFAULT_DLQ_TOPIC);
    this.publishTimeoutMs =
        Long.parseLong(
            config.getProperty(
                KAFKA_PUBLISH_TIMEOUT_MS, String.valueOf(DEFAULT_PUBLISH_TIMEOUT_MS)));
  }

  private void validateRetryConfiguration(ApplicationConfig config) throws FatalAdapterException {
    String pollIntervalStr = config.getProperty(KAFKA_MAX_POLL_INTERVAL_MS, "300000");
    long maxPollIntervalMs = Long.parseLong(pollIntervalStr);

    long totalPotentialWaitTime = 0;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      totalPotentialWaitTime += backoffCalculator.calculate(attempt);
    }

    // use 80% as margin
    long safeLimit = (long) (maxPollIntervalMs * 0.8);

    if (totalPotentialWaitTime > safeLimit) {
      String msg =
          String.format(
              "Dangerous configuration detected! Total retry backoff (%d ms) exceeds 80%% of %s (%d ms). "
                  + "Please decrease '%s' or increase '%s'.",
              totalPotentialWaitTime,
              KAFKA_MAX_POLL_INTERVAL_MS,
              maxPollIntervalMs,
              KAFKA_RETRY_MAX_ATTEMPTS,
              KAFKA_MAX_POLL_INTERVAL_MS);

      throw new FatalAdapterException(AdapterErrorCode.CONFIGURATION_ERROR, msg);
    }
  }

  private Properties createConsumerProperties(String bootstrapServers, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return props;
  }

  private Properties createProducerProperties(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        org.apache.kafka.common.serialization.StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.RETRIES_CONFIG, 3);
    return props;
  }

  private void subscribeToTopics(List<String> topicList) {
    kafkaConsumer.subscribe(
        topicList,
        new org.apache.kafka.clients.consumer.ConsumerRebalanceListener() {
          @Override
          public void onPartitionsRevoked(
              java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
            ready = false;
            logger.info("Partitions revoked - Consumer not ready");
          }

          @Override
          public void onPartitionsAssigned(
              java.util.Collection<org.apache.kafka.common.TopicPartition> partitions) {
            ready = true;
            logger.info("Partitions assigned: {} - Consumer ready", partitions);
          }
        });
  }

  private void logInitialization(List<String> topicList) {
    logger.info(
        "Kafka event consumer initialized for adapter {} with {} topic(s): {}",
        adapter.getClass().getSimpleName(),
        topicList.size(),
        topicList);
    logger.info(
        "Retry configuration: maxRetries={}, initialBackoffMs={}, dlqTopic={}, publishTimeoutMs={}",
        maxRetries,
        initialBackoffMs,
        dlqTopic,
        publishTimeoutMs);
  }

  public boolean isReady() {
    return ready;
  }

  @Override
  public String getName() {
    return HANDLER_NAME;
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
    try {
      while (running.get()) {
        try {
          ConsumerRecords<String, CloudEvent> records = kafkaConsumer.poll(Duration.ofMillis(1000));

          if (records.isEmpty()) {
            continue;
          }

          for (var record : records) {
            setupMDC(record);
            try {
              processWithRetry(record);
              // Commit after each successful processing to maintain event ordering
              kafkaConsumer.commitSync();
            } finally {
              clearMDC();
            }
          }
        } catch (Exception e) {
          logger.error("Error in consumption loop", e);
          if (!running.get()) {
            break;
          }
        }
      }
    } catch (Exception e) {
      logger.error("Consumer loop died unexpectedly", e);
    } finally {
      ready = false;
      logger.info("Consumption loop ended");
    }
  }

  /**
   * Sets up MDC context for structured logging.
   *
   * @param record the Kafka record being processed
   */
  private void setupMDC(ConsumerRecord<String, CloudEvent> record) {
    CloudEvent event = record.value();
    if (event != null) {
      MDC.put(MDC_EVENT_ID, event.getId());
      MDC.put(MDC_TOPIC, record.topic());

      // Extract correlation ID from CloudEvent extension
      Object correlationId = event.getExtension("correlationid");
      if (correlationId != null) {
        MDC.put(MDC_CORRELATION_ID, correlationId.toString());
      } else {
        // Fallback to event ID if no correlation ID
        MDC.put(MDC_CORRELATION_ID, event.getId());
      }
    }
  }

  /** Clears MDC context after processing. */
  private void clearMDC() {
    MDC.remove(MDC_CORRELATION_ID);
    MDC.remove(MDC_EVENT_ID);
    MDC.remove(MDC_TOPIC);
  }

  /**
   * Processes a record with blocking retry for transient errors.
   *
   * @param record the Kafka record to process
   */
  private void processWithRetry(ConsumerRecord<String, CloudEvent> record) {
    int attempts = 0;

    // maxRetries + 1, because first attempt + maxRetries
    for (int attempt = 0; attempt <= maxRetries + 1; attempt++) {
      try {
        processor.handleEvent(record.topic(), record.value());
        logger.debug(
            "Successfully processed event {} on attempt {}", record.value().getId(), attempts + 1);
        return; // Success - exit retry loop
      } catch (RetryableAdapterException e) {
        attempts++;
        if (attempts > maxRetries) {
          logger.error(
              "Max retries ({}) exceeded for event {}. Sending to DLQ. Error: {}",
              maxRetries,
              record.value().getId(),
              e.getInternalMessage());
          sendToDLQ(record, e, true);
          return;
        }

        long backoff = backoffCalculator.calculate(attempt);
        logger.warn(
            "Retryable error processing event {} (attempt {}/{}). Retrying in {}ms. Error: {}",
            record.value().getId(),
            attempts,
            maxRetries,
            backoff,
            e.getInternalMessage());

        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          logger.warn("Retry sleep interrupted for event {}", record.value().getId());
          sendToDLQ(record, e, true);
          return;
        }
      } catch (FatalAdapterException e) {
        // Failure result already published by AbstractConfigAdapter template method
        logger.error(
            "Fatal error processing event {}. Sending to DLQ immediately. Error: {}",
            record.value().getId(),
            e.getInternalMessage());
        sendToDLQ(record, e, false);
        return;
      } catch (Exception e) {
        // Wrap unknown exceptions as fatal and send to DLQ
        // These originate from CloudEventProcessor (e.g., deserialization), not the adapter
        logger.error(
            "Unexpected error processing event {}. Wrapping as fatal and sending to DLQ.",
            record.value().getId(),
            e);
        FatalAdapterException wrapped =
            new FatalAdapterException(AdapterErrorCode.UNKNOWN_ERROR, e, e.getMessage());
        sendToDLQ(record, wrapped, true);
        return;
      }
    }
  }

  /**
   * Sends a failed event to the Dead Letter Queue (DLQ). Optionally delegates failure result
   * publishing to the adapter. This method is synchronous to ensure data safety - if DLQ send
   * fails, an exception is thrown so the event will be reprocessed on the next poll.
   *
   * @param record the original Kafka record
   * @param exception the adapter exception that caused the failure
   * @param publishFailure if true, delegates failure result publishing to the adapter (for cases
   *     where the adapter's template method has not yet published a failure result)
   * @throws RuntimeException if DLQ send fails, causing the event to be reprocessed
   */
  private void sendToDLQ(
      ConsumerRecord<String, CloudEvent> record,
      AdapterException exception,
      boolean publishFailure) {
    CloudEvent originalEvent = record.value();

    try {
      // Build DLQ event with safe metadata (no stack traces, no PII)
      CloudEvent dlqEvent =
          CloudEventBuilder.from(originalEvent)
              .withId(UUID.randomUUID().toString())
              .withExtension("dlqerrorcode", String.valueOf(exception.getNumericCode()))
              .withExtension("dlqerrormsg", exception.getSafeExternalMessage())
              .withExtension("dlqoriginaltopic", record.topic())
              .withExtension("dlqtimestamp", OffsetDateTime.now().toString())
              .withExtension("dlqretrycount", String.valueOf(maxRetries))
              .build();

      // Synchronous send - critical for data safety
      kafkaProducer
          .send(new ProducerRecord<>(dlqTopic, dlqEvent))
          .get(publishTimeoutMs, TimeUnit.MILLISECONDS);

      logger.info("Sent event {} to DLQ topic {}", originalEvent.getId(), dlqTopic);

      if (publishFailure) {
        delegateFailureResultToAdapter(originalEvent, exception);
      }

    } catch (Exception e) {
      logger.error(
          "CRITICAL: Failed to send event {} to DLQ. Event will be reprocessed. Error: {}",
          originalEvent.getId(),
          e.getMessage(),
          e);
      // Throw exception so event is not committed and will be reprocessed
      throw new RuntimeException("DLQ send failed - event will be reprocessed", e);
    }
  }

  /**
   * Delegates failure result publishing to the adapter by deserializing the original CloudEvent
   * data into a ConfigEvent and calling the adapter's publishFailureResult method.
   *
   * @param originalEvent the original CloudEvent that failed
   * @param exception the adapter exception that caused the failure
   */
  private void delegateFailureResultToAdapter(
      CloudEvent originalEvent, AdapterException exception) {
    try {
      if (originalEvent.getData() == null) {
        logger.debug("Cannot publish failure result: original event has no data");
        return;
      }

      ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
      ConfigEvent configEvent =
          objectMapper.readValue(originalEvent.getData().toBytes(), ConfigEvent.class);

      adapter.publishFailureResult(configEvent, exception);

    } catch (Exception e) {
      logger.warn(
          "Failed to publish failure result for event {}: {}",
          originalEvent.getId(),
          e.getMessage());
    }
  }

  public void stop() {
    logger.info("Stopping Kafka event consumer");
    ready = false;
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
  public void publish(String topic, ConfigResultEvent resultEvent)
      throws RetryableAdapterException, FatalAdapterException {
    CloudEvent cloudEvent = convertToCloudEvent(resultEvent);
    ProducerRecord<String, CloudEvent> record =
        new ProducerRecord<>(topic, cloudEvent.getId(), cloudEvent);

    try {
      // Synchronous send with timeout
      kafkaProducer.send(record).get(publishTimeoutMs, TimeUnit.MILLISECONDS);
      logger.debug("Published result event {} to topic {}", cloudEvent.getId(), topic);

    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      logger.error(
          "Failed to publish event {} to {}: {}", cloudEvent.getId(), topic, cause.getMessage());

      if (isRetryableKafkaError(cause)) {
        throw new RetryableAdapterException(
            AdapterErrorCode.PUBLISH_ERROR, cause, topic, cause.getMessage());
      } else {
        throw new FatalAdapterException(
            AdapterErrorCode.PUBLISH_ERROR,
            cause,
            String.format("Failed to publish to %s: %s", topic, cause.getMessage()));
      }
    } catch (TimeoutException e) {
      logger.error("Publish timeout for event {} to {}", cloudEvent.getId(), topic);
      throw new RetryableAdapterException(
          AdapterErrorCode.PUBLISH_TIMEOUT, e, topic, publishTimeoutMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RetryableAdapterException(AdapterErrorCode.PUBLISH_ERROR, e, topic, "interrupted");
    }
  }

  /**
   * Determines if a Kafka error is retryable (transient) or permanent.
   *
   * @param cause the exception cause from Kafka
   * @return true if the error is retryable, false otherwise
   */
  private boolean isRetryableKafkaError(Throwable cause) {
    return cause instanceof org.apache.kafka.common.errors.RetriableException;
  }

  /**
   * Converts a ConfigResultEvent to a CloudEvent for transmission over Kafka.
   *
   * @param resultEvent the configuration result event
   * @return a CloudEvent representation
   */
  private CloudEvent convertToCloudEvent(ConfigResultEvent resultEvent) {
    try {
      ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
      byte[] jsonData = objectMapper.writeValueAsBytes(resultEvent);

      CloudEventBuilder builder =
          CloudEventBuilder.v1()
              .withId(UUID.randomUUID().toString())
              .withSource(URI.create(resultEvent.source()))
              .withType(resultEvent.resultType())
              .withTime(resultEvent.timestamp())
              .withDataContentType(Constants.CONTENT_TYPE_JSON)
              .withData(jsonData)
              .withExtension("correlationid", resultEvent.correlationId())
              .withExtension("originalmessageid", resultEvent.originalMessageId())
              .withExtension("status", resultEvent.status().name())
              .withExtension("operation", resultEvent.operation().name())
              .withExtension("targetresource", resultEvent.targetResource());

      if (resultEvent.message() != null) {
        builder.withExtension("message", resultEvent.message());
      }

      if (resultEvent.status() == ConfigResultEvent.Status.SUCCESS
          && resultEvent.resourceId() != null) {
        builder.withExtension("resourceid", resultEvent.resourceId());
      }

      if (resultEvent.status() == ConfigResultEvent.Status.FAILURE) {
        if (resultEvent.errorCode() != null) {
          builder.withExtension("errorcode", resultEvent.errorCode());
        }
        if (resultEvent.message() != null) {
          builder.withExtension("errormessage", resultEvent.message());
        }
      }

      return builder.build();

    } catch (Exception e) {
      logger.error("Failed to serialize ConfigResultEvent to JSON", e);

      return CloudEventBuilder.v1()
          .withId(UUID.randomUUID().toString())
          .withSource(URI.create(resultEvent.source()))
          .withType(resultEvent.resultType())
          .withDataContentType(Constants.CONTENT_TYPE_JSON)
          .withData("{}".getBytes())
          .build();
    }
  }

  @Override
  public void close() {
    stop();
    flushAndCloseProducer();
    closeQuietly(kafkaConsumer, "Kafka consumer");
    closeQuietly(adapter, "Adapter");
  }

  private void flushAndCloseProducer() {
    try {
      kafkaProducer.flush();
      kafkaProducer.close();
      logger.info("Kafka producer closed");
    } catch (Exception e) {
      logger.error("Error closing Kafka producer", e);
    }
  }

  private void closeQuietly(AutoCloseable resource, String resourceName) {
    if (resource == null) {
      return;
    }
    try {
      resource.close();
      logger.info("{} closed", resourceName);
    } catch (Exception e) {
      logger.error("Error closing {}", resourceName, e);
    }
  }

  // Getter methods for testing
  int getMaxRetries() {
    return maxRetries;
  }

  long getInitialBackoffMs() {
    return initialBackoffMs;
  }

  String getDlqTopic() {
    return dlqTopic;
  }

  long getPublishTimeoutMs() {
    return publishTimeoutMs;
  }
}

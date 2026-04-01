/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.event.handler.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Constants;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.ApplicationConfig;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventConsumer;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.ConfigResultEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
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
import org.owasp.encoder.Encode;
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

  // DLQ configuration keys
  private static final String KAFKA_DLQ_TOPIC = "kafka.dlq.topic";
  private static final String KAFKA_PUBLISH_TIMEOUT_MS = "kafka.publish.timeout.ms";

  // Default values
  private static final String DEFAULT_DLQ_TOPIC = "de.civitascore.idm.dlq";
  private static final long DEFAULT_PUBLISH_TIMEOUT_MS = 5000L;

  // MDC keys
  private static final String MDC_CORRELATION_ID = "correlationId";
  private static final String MDC_EVENT_ID = "eventId";
  private static final String MDC_TOPIC = "topic";

  private KafkaConsumer<String, CloudEvent> kafkaConsumer;
  private KafkaProducer<String, CloudEvent> kafkaProducer;
  private ConfigAdapter adapter;
  private RetryHandler retryHandler;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private Thread consumerThread;
  private boolean ready;

  private long publishTimeoutMs;

  public KafkaEventHandler() {}

  @Override
  public void initialize(ApplicationConfig config, ConfigAdapter adapter)
      throws FatalAdapterException {
    this.adapter = adapter;

    String dlqTopic = config.getProperty(KAFKA_DLQ_TOPIC, DEFAULT_DLQ_TOPIC);
    this.publishTimeoutMs =
        Long.parseLong(
            config.getProperty(
                KAFKA_PUBLISH_TIMEOUT_MS, String.valueOf(DEFAULT_PUBLISH_TIMEOUT_MS)));

    RetryHandler.RetryConfig retryConfig = RetryHandler.loadAndValidate(config);

    String bootstrapServers = config.getProperty(KAFKA_BOOTSTRAP_SERVERS, "localhost:9092");
    String groupId = config.getProperty(KAFKA_GROUP_ID, "config-adapter-group");

    this.kafkaConsumer = new KafkaConsumer<>(createConsumerProperties(bootstrapServers, groupId));

    List<String> topicList = new ArrayList<>(adapter.getSubscribedTopics());
    subscribeToTopics(topicList);

    this.kafkaProducer = new KafkaProducer<>(createProducerProperties(bootstrapServers));

    CloudEventProcessor processor = new CloudEventProcessor(adapter);
    DlqHandler dlqHandler =
        new DlqHandler(
            dlqTopic, publishTimeoutMs, retryConfig.maxRetries(), kafkaProducer, adapter);
    this.retryHandler =
        new RetryHandler(retryConfig.maxRetries(), retryConfig.calculator(), dlqHandler, processor);

    adapter.setEventPublisher(this);

    logInitialization(topicList, retryConfig, dlqTopic);
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
    props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
    props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
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

  private void logInitialization(
      List<String> topicList, RetryHandler.RetryConfig retryConfig, String dlqTopic) {
    logger.info(
        "Kafka event consumer initialized for adapter {} with {} topic(s): {}",
        adapter.getClass().getSimpleName(),
        topicList.size(),
        Encode.forJava(String.valueOf(topicList)));
    logger.info(
        "Retry configuration: maxRetries={}, initialBackoffMs={}, dlqTopic={}, publishTimeoutMs={}",
        retryConfig.maxRetries(),
        retryConfig.initialBackoffMs(),
        Encode.forJava(dlqTopic),
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
              retryHandler.processWithRetry(record);
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
      logger.debug(
          "Published result event {} to topic {}",
          Encode.forJava(cloudEvent.getId()),
          Encode.forJava(topic));

    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      logger.error(
          "Failed to publish event {} to {}: {}",
          Encode.forJava(cloudEvent.getId()),
          Encode.forJava(topic),
          Encode.forJava(String.valueOf(cause.getMessage())));

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
      logger.error(
          "Publish timeout for event {} to {}",
          Encode.forJava(cloudEvent.getId()),
          Encode.forJava(topic));
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
    return retryHandler.getMaxRetries();
  }

  long getPublishTimeoutMs() {
    return publishTimeoutMs;
  }
}

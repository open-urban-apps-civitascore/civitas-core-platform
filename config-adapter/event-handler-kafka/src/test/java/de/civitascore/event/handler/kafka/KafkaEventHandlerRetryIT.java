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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Constants;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.UserConfig;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for Kafka event handler retry and DLQ functionality. Tests:
 *
 * <ul>
 *   <li>Retry on transient failures with eventual success
 *   <li>Immediate DLQ on fatal errors
 *   <li>DLQ after max retries exceeded
 *   <li>DLQ message format (no stack traces, safe messages)
 * </ul>
 */
class KafkaEventHandlerRetryIT extends AbstractKafkaIT {

  private KafkaEventHandler handler;
  private RetryTestAdapter testAdapter;
  private KafkaProducer<String, CloudEvent> testProducer;
  private KafkaConsumer<String, CloudEvent> dlqConsumer;
  private AppConfig config;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = ObjectMapperFactory.createObjectMapper();

    // Create unique DLQ topic per test to avoid interference between tests
    // Unique per test to avoid interference
    String dlqTopic = "test.dlq." + UUID.randomUUID();

    // Create test configuration with retry settings
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", KAFKA.getBootstrapServers());
    props.put("kafka.group.id", "test-group-" + UUID.randomUUID());
    props.put("kafka.auto.offset.reset", "earliest");
    props.put("kafka.retry.max.attempts", "3");
    props.put("kafka.retry.initial.backoff.ms", "100"); // Fast for testing
    props.put("kafka.dlq.topic", dlqTopic);
    config = new AppConfig(new MapConfiguration(props));

    // Create test adapter
    testAdapter = new RetryTestAdapter();

    // Create test producer
    Properties producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProps.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    testProducer = new KafkaProducer<>(producerProps);

    // Create DLQ consumer
    Properties dlqConsumerProps = new Properties();
    dlqConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    dlqConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-group-" + UUID.randomUUID());
    dlqConsumerProps.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    dlqConsumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    dlqConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    dlqConsumer = new KafkaConsumer<>(dlqConsumerProps);
    dlqConsumer.subscribe(List.of(dlqTopic));
  }

  @AfterEach
  void tearDown() {
    if (handler != null) {
      handler.close();
    }
    if (testProducer != null) {
      testProducer.close();
    }
    if (dlqConsumer != null) {
      dlqConsumer.close();
    }
  }

  @Test
  void shouldRetryOnTransientFailure_thenSucceed() throws Exception {
    // Given - adapter fails twice, then succeeds
    String topic = "retry.test.success";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setFailuresBeforeSuccess(2); // Fail first 2 attempts

    CountDownLatch successLatch = new CountDownLatch(1);
    testAdapter.setSuccessCallback(successLatch::countDown);

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    // When
    ConfigEvent configEvent = createTestConfigEvent("retry-user-1");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // Then
    assertTrue(successLatch.await(30, TimeUnit.SECONDS), "Event should eventually succeed");
    assertEquals(
        3, testAdapter.getAttemptCount(), "Should have tried 3 times (2 failures + 1 success)");
    assertEquals(1, testAdapter.getProcessedEvents().size(), "Event should be processed once");
  }

  @Test
  void shouldSendToDLQ_onFatalError() throws Exception {
    // Given - adapter throws fatal error
    String topic = "dlq.test.fatal";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setThrowFatalError(true);

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    // When
    ConfigEvent configEvent = createTestConfigEvent("fatal-user");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // Then - should be sent to DLQ immediately (no retries)
    CloudEvent dlqEvent = pollDLQ(10, TimeUnit.SECONDS);
    assertNotNull(dlqEvent, "Event should be in DLQ");
    assertEquals(1, testAdapter.getAttemptCount(), "Should only try once for fatal errors");

    // Verify DLQ metadata
    assertNotNull(dlqEvent.getExtension("dlqerrorcode"));
    assertNotNull(dlqEvent.getExtension("dlqerrormsg"));
    assertEquals(topic, dlqEvent.getExtension("dlqoriginaltopic"));
  }

  @Test
  void shouldSendToDLQ_afterMaxRetries() throws Exception {
    // Given - adapter always fails with retryable error
    String topic = "dlq.test.max.retries";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setAlwaysFailRetryable(true);

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    // When
    ConfigEvent configEvent = createTestConfigEvent("max-retry-user");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // Then - should be sent to DLQ after max retries (3 + 1 initial = 4 attempts)
    CloudEvent dlqEvent = pollDLQ(30, TimeUnit.SECONDS);
    assertNotNull(dlqEvent, "Event should be in DLQ after max retries");
    assertEquals(
        4,
        testAdapter.getAttemptCount(),
        "Should try maxRetries + 1 times (1 initial + 3 retries)");
  }

  @Test
  void dlqEvent_shouldNotContainStackTrace() throws Exception {
    // Given - adapter throws error with stack trace
    String topic = "dlq.test.security";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setThrowFatalError(true);
    testAdapter.setErrorMessage(
        "Detailed error with user@email.com and internal path /var/log/secret.txt");

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    // When
    ConfigEvent configEvent = createTestConfigEvent("security-user");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // Then - DLQ message should be safe
    CloudEvent dlqEvent = pollDLQ(10, TimeUnit.SECONDS);
    assertNotNull(dlqEvent, "Event should be in DLQ");

    String errorMsg = (String) dlqEvent.getExtension("dlqerrormsg");
    assertNotNull(errorMsg);

    // Should NOT contain PII or sensitive data
    assertFalse(errorMsg.contains("user@email.com"), "DLQ message should not contain PII");
    assertFalse(errorMsg.contains("/var/log"), "DLQ message should not contain paths");
    assertFalse(
        errorMsg.contains("Exception"), "DLQ message should not contain exception class names");
    assertFalse(errorMsg.contains(".java"), "DLQ message should not contain stack trace elements");
    assertFalse(errorMsg.contains("at "), "DLQ message should not contain stack trace");

    // Should contain safe message
    assertEquals("Validation failed", errorMsg, "DLQ message should be safe external message");
  }

  @Test
  void shouldPreserveEventOrdering_duringRetries() throws Exception {
    // Given - multiple events where first needs retry
    String topic = "order.test";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setFailuresBeforeSuccess(1); // First event fails once

    List<String> processOrder = Collections.synchronizedList(new ArrayList<>());
    testAdapter.setOrderTracker(processOrder);

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    // When - send two events
    ConfigEvent event1 = createTestConfigEvent("order-user-1");
    ConfigEvent event2 = createTestConfigEvent("order-user-2");

    testProducer.send(new ProducerRecord<>(topic, "key1", createCloudEvent(event1))).get();
    testProducer.send(new ProducerRecord<>(topic, "key2", createCloudEvent(event2))).get();
    testProducer.flush();

    // Then - events should be processed in order (blocking retry preserves order)
    await().atMost(30, TimeUnit.SECONDS).until(() -> processOrder.size() >= 2);

    assertEquals("order-user-1", processOrder.get(0), "First event should be processed first");
    assertEquals("order-user-2", processOrder.get(1), "Second event should be processed second");
  }

  // Helper methods

  private CloudEvent pollDLQ(long timeout, TimeUnit unit) {
    long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
    while (System.currentTimeMillis() < deadline) {
      ConsumerRecords<String, CloudEvent> records = dlqConsumer.poll(Duration.ofMillis(500));
      if (!records.isEmpty()) {
        return records.iterator().next().value();
      }
    }
    return null;
  }

  private ConfigEvent createTestConfigEvent(String userId) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now(),
            "test.source",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");

    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("testuser");
    userConfig.setEmail("test@example.com");
    userConfig.setEnabled(true);

    Config eventConfig = new Config("users/" + userId, userConfig);

    Payload payload = new Payload("user", "users/" + userId, Operation.CREATE, eventConfig);

    return new ConfigEvent(metadata, payload);
  }

  private CloudEvent createCloudEvent(ConfigEvent configEvent) throws Exception {
    String jsonData = objectMapper.writeValueAsString(configEvent);

    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(URI.create("test.producer"))
        .withType("user.created")
        .withDataContentType(Constants.CONTENT_TYPE_JSON)
        .withData(jsonData.getBytes())
        .withExtension("correlationid", configEvent.metadata().correlationId())
        .build();
  }

  /** Test adapter that can simulate various failure scenarios. */
  static class RetryTestAdapter implements ConfigAdapter {
    private List<String> subscribedTopics = null;
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    @Override
    public void initialize(AdapterConfig config) {}

    private int failuresBeforeSuccess = 0;
    private boolean throwFatalError = false;
    private boolean alwaysFailRetryable = false;
    private String errorMessage = "Test error";
    private Runnable successCallback;
    private List<String> orderTracker;

    private final List<ConfigEvent> processedEvents =
        Collections.synchronizedList(new ArrayList<>());

    public void setFailuresBeforeSuccess(int failures) {
      this.failuresBeforeSuccess = failures;
    }

    public void setThrowFatalError(boolean fatal) {
      this.throwFatalError = fatal;
    }

    public void setAlwaysFailRetryable(boolean always) {
      this.alwaysFailRetryable = always;
    }

    public void setErrorMessage(String msg) {
      this.errorMessage = msg;
    }

    public void setSuccessCallback(Runnable callback) {
      this.successCallback = callback;
    }

    public void setOrderTracker(List<String> tracker) {
      this.orderTracker = tracker;
    }

    public int getAttemptCount() {
      return attemptCount.get();
    }

    @Override
    public String getName() {
      return "retry-test";
    }

    @Override
    public List<String> getSubscribedTopics() {
      return subscribedTopics;
    }

    public void setSubscribedTopics(List<String> topics) {
      this.subscribedTopics = topics;
    }

    @Override
    public void processConfigEvent(String topic, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException {
      int currentAttempt = attemptCount.incrementAndGet();

      if (throwFatalError) {
        throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, errorMessage);
      }

      if (alwaysFailRetryable) {
        throw new RetryableAdapterException(
            AdapterErrorCode.SERVICE_UNAVAILABLE, "test-service", 503);
      }

      if (currentAttempt <= failuresBeforeSuccess) {
        throw new RetryableAdapterException(AdapterErrorCode.CONNECTION_TIMEOUT, "test-service");
      }

      // Success
      processedEvents.add(event);

      if (orderTracker != null) {
        // Extract user ID from target resource
        String targetResource = event.payload().targetResource();
        String userId = targetResource.replace("users/", "");
        orderTracker.add(userId);
      }

      if (successCallback != null) {
        successCallback.run();
      }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
      // Not needed for this test
    }

    @Override
    public void publishFailureResult(ConfigEvent event, AdapterException exception) {
      // Not needed for this test
    }

    public List<ConfigEvent> getProcessedEvents() {
      return new ArrayList<>(processedEvents);
    }

    @Override
    public void close() {
      // No cleanup needed
    }
  }
}

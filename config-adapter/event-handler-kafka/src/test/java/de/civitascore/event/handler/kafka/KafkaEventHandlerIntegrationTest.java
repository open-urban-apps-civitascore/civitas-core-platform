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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.Constants;
import de.civitascore.configadapter.adapter.ConfigAdapter;
import de.civitascore.configadapter.configuration.AppConfig;
import de.civitascore.configadapter.exception.AdapterException;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.exception.RetryableAdapterException;
import de.civitascore.configadapter.messaging.EventPublisher;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.Config;
import de.civitascore.configadapter.model.ConfigEvent;
import de.civitascore.configadapter.model.ConfigResultEvent;
import de.civitascore.configadapter.model.Metadata;
import de.civitascore.configadapter.model.Operation;
import de.civitascore.configadapter.model.Payload;
import de.civitascore.configadapter.model.idm.UserConfig;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for KafkaEventHandler using Testcontainers. Tests the full flow: Kafka ->
 * Handler -> Adapter -> Publisher -> Kafka
 */
@Testcontainers
class KafkaEventHandlerIntegrationTest {

  @SuppressWarnings("resource")
  @Container
  static ConfluentKafkaContainer kafka =
      new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
          .withReuse(false);

  private KafkaEventHandler handler;
  private TestAdapter testAdapter;
  private KafkaProducer<String, CloudEvent> testProducer;
  private AppConfig config;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = ObjectMapperFactory.createObjectMapper();

    // Create test configuration
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", kafka.getBootstrapServers());
    props.put("kafka.group.id", "test-group-" + UUID.randomUUID());
    props.put("kafka.auto.offset.reset", "earliest");
    config = new AppConfig(new MapConfiguration(props));

    // Create test adapter
    testAdapter = new TestAdapter();

    // Create test producer
    Properties producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProps.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    // Don't specify encoding - use default binary mode
    testProducer = new KafkaProducer<>(producerProps);
  }

  @AfterEach
  void tearDown() {
    if (handler != null) {
      handler.close();
    }
    if (testProducer != null) {
      testProducer.close();
    }
  }

  @AfterAll
  static void afterAll() {
    kafka.close();
  }

  @Test
  void shouldConsumeAndProcessCloudEvent() throws Exception {
    // Given
    String topic = "user.created-cloudevent";
    testAdapter.setSubscribedTopics(List.of(topic));
    CountDownLatch latch = new CountDownLatch(1);
    testAdapter.setProcessCallback((t, event) -> latch.countDown());

    // Create handler with test adapter
    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    // Create and send test event
    ConfigEvent configEvent = createTestConfigEvent("test-user-123");
    CloudEvent cloudEvent = createCloudEvent(configEvent);

    // When
    testProducer.send(new ProducerRecord<>(topic, "test-key", cloudEvent)).get();
    testProducer.flush();

    // Then
    assertTrue(latch.await(10, TimeUnit.SECONDS), "Event should be processed within 10 seconds");
    assertEquals(1, testAdapter.getProcessedEvents().size());

    ConfigEvent receivedEvent = testAdapter.getProcessedEvents().getFirst();
    assertEquals(Operation.CREATE, receivedEvent.payload().operation());
    assertEquals("user", receivedEvent.payload().targetComponent());
    assertEquals("users/test-user-123", receivedEvent.payload().targetResource());
  }

  @Test
  void shouldPublishResultEvents() throws Exception {
    // Given
    String topic = "user.created-publish";
    testAdapter.setSubscribedTopics(List.of(topic));
    CountDownLatch publishLatch = new CountDownLatch(1);
    List<ConfigResultEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    testAdapter.setProcessCallback(
        (t, event) -> {
          // Adapter publishes result event
          if (testAdapter.eventPublisher != null) {
            ConfigResultEvent resultEvent =
                ConfigResultEvent.success(
                    event.metadata().correlationId(),
                    event.metadata().messageId(),
                    "Test success",
                    null,
                    event.payload().operation(),
                    event.payload().targetResource(),
                    "test.adapter",
                    "de.civitascore.test.processing.result");
            testAdapter.eventPublisher.publish(event.metadata().resultTopic(), resultEvent);
            publishedEvents.add(resultEvent);
            publishLatch.countDown();
          }
        });

    // Create handler
    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    // Create test event with result topic
    ConfigEvent configEvent = createTestConfigEvent("test-user-456");
    CloudEvent cloudEvent = createCloudEvent(configEvent);

    // When
    testProducer.send(new ProducerRecord<>(topic, "test-key", cloudEvent)).get();
    testProducer.flush();

    // Then
    assertTrue(
        publishLatch.await(10, TimeUnit.SECONDS), "Result should be published within 10 seconds");
    assertEquals(1, publishedEvents.size());
    assertEquals(ConfigResultEvent.Status.SUCCESS, publishedEvents.getFirst().status());
  }

  @Test
  void shouldHandleMultipleTopics() throws Exception {
    // Given
    testAdapter.setSubscribedTopics(List.of("user.created", "user.updated", "user.deleted"));
    CountDownLatch latch = new CountDownLatch(3);

    testAdapter.setProcessCallback((topic, event) -> latch.countDown());

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    // When - send events to different topics
    for (String topic : List.of("user.created", "user.updated", "user.deleted")) {
      ConfigEvent configEvent = createTestConfigEvent("user-" + topic);
      CloudEvent cloudEvent = createCloudEvent(configEvent);
      testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    }
    testProducer.flush();

    // Then
    assertTrue(latch.await(15, TimeUnit.SECONDS), "All 3 events should be processed");
    assertEquals(3, testAdapter.getProcessedEvents().size());
  }

  @Test
  void shouldHandleInvalidCloudEvent() throws Exception {
    // Given
    String topic = "user.invalid";
    testAdapter.setSubscribedTopics(List.of(topic));
    CountDownLatch latch = new CountDownLatch(1);
    testAdapter.setProcessCallback((t, event) -> latch.countDown());
    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();

    // Create CloudEvent with no data
    CloudEvent cloudEvent =
        CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("test"))
            .withType("user.created")
            .build();

    // When
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // Then - event should not be processed (no data)
    assertFalse(latch.await(3, TimeUnit.SECONDS), "Invalid event should not be processed");
    assertEquals(0, testAdapter.getProcessedEvents().size());
  }

  @Test
  void shouldRetryAndSucceed_WhenProcessingFailsOnce() throws Exception {
    // Test renamed and updated to work with new retry logic
    // With retry logic, the event is retried (not requiring consumer restart)
    String topic = "user.resilience-test";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setShouldFailOnce(true);

    CountDownLatch successLatch = new CountDownLatch(1);
    testAdapter.setProcessCallback((t, e) -> successLatch.countDown());

    handler = new KafkaEventHandler();
    handler.initialize(config, testAdapter);
    handler.start();
    await()
        .atMost(30, TimeUnit.SECONDS)
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(() -> handler.isReady());

    ConfigEvent configEvent = createTestConfigEvent("resilience-user");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    // With retry logic, the event should be automatically retried and succeed
    boolean success = successLatch.await(30, TimeUnit.SECONDS);
    assertTrue(success, "Event should be processed successfully after retry");

    // Should have 2 attempts: 1 failure + 1 success
    assertEquals(
        2, testAdapter.getAttemptCount(), "Should be tried twice (1 failure + 1 success).");
    assertEquals(1, testAdapter.getProcessedEvents().size(), "Should be processed.");
  }

  // Helper methods

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

    Config config = new Config("users/" + userId, userConfig);

    Payload payload = new Payload("user", "users/" + userId, Operation.CREATE, config);

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
        .build();
  }

  /** Test adapter implementation */
  static class TestAdapter implements ConfigAdapter {
    private List<String> subscribedTopics = null;
    private final AtomicInteger attemptCount = new AtomicInteger(0);
    private boolean shouldFailOnce = false;

    public void setShouldFailOnce(boolean shouldFail) {
      this.shouldFailOnce = shouldFail;
      this.attemptCount.set(0);
    }

    public int getAttemptCount() {
      return attemptCount.get();
    }

    private final List<ConfigEvent> processedEvents =
        Collections.synchronizedList(new ArrayList<>());
    private ProcessCallback processCallback;
    EventPublisher eventPublisher;

    @Override
    public String getName() {
      return "test";
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
        throws RetryableAdapterException, FatalAdapterException {
      int currentAttempt = attemptCount.incrementAndGet();

      if (shouldFailOnce && currentAttempt == 1) {
        // Throw RetryableAdapterException to trigger retry logic
        throw new RetryableAdapterException(AdapterErrorCode.CONNECTION_TIMEOUT, "test-service");
      }

      processedEvents.add(event);
      if (processCallback != null) {
        processCallback.onProcess(topic, event);
      }
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
      this.eventPublisher = publisher;
    }

    public List<ConfigEvent> getProcessedEvents() {
      return new ArrayList<>(processedEvents);
    }

    public void setProcessCallback(ProcessCallback callback) {
      processedEvents.clear();
      this.processCallback = callback;
    }

    @Override
    public void publishFailureResult(ConfigEvent event, AdapterException exception) {
      // Not needed for this test
    }

    @Override
    public void close() {
      // No cleanup needed
    }
  }

  @FunctionalInterface
  interface ProcessCallback {
    void onProcess(String topic, ConfigEvent event)
        throws FatalAdapterException, RetryableAdapterException;
  }
}

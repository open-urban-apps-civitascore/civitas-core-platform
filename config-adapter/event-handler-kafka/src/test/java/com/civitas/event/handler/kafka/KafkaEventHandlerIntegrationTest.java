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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for KafkaEventHandler using Testcontainers. Tests the full flow: Kafka ->
 * Handler -> Adapter -> Publisher -> Kafka
 */
@Testcontainers
class KafkaEventHandlerIntegrationTest {

  @Container
  static KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3")).withReuse(false);

  private KafkaEventHandler handler;
  private TestAdapter testAdapter;
  private KafkaProducer<String, CloudEvent> testProducer;
  private AppConfig config;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();

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
    handler = new KafkaEventHandler(config, testAdapter);
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
    assertEquals("CREATE", receivedEvent.payload().operation());
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
                    "test.adapter");
            testAdapter.eventPublisher.publish(event.metadata().resultTopic(), resultEvent);
            publishedEvents.add(resultEvent);
            publishLatch.countDown();
          }
        });

    // Create handler
    handler = new KafkaEventHandler(config, testAdapter);
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

    handler = new KafkaEventHandler(config, testAdapter);
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
    handler = new KafkaEventHandler(config, testAdapter);
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
  void shouldNotLoseMessage_WhenProcessingFails() throws Exception {
    String topic = "user.resilience-test";
    testAdapter.setSubscribedTopics(List.of(topic));
    testAdapter.setShouldFailOnce(true);

    CountDownLatch successLatch = new CountDownLatch(1);
    testAdapter.setProcessCallback((t, e) -> successLatch.countDown());

    handler = new KafkaEventHandler(config, testAdapter);
    handler.start();
    await().atMost(30, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> handler.isReady());

    ConfigEvent configEvent = createTestConfigEvent("resilience-user");
    CloudEvent cloudEvent = createCloudEvent(configEvent);
    testProducer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    testProducer.flush();

    await().atMost(5, TimeUnit.SECONDS).until(() -> testAdapter.getAttemptCount() >= 1);

    assertEquals(1, testAdapter.getAttemptCount(), "Should be tried once.");
    assertEquals(0, testAdapter.getProcessedEvents().size(), "Should not be processed.");

    handler.close();
    handler = new KafkaEventHandler(config, testAdapter);
    handler.start();

    await().atMost(30, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> handler.isReady());

    boolean success = successLatch.await(10, TimeUnit.SECONDS);
    assertTrue(success, "Event should be processed successfully");

    assertEquals(2, testAdapter.getAttemptCount(), "Should be tried two times.");
    assertEquals(1, testAdapter.getProcessedEvents().size(), "Should be processed now.");
  }

  // Helper methods

  private ConfigEvent createTestConfigEvent(String userId) {
    Metadata metadata =
        new Metadata(
            UUID.randomUUID().toString(),
            OffsetDateTime.now().toString(),
            "test.source",
            UUID.randomUUID().toString(),
            "1.0",
            "result.topic");

    Map<String, Object> userData = new HashMap<>();
    userData.put("username", "testuser");
    userData.put("email", "test@example.com");
    userData.put("enabled", true);

    Config config = new Config("users/" + userId, userData);

    Payload payload = new Payload("user", "users/" + userId, "CREATE", config);

    return new ConfigEvent(metadata, payload);
  }

  private CloudEvent createCloudEvent(ConfigEvent configEvent) throws Exception {
    String jsonData = objectMapper.writeValueAsString(configEvent);

    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(URI.create("test.producer"))
        .withType("user.created")
        .withDataContentType("application/json")
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
    public void processConfigEvent(String topic, ConfigEvent event) {
        int currentAttempt = attemptCount.incrementAndGet();

        if (shouldFailOnce && currentAttempt == 1) {
            throw new RuntimeException("Simulated DB Crash at first try!");
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
    public void close() {
      // No cleanup needed
    }
  }

  @FunctionalInterface
  interface ProcessCallback {
    void onProcess(String topic, ConfigEvent event);
  }
}

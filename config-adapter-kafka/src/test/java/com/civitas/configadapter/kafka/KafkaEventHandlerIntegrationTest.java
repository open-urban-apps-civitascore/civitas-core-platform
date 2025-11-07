package com.civitas.configadapter.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.civitas.configadapter.adapter.ConfigAdapter;
import com.civitas.configadapter.config.AppConfig;
import com.civitas.configadapter.messaging.EventPublisher;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Payload;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventSerializer;

/**
 * Integration test for KafkaEventHandler using Testcontainers.
 * Tests the full flow: Kafka -> Handler -> Adapter -> Publisher -> Kafka
 */
@Testcontainers
class KafkaEventHandlerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
            .withReuse(false);

    private KafkaEventHandler handler;
    private TestAdapter testAdapter;
    private KafkaProducer<String, CloudEvent> testProducer;
    private AppConfig config;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Create test configuration
        Properties props = new Properties();
        props.setProperty("kafka.bootstrap.servers", kafka.getBootstrapServers());
        props.setProperty("kafka.group.id", "test-group-" + UUID.randomUUID());
        props.setProperty("kafka.auto.offset.reset", "earliest");
        config = new AppConfig(props);

        // Create test adapter
        testAdapter = new TestAdapter();
        
        // Create test producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
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

        // Wait a bit for handler to be ready
        Thread.sleep(1000);

        // Create and send test event
        ConfigEvent configEvent = createTestConfigEvent("test-user-123");
        CloudEvent cloudEvent = createCloudEvent(configEvent);

        // When
        testProducer.send(new ProducerRecord<>(topic, "test-key", cloudEvent)).get();
        testProducer.flush();

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Event should be processed within 10 seconds");
        assertEquals(1, testAdapter.getProcessedEvents().size());

        ConfigEvent receivedEvent = testAdapter.getProcessedEvents().get(0);
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
        List<CloudEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

        testAdapter.setProcessCallback((t, event) -> {
            // Adapter publishes result event
            if (testAdapter.eventPublisher != null) {
                CloudEvent resultEvent = CloudEventBuilder.v1()
                        .withId(UUID.randomUUID().toString())
                        .withSource(URI.create("test.adapter"))
                        .withType("test.result")
                        .withExtension("status", "SUCCESS")
                        .build();
                testAdapter.eventPublisher.publish(event.metadata().resultTopic(), resultEvent);
                publishedEvents.add(resultEvent);
                publishLatch.countDown();
            }
        });

        // Create handler
        handler = new KafkaEventHandler(config, testAdapter);
        handler.start();
        Thread.sleep(1000);

        // Create test event with result topic
        ConfigEvent configEvent = createTestConfigEvent("test-user-456");
        CloudEvent cloudEvent = createCloudEvent(configEvent);

        // When
        testProducer.send(new ProducerRecord<>(topic, "test-key", cloudEvent)).get();
        testProducer.flush();

        // Then
        assertTrue(publishLatch.await(10, TimeUnit.SECONDS), "Result should be published within 10 seconds");
        assertEquals(1, publishedEvents.size());
        assertEquals("SUCCESS", publishedEvents.get(0).getExtension("status"));
    }

    @Test
    void shouldHandleMultipleTopics() throws Exception {
        // Given
        testAdapter.setSubscribedTopics(List.of("user.created", "user.updated", "user.deleted"));
        CountDownLatch latch = new CountDownLatch(3);

        testAdapter.setProcessCallback((topic, event) -> latch.countDown());

        handler = new KafkaEventHandler(config, testAdapter);
        handler.start();
        Thread.sleep(1000);

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
        Thread.sleep(1000);

        // Create CloudEvent with no data
        CloudEvent cloudEvent = CloudEventBuilder.v1()
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

    // Helper methods

    private ConfigEvent createTestConfigEvent(String userId) {
        Metadata metadata = new Metadata(
                UUID.randomUUID().toString(),
                OffsetDateTime.now().toString(),
                "test.source",
                UUID.randomUUID().toString(),
                "1.0",
                "result.topic"
        );

        Map<String, Object> userData = new HashMap<>();
        userData.put("username", "testuser");
        userData.put("email", "test@example.com");
        userData.put("enabled", true);

        Config config = new Config("users/" + userId, userData);

        Payload payload = new Payload(
                "user",
                "users/" + userId,
                "CREATE",
                config
        );

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

    /**
     * Test adapter implementation
     */
    static class TestAdapter implements ConfigAdapter {
        private List<String> subscribedTopics = null;
        private final List<ConfigEvent> processedEvents = Collections.synchronizedList(new ArrayList<>());
        private ProcessCallback processCallback;
        EventPublisher eventPublisher;

        @Override
        public List<String> getSubscribedTopics() {
            return subscribedTopics;
        }

        public void setSubscribedTopics(List<String> topics) {
            this.subscribedTopics = topics;
        }

        @Override
        public void processConfigEvent(String topic, ConfigEvent event) {
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

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.civitas.configadapter.Constants;
import com.civitas.configadapter.Topics;
import com.civitas.configadapter.configuration.AppConfig;
import com.civitas.configadapter.exception.FatalAdapterException;
import com.civitas.configadapter.keycloak.KeycloakAdapter;
import com.civitas.configadapter.model.Config;
import com.civitas.configadapter.model.ConfigEvent;
import com.civitas.configadapter.model.ConfigResultEvent;
import com.civitas.configadapter.model.Metadata;
import com.civitas.configadapter.model.Operation;
import com.civitas.configadapter.model.Payload;
import com.civitas.configadapter.model.idm.IdmConfigValue;
import com.civitas.configadapter.model.idm.RealmConfig;
import com.civitas.configadapter.model.idm.UserConfig;
import com.civitas.event.handler.kafka.KafkaEventHandler;
import com.civitas.event.handler.kafka.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.kafka.CloudEventDeserializer;
import io.cloudevents.kafka.CloudEventSerializer;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test that tests the complete flow: Kafka Producer -> Kafka ->
 * KafkaEventHandler -> KeycloakAdapter -> Keycloak -> Result Event -> Kafka
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndIntegrationTest {

  @SuppressWarnings("resource")
  @Container
  static ConfluentKafkaContainer kafka =
      new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.3"))
          .withReuse(false);

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> keycloak =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:23.0"))
          .withExposedPorts(8080)
          .withEnv("KEYCLOAK_ADMIN", "admin")
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
          .withCommand("start-dev")
          .withReuse(false);

  private KafkaEventHandler consumer;
  private KeycloakAdapter adapter;
  private KafkaProducer<String, CloudEvent> producer;
  private KafkaConsumer<String, CloudEvent> resultConsumer;
  private Keycloak keycloakClient;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws InterruptedException, FatalAdapterException {
    objectMapper = ObjectMapperFactory.createObjectMapper();

    String keycloakUrl = "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080);

    // Wait for Keycloak to be ready
    waitForKeycloakReady(keycloakUrl);

    // Create configuration
    Map<String, Object> props = new HashMap<>();
    props.put("kafka.bootstrap.servers", kafka.getBootstrapServers());
    props.put("kafka.group.id", "e2e-test-group-" + UUID.randomUUID());
    props.put("kafka.auto.offset.reset", "earliest");
    props.put("keycloak.url", keycloakUrl);
    props.put("keycloak.realm", "master");
    props.put("keycloak.username", "admin");
    props.put("keycloak.password", "admin");
    props.put("keycloak.client.id", "admin-cli");
    props.put(
        "keycloak.topics",
        String.join(
            ",",
            Topics.USER_CREATED.toString(),
            Topics.USER_UPDATED.toString(),
            Topics.USER_DELETED.toString(),
            Topics.USER_LOCKED.toString(),
            Topics.USER_UNLOCKED.toString(),
            Topics.USER_PASSWORD_CHANGED.toString(),
            Topics.USER_PASSWORD_RESET.toString(),
            Topics.REALM_CREATED.toString(),
            Topics.REALM_UPDATED.toString(),
            Topics.REALM_DELETED.toString(),
            Topics.CLIENT_CREATED.toString(),
            Topics.CLIENT_UPDATED.toString(),
            Topics.CLIENT_DELETED.toString()));
    AppConfig config = new AppConfig(new MapConfiguration(props));

    // Create adapter
    adapter = new KeycloakAdapter();
    adapter.initialize(config);

    // Create consumer with adapter
    consumer = new KafkaEventHandler();
    consumer.initialize(config, adapter);
    consumer.start();

    // Create Kafka producer for sending test events
    Properties producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    producerProps.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CloudEventSerializer.class.getName());
    // Don't specify encoding - use default binary mode
    producer = new KafkaProducer<>(producerProps);

    // Create Kafka consumer for reading result events
    Properties consumerProps = new Properties();
    consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "result-consumer-" + UUID.randomUUID());
    consumerProps.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    consumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, CloudEventDeserializer.class.getName());
    consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    // Deserializer will auto-detect encoding mode from CloudEvent headers
    resultConsumer = new KafkaConsumer<>(consumerProps);
    resultConsumer.subscribe(Collections.singletonList("result.topic"));

    // Poll once to trigger topic creation and partition assignment
    resultConsumer.poll(Duration.ofMillis(100));

    // Create Keycloak client for verification
    keycloakClient = Keycloak.getInstance(keycloakUrl, "master", "admin", "admin", "admin-cli");
  }

  @AfterEach
  void tearDown() {
    // Clean up any test realms (ignore errors if they don't exist)
    if (keycloakClient != null) {
      try {
        cleanupTestRealms();
      } catch (Exception e) {
        // Ignore cleanup errors
      }
      keycloakClient.close();
    }

    if (consumer != null) {
      consumer.close();
    }
    if (producer != null) {
      producer.close();
    }
    if (resultConsumer != null) {
      resultConsumer.close();
    }
  }

  private void cleanupTestRealms() {
    // Try to delete test realms - don't fail if they don't exist
    String[] testRealms = {
      "e2e-test-realm", "user-e2e-realm", "client-realm", "seq-test-realm", "correlation-test-realm"
    };

    for (String realm : testRealms) {
      try {
        keycloakClient.realm(realm).remove();
      } catch (Exception e) {
        // Realm doesn't exist or already deleted
      }
    }
  }

  @Test
  @Order(1)
  void shouldHandleCompleteRealmCreationFlow() throws Exception {
    // Given - create RealmConfig directly (as developers would)
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("e2e-test-realm");
    realmConfig.setEnabled(true);
    realmConfig.setDisplayName("E2E Test Realm");

    ConfigEvent configEvent =
        createConfigEvent(
            "e2e-test-realm", "realm", Operation.CREATE, realmConfig, "correlation123");

    CloudEvent cloudEvent = wrapInCloudEvent(configEvent, Topics.REALM_CREATED.toString());

    // When - send event to Kafka
    producer.send(new ProducerRecord<>(Topics.REALM_CREATED.toString(), "key", cloudEvent)).get();
    producer.flush();

    // Then - wait for result event
    CloudEvent resultEvent = waitForResultEvent("correlation123", 15000);
    assertNotNull(resultEvent, "Result event should be published");
    assertEquals("SUCCESS", resultEvent.getExtension("status"));
    assertEquals("e2e-test-realm", resultEvent.getExtension("resourceid"));

    // Verify CloudEvent type is set from resultType
    assertEquals(IdmConfigValue.IDM_RESULT_TYPE, resultEvent.getType());

    // Verify CloudEvent data contains the serialized ConfigResultEvent
    assertNotNull(resultEvent.getData(), "CloudEvent data should not be null");
    ConfigResultEvent resultData =
        objectMapper.readValue(resultEvent.getData().toBytes(), ConfigResultEvent.class);
    assertEquals("correlation123", resultData.correlationId());
    assertEquals(ConfigResultEvent.Status.SUCCESS, resultData.status());
    assertEquals(Operation.CREATE, resultData.operation());
    assertEquals("e2e-test-realm", resultData.resourceId());
    assertEquals(IdmConfigValue.IDM_RESULT_TYPE, resultData.resultType());

    // Verify realm was actually created in Keycloak
    RealmRepresentation createdRealm = keycloakClient.realm("e2e-test-realm").toRepresentation();
    assertNotNull(createdRealm);
    assertEquals("e2e-test-realm", createdRealm.getRealm());
    assertEquals("E2E Test Realm", createdRealm.getDisplayName());
  }

  @Test
  @Order(2)
  void shouldHandleCompleteUserCreationFlow() throws Exception {
    // Given - create test realm first
    RealmRepresentation realmRep = new RealmRepresentation();
    realmRep.setRealm("user-e2e-realm");
    realmRep.setEnabled(true);
    keycloakClient.realms().create(realmRep);

    // Create UserConfig directly
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("e2euser");
    userConfig.setEmail("e2euser@example.com");
    userConfig.setFirstName("E2E");
    userConfig.setLastName("User");
    userConfig.setEnabled(true);

    ConfigEvent configEvent =
        createConfigEvent(
            "user-e2e-realm", "user", Operation.CREATE, userConfig, "correlationuser123");

    CloudEvent cloudEvent = wrapInCloudEvent(configEvent, Topics.USER_CREATED.toString());

    // When - send event to Kafka
    producer.send(new ProducerRecord<>(Topics.USER_CREATED.toString(), "key", cloudEvent)).get();
    producer.flush();

    // Then - wait for result event
    CloudEvent resultEvent = waitForResultEvent("correlationuser123", 15000);
    assertNotNull(resultEvent, "Result event should be published");
    assertEquals("SUCCESS", resultEvent.getExtension("status"));

    // Resource ID should be the Keycloak-generated UUID (used for future UPDATE/DELETE operations)
    String resourceId = (String) resultEvent.getExtension("resourceid");
    assertNotNull(resourceId, "Resource ID should be set");
    assertTrue(
        resourceId.matches("[0-9a-f-]{36}"),
        "Resource ID should be a UUID, but was: " + resourceId);

    // Verify user was actually created in Keycloak
    List<UserRepresentation> users =
        keycloakClient.realm("user-e2e-realm").users().search("e2euser");
    assertEquals(1, users.size());
    assertEquals("e2euser", users.get(0).getUsername());
  }

  @Test
  @Order(3)
  void shouldHandleErrorsEndToEnd() throws Exception {
    // Given - try to update non-existent realm
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("non-existent-realm");

    ConfigEvent configEvent =
        createConfigEvent(
            "non-existent-realm", "realm", Operation.UPDATE, realmConfig, "correlationerror123");

    CloudEvent cloudEvent = wrapInCloudEvent(configEvent, Topics.REALM_UPDATED.toString());

    // When - send event to Kafka
    producer.send(new ProducerRecord<>(Topics.REALM_UPDATED.toString(), "key", cloudEvent)).get();
    producer.flush();

    // Then - wait for error result event
    CloudEvent resultEvent = waitForResultEvent("correlationerror123", 15000);
    assertNotNull(resultEvent, "Error result event should be published");
    assertEquals("FAILURE", resultEvent.getExtension("status"));
    assertNotNull(resultEvent.getExtension("errorcode"));
    assertNotNull(resultEvent.getExtension("errormessage"));

    // Verify CloudEvent type is set from resultType
    assertEquals(IdmConfigValue.IDM_RESULT_TYPE, resultEvent.getType());

    // Verify CloudEvent data contains the serialized ConfigResultEvent with error details
    assertNotNull(resultEvent.getData(), "CloudEvent data should not be null");
    ConfigResultEvent resultData =
        objectMapper.readValue(resultEvent.getData().toBytes(), ConfigResultEvent.class);
    assertEquals("correlationerror123", resultData.correlationId());
    assertEquals(ConfigResultEvent.Status.FAILURE, resultData.status());
    assertEquals(Operation.UPDATE, resultData.operation());
    assertNotNull(resultData.errorCode());
    assertNotNull(resultData.message());
    assertEquals(IdmConfigValue.IDM_RESULT_TYPE, resultData.resultType());
  }

  @Test
  @Order(4)
  void shouldPreserveCorrelationIdThroughoutFlow() throws Exception {
    // Given
    String correlationId = "testcorrelation" + UUID.randomUUID();
    String messageId = UUID.randomUUID().toString();

    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("correlation-test-realm");
    realmConfig.setEnabled(true);

    ConfigEvent configEvent =
        createConfigEventWithMessageId(
            "realms/correlation-test-realm",
            "realm",
            Operation.CREATE,
            realmConfig,
            correlationId,
            messageId);

    CloudEvent cloudEvent = wrapInCloudEvent(configEvent, Topics.REALM_CREATED.toString());

    // When
    producer.send(new ProducerRecord<>(Topics.REALM_CREATED.toString(), "key", cloudEvent)).get();
    producer.flush();

    // Then
    CloudEvent resultEvent = waitForResultEvent(correlationId, 15000);
    assertNotNull(resultEvent);
    assertEquals(correlationId, resultEvent.getExtension("correlationid"));
    assertEquals(messageId, resultEvent.getExtension("originalmessageid"));
    assertEquals(Operation.CREATE.name(), resultEvent.getExtension("operation"));
    assertEquals("realms/correlation-test-realm", resultEvent.getExtension("targetresource"));
  }

  @Test
  @Order(5)
  void shouldHandleMultipleSequentialOperations() throws Exception {
    // This test verifies the adapter can handle multiple operations in sequence

    // 1. Create realm
    RealmConfig realmConfig = new RealmConfig();
    realmConfig.setRealm("seq-test-realm");
    realmConfig.setEnabled(true);

    sendEventAndWaitForResult(
        Topics.REALM_CREATED.toString(),
        createConfigEvent("seq-test-realm", "realm", Operation.CREATE, realmConfig, "seq-1"));

    // 2. Create user
    UserConfig userConfig = new UserConfig();
    userConfig.setUsername("sequser");
    userConfig.setEmail("sequser@example.com");
    userConfig.setEnabled(true);

    sendEventAndWaitForResult(
        Topics.USER_CREATED.toString(),
        createConfigEvent("seq-test-realm", "user", Operation.CREATE, userConfig, "seq-2"));

    // 3. Update user
    List<UserRepresentation> users =
        keycloakClient.realm("seq-test-realm").users().search("sequser");
    String userId = users.get(0).getId();

    userConfig.setId(userId); // Set the Keycloak user ID for UPDATE operation
    userConfig.setFirstName("Updated");

    sendEventAndWaitForResult(
        Topics.USER_UPDATED.toString(),
        createConfigEvent("seq-test-realm", "user", Operation.UPDATE, userConfig, "seq-3"));

    // Verify all operations succeeded
    RealmRepresentation realm = keycloakClient.realm("seq-test-realm").toRepresentation();
    assertNotNull(realm);

    users = keycloakClient.realm("seq-test-realm").users().search("sequser");
    assertEquals(1, users.size());
    assertEquals("Updated", users.get(0).getFirstName());
  }

  // Helper methods

  private void waitForKeycloakReady(String keycloakUrl) {
    ThrowingRunnable assertion =
        () -> {
          try (Keycloak testClient =
              Keycloak.getInstance(keycloakUrl, "master", "admin", "admin", "admin-cli")) {
            testClient.serverInfo().getInfo();
          }
        };
    await()
        .atMost(30, SECONDS)
        .pollInterval(1, SECONDS)
        .ignoreExceptions()
        .untilAsserted(assertion);
  }

  /** Creates a ConfigEvent with a config value - demonstrates how developers use the API. */
  private ConfigEvent createConfigEvent(
      String targetResource,
      String targetComponent,
      Operation operation,
      IdmConfigValue configValue,
      String correlationId) {
    return createConfigEventWithMessageId(
        targetResource,
        targetComponent,
        operation,
        configValue,
        correlationId,
        UUID.randomUUID().toString());
  }

  /** Creates a ConfigEvent with a specific message ID for testing message tracking. */
  private ConfigEvent createConfigEventWithMessageId(
      String targetResource,
      String targetComponent,
      Operation operation,
      IdmConfigValue configValue,
      String correlationId,
      String messageId) {
    Metadata metadata =
        new Metadata(
            messageId, OffsetDateTime.now(), "e2e.test", correlationId, "1.0", "result.topic");

    Config config = new Config(targetResource, configValue);
    Payload payload = new Payload(targetComponent, targetResource, operation, config);

    return new ConfigEvent(metadata, payload);
  }

  private CloudEvent wrapInCloudEvent(ConfigEvent configEvent, String type) throws Exception {
    String jsonData = objectMapper.writeValueAsString(configEvent);

    return CloudEventBuilder.v1()
        .withId(UUID.randomUUID().toString())
        .withSource(URI.create("e2e.test.producer"))
        .withType(type)
        .withDataContentType(Constants.CONTENT_TYPE_JSON)
        .withData(jsonData.getBytes())
        .build();
  }

  private CloudEvent waitForResultEvent(String correlationId, long timeoutMs) {
    long startTime = System.currentTimeMillis();
    int pollCount = 0;

    System.out.println("Waiting for result event with correlationId: " + correlationId);

    while (System.currentTimeMillis() - startTime < timeoutMs) {
      ConsumerRecords<String, CloudEvent> records = resultConsumer.poll(Duration.ofMillis(1000));
      pollCount++;

      System.out.println("Poll #" + pollCount + ": received " + records.count() + " record(s)");

      for (ConsumerRecord<String, CloudEvent> record : records) {
        CloudEvent event = record.value();
        if (event == null) {
          continue;
        }

        String eventCorrelationId = (String) event.getExtension("correlationid");
        if (correlationId.equals(eventCorrelationId)) {
          return event;
        }
      }
    }

    System.out.println("Timeout waiting for result event after " + pollCount + " polls");
    return null;
  }

  private void sendEventAndWaitForResult(String topic, ConfigEvent configEvent) throws Exception {
    CloudEvent cloudEvent = wrapInCloudEvent(configEvent, topic);
    producer.send(new ProducerRecord<>(topic, "key", cloudEvent)).get();
    producer.flush();

    CloudEvent resultEvent = waitForResultEvent(configEvent.metadata().correlationId(), 15000);
    assertNotNull(
        resultEvent,
        "Result event should be published for correlation: "
            + configEvent.metadata().correlationId());
    assertEquals(
        "SUCCESS",
        resultEvent.getExtension("status"),
        "Operation should succeed. Error: " + resultEvent.getExtension("errormessage"));
  }
}

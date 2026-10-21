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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.civitascore.configadapter.crypto.CredentialDecryptor;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestRequest;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Outcome;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.configadapter.model.connectiontest.ConnectionTestTopics;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A request on the request topic, with an encrypted password, comes back as a result on the result
 * topic.
 */
class ConnectionTestConsumerIT {

  static {
    Awaitility.setDefaultPollDelay(Duration.ZERO);
  }

  private static final ConfluentKafkaContainer KAFKA =
      new ConfluentKafkaContainer(DockerImageName.parse(TestContainerImages.KAFKA));

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse(TestContainerImages.POSTGRES))
          .withDatabaseName("source")
          .withUsername("owner")
          .withPassword("owner-secret");

  static {
    KAFKA.start();
    POSTGRES.start();
  }

  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private static byte[] stretchedKey;
  private static ConnectionTestConsumer testConsumer;
  private static KafkaProducer<String, String> requests;
  private static KafkaConsumer<String, String> results;
  private static final List<ConnectionTestResult> RECEIVED = new ArrayList<>();

  @BeforeAll
  static void start() throws Exception {
    stretchedKey =
        CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes("ab".repeat(32)));
    ConnectionTestService service =
        new ConnectionTestService(
            List.of(new SqlProber(host -> {}, Duration.ofSeconds(3))),
            connector ->
                CredentialDecryptor.decryptMapValues(
                    connector, stretchedKey, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT));
    testConsumer =
        new ConnectionTestConsumer(
            new KafkaConsumer<>(consumerProperties("config-adapter-connection-test")),
            new KafkaProducer<>(producerProperties()),
            ConnectionTestTopics.REQUEST,
            ConnectionTestTopics.RESULT,
            service,
            new ConnectionTestSettings(Duration.ofSeconds(5), 2),
            Clock.systemUTC());
    testConsumer.start();

    requests = new KafkaProducer<>(producerProperties());
    results = new KafkaConsumer<>(consumerProperties("portal-backend-" + UUID.randomUUID()));
    results.subscribe(List.of(ConnectionTestTopics.RESULT));
    // The consumer starts at the latest offset, so a sentinel is retried until its result arrives.
    await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofSeconds(1))
        .until(
            () -> {
              send(connector("owner-secret"), "sentinel-" + UUID.randomUUID());
              return poll().stream().anyMatch(result -> result.requestId().startsWith("sentinel-"));
            });
  }

  @AfterAll
  static void stop() {
    testConsumer.close();
    requests.close();
    results.close();
  }

  @Test
  void answersARequestWithAnEncryptedPassword() throws Exception {
    String id = send(connector("owner-secret"), UUID.randomUUID().toString());

    ConnectionTestResult result = awaitResult(id);

    assertEquals(Outcome.SUCCESS, result.outcome(), result.message());
    assertTrue(result.preview().startsWith("datname\r\n"), result.preview());
    assertEquals(result.preview().split("\r\n").length - 1, result.rowCount());
  }

  @Test
  void answersAWrongPasswordWithAFailureThatDoesNotContainIt() throws Exception {
    String id = send(connector("wrong-secret"), UUID.randomUUID().toString());

    ConnectionTestResult result = awaitResult(id);

    assertEquals(Outcome.FAILURE, result.outcome());
    assertEquals(Reason.SOURCE_ERROR, result.reason());
    assertTrue(result.message().contains("password authentication failed"), result.message());
    assertFalse(result.message().contains("wrong-secret"));
  }

  @Test
  void skipsAMalformedRequestAndKeepsAnswering() throws Exception {
    requests.send(new ProducerRecord<>(ConnectionTestTopics.REQUEST, "x", "{not json")).get();
    String id = send(connector("owner-secret"), UUID.randomUUID().toString());

    assertEquals(Outcome.SUCCESS, awaitResult(id).outcome());
  }

  @Test
  void doesNotRunAStaleRequest() throws Exception {
    String stale = UUID.randomUUID().toString();
    ConnectionTestRequest request =
        new ConnectionTestRequest(
            stale, Instant.now().minusSeconds(1), connector("owner-secret"), null);
    requests
        .send(
            new ProducerRecord<>(
                ConnectionTestTopics.REQUEST, stale, MAPPER.writeValueAsString(request)))
        .get();
    String fresh = send(connector("owner-secret"), UUID.randomUUID().toString());

    awaitResult(fresh);
    assertTrue(RECEIVED.stream().noneMatch(result -> result.requestId().equals(stale)));
  }

  private static String send(Map<String, Object> connector, String id) throws Exception {
    ConnectionTestRequest request =
        new ConnectionTestRequest(id, Instant.now().plusSeconds(20), connector, null);
    requests
        .send(
            new ProducerRecord<>(
                ConnectionTestTopics.REQUEST, id, MAPPER.writeValueAsString(request)))
        .get();
    return id;
  }

  private static ConnectionTestResult awaitResult(String id) {
    return await()
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                poll().stream()
                    .filter(result -> result.requestId().equals(id))
                    .findFirst()
                    .orElse(null),
            result -> result != null);
  }

  private static synchronized List<ConnectionTestResult> poll() throws Exception {
    for (ConsumerRecord<String, String> record : results.poll(Duration.ofMillis(200))) {
      RECEIVED.add(MAPPER.readValue(record.value(), ConnectionTestResult.class));
    }
    return List.copyOf(RECEIVED);
  }

  private static Map<String, Object> connector(String password) throws Exception {
    Map<String, Object> connector = new HashMap<>();
    connector.put("connectionType", "sql");
    connector.put(
        "dsn",
        "postgres://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/source");
    connector.put("table", "pg_catalog.pg_database");
    connector.put("columns", List.of("datname"));
    connector.put("user", "owner");
    connector.put(
        "password",
        "ENC("
            + CredentialEncryptor.encrypt(
                password, stretchedKey, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")");
    return connector;
  }

  private static Properties consumerProperties(String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    return props;
  }

  private static Properties producerProperties() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    return props;
  }
}

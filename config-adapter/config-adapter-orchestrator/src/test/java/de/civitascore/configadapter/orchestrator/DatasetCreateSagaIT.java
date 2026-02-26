/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.orchestrator;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.civitascore.configadapter.apisix.ApisixSagaHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.frost.FrostSagaHandler;
import de.civitascore.configadapter.model.saga.SagaContext;
import de.civitascore.configadapter.model.saga.SagaType;
import de.civitascore.event.handler.kafka.KafkaSagaCommandConsumer;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test for the Dataset Create saga. Validates the full orchestration flow
 * through real Kafka, a real FROST Server (with PostGIS), and a mock APISIX HTTP server.
 *
 * <pre>
 *   DatasetCreateSagaIT
 *          │
 *    startSaga(DATASET_CREATE, ...)
 *          │
 *          ▼
 *   DatasetSagaOrchestrator ──────────────────────────┐
 *   (real engine, Kafka state store, Kafka dispatcher) │
 *          │                                           │
 *   ExecuteStep (Kafka)                    STEP_COMPLETED (Kafka)
 *          │                                           │
 *          ▼                                           │
 *   KafkaSagaCommandConsumer                           │
 *    ├─ FrostSagaHandler  ──▶ FROST Container          │
 *    └─ ApisixSagaHandler ──▶ Mock HTTP Server ────────┘
 * </pre>
 */
@Testcontainers
class DatasetCreateSagaIT {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final String SAGA_RESULT_TOPIC = "de.civitascore.saga.result";

  static Network network = Network.newNetwork();

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> postgis =
      new GenericContainer<>(DockerImageName.parse("postgis/postgis:16-3.4-alpine"))
          .withNetwork(network)
          .withNetworkAliases("database")
          .withEnv("POSTGRES_DB", "sensorthings")
          .withEnv("POSTGRES_USER", "sensorthings")
          .withEnv("POSTGRES_PASSWORD", "ChangeMe")
          .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2));

  @SuppressWarnings("resource")
  @Container
  static GenericContainer<?> frost =
      new GenericContainer<>(DockerImageName.parse("hylkevds/frost-http-projects:latest"))
          .withNetwork(network)
          .withExposedPorts(8080)
          .dependsOn(postgis)
          .withEnv("serviceRootUrl", "http://localhost:8080/FROST-Server/")
          .withEnv("plugins_modelLoader_enable", "true")
          .withEnv("plugins_multiDatastream_enable", "false")
          .withEnv("plugins_actuation_enable", "false")
          .withEnv("persistence_db_driver", "org.postgresql.Driver")
          .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
          .withEnv("persistence_db_username", "sensorthings")
          .withEnv("persistence_db_password", "ChangeMe")
          .withEnv("persistence_autoUpdateDatabase", "true")
          .withEnv("plugins_modelLoader_securityPath", "")
          .withEnv("plugins_modelLoader_securityFiles", "")
          .waitingFor(
              Wait.forHttp("/FROST-Server/v1.1/Projects")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().registerModule(new JavaTimeModule());

  private HttpServer apisixMock;
  private List<RecordedApisixRequest> apisixRequests;
  private DatasetSagaOrchestrator orchestrator;
  private KafkaSagaCommandConsumer commandConsumer;
  private KafkaConsumer<String, byte[]> verificationConsumer;
  private Client httpClient;
  private String frostBaseUrl;

  @BeforeAll
  static void createTopics() throws InterruptedException, ExecutionException, TimeoutException {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    try (AdminClient admin = AdminClient.create(props)) {
      List<NewTopic> topics =
          List.of(
              new NewTopic("de.civitascore.saga.state", 1, (short) 1),
              new NewTopic(SAGA_RESULT_TOPIC, 1, (short) 1),
              new NewTopic("de.civitascore.saga.manual-intervention", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.frost.execute", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.apisix.execute", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.redpanda.execute", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.frost.compensate", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.apisix.compensate", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.frost.result", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.apisix.result", 1, (short) 1),
              new NewTopic("de.civitascore.dataset.redpanda.result", 1, (short) 1));
      admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
    }
  }

  @BeforeEach
  void setUp() {
    frostBaseUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();
    waitForFrostReady();
  }

  @AfterEach
  void tearDown() {
    if (orchestrator != null) {
      orchestrator.stop();
    }
    if (commandConsumer != null) {
      commandConsumer.close();
    }
    if (verificationConsumer != null) {
      verificationConsumer.close();
    }
    if (apisixMock != null) {
      apisixMock.stop(0);
    }
    if (httpClient != null) {
      httpClient.close();
    }
  }

  @AfterAll
  static void tearDownNetwork() {
    network.close();
  }

  @Test
  void datasetCreateSaga_withoutPipelines_completesSuccessfully() throws Exception {
    String apisixMockUrl = startApisixMock();
    wireAndStart(frostBaseUrl, apisixMockUrl);

    String datasetId = "ds-e2e-" + System.nanoTime();
    Map<String, Object> triggerPayload = buildTriggerPayload(datasetId);

    Optional<SagaContext> sagaContext =
        orchestrator.startSaga(SagaType.DATASET_CREATE, datasetId, triggerPayload);
    assertTrue(sagaContext.isPresent());
    String sagaId = sagaContext.get().sagaId();

    Map<String, Object> sagaResult = awaitSagaResultMessage(sagaId, "SAGA_COMPLETED");

    // Verify: saga result contains expected resource IDs
    @SuppressWarnings("unchecked")
    Map<String, Object> resultPayload = (Map<String, Object>) sagaResult.get("result");
    assertNotNull(resultPayload, "Saga result should contain a result payload");
    assertNotNull(resultPayload.get("projectId"), "Result should contain projectId from FROST");
    assertEquals(datasetId, resultPayload.get("routeId"), "Route ID should be the datasetId");
    assertEquals(datasetId, resultPayload.get("serviceId"), "Service ID should be the datasetId");

    // Verify: FROST project actually exists
    String projectId = String.valueOf(resultPayload.get("projectId"));
    try (Response frostResponse =
        httpClient
            .target(frostBaseUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(200, frostResponse.getStatus(), "FROST project should exist after saga");
      String body = frostResponse.readEntity(String.class);
      assertTrue(body.contains("E2E Test Dataset"), "Project name should match trigger payload");
    }

    // Verify: APISIX mock received PUT requests for upstream and route
    assertFalse(apisixRequests.isEmpty(), "APISIX mock should have received requests");
    assertTrue(
        apisixRequests.stream()
            .anyMatch(
                r ->
                    "PUT".equals(r.method)
                        && r.path.contains("/apisix/admin/upstreams/" + datasetId)),
        "APISIX mock should have received PUT upstream request");
    assertTrue(
        apisixRequests.stream()
            .anyMatch(
                r ->
                    "PUT".equals(r.method) && r.path.contains("/apisix/admin/routes/" + datasetId)),
        "APISIX mock should have received PUT route request");
  }

  @Test
  void datasetCreateSaga_frostFailure_publishesSagaFailed() throws Exception {
    String unreachableFrostUrl = "http://localhost:1/FROST-Server/v1.1";
    String apisixMockUrl = startApisixMock();
    wireAndStart(unreachableFrostUrl, apisixMockUrl);

    String datasetId = "ds-e2e-fail-" + System.nanoTime();
    Map<String, Object> triggerPayload = buildTriggerPayload(datasetId);

    Optional<SagaContext> sagaContext =
        orchestrator.startSaga(SagaType.DATASET_CREATE, datasetId, triggerPayload);
    assertTrue(sagaContext.isPresent());
    String sagaId = sagaContext.get().sagaId();

    Map<String, Object> sagaResult = awaitSagaResultMessage(sagaId, "SAGA_FAILED");

    // Verify: saga failed with compensation completed (no steps succeeded, nothing to compensate)
    assertEquals(true, sagaResult.get("compensated"), "Failed at step 1, should be compensated");

    // Verify: no APISIX calls were made for this dataset (saga never reached step 2)
    assertTrue(
        apisixRequests.stream().noneMatch(r -> r.path.contains(datasetId)),
        "APISIX mock should not have received requests for the failed dataset");
  }

  // ─── Wiring ──────────────────────────────────────────────────────────────────

  private void wireAndStart(String frostUrl, String apisixMockUrl) {
    // Initialize saga command handlers
    FrostSagaHandler frostHandler = new FrostSagaHandler();
    frostHandler.initialize(
        mapConfig(
            Map.of(
                "frost.url", frostUrl,
                "frost.api.key", "test-api-key",
                "frost.api.key.header", "X-API-Key")));

    ApisixSagaHandler apisixHandler = new ApisixSagaHandler();
    apisixHandler.initialize(
        mapConfig(Map.of("apisix.admin.url", apisixMockUrl, "apisix.admin.key", "test-admin-key")));

    // Create the Kafka command consumer with both handlers
    KafkaConsumer<String, byte[]> commandKafkaConsumer =
        createByteConsumer("saga-command-e2e-" + System.nanoTime());
    KafkaProducer<String, byte[]> commandKafkaProducer = createProducer();
    commandConsumer =
        new KafkaSagaCommandConsumer(
            commandKafkaConsumer,
            commandKafkaProducer,
            Map.of("frost", frostHandler, "apisix", apisixHandler));
    commandConsumer.start();

    // Initialize and start the orchestrator
    orchestrator = new DatasetSagaOrchestrator();
    orchestrator.initialize(kafka.getBootstrapServers());
    orchestrator.start();

    // Create a verification consumer to read saga results
    verificationConsumer = createByteConsumer("saga-verify-e2e-" + System.nanoTime());
    verificationConsumer.subscribe(List.of(SAGA_RESULT_TOPIC));
    // Trigger initial partition assignment
    verificationConsumer.poll(Duration.ofMillis(500));
  }

  // ─── APISIX Mock ────────────────────────────────────────────────────────────

  private String startApisixMock() throws IOException {
    apisixRequests = Collections.synchronizedList(new ArrayList<>());
    apisixMock = HttpServer.create(new InetSocketAddress(0), 0);
    apisixMock.createContext("/apisix/admin/", this::handleApisixRequest);
    apisixMock.setExecutor(null);
    apisixMock.start();
    int port = apisixMock.getAddress().getPort();
    return "http://localhost:" + port;
  }

  private void handleApisixRequest(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    // Drain request body
    exchange.getRequestBody().readAllBytes();

    apisixRequests.add(new RecordedApisixRequest(method, path));

    String responseJson = "{\"value\":{}}";
    byte[] responseBytes = responseJson.getBytes();
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, responseBytes.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(responseBytes);
    }
  }

  // ─── Trigger Payload ────────────────────────────────────────────────────────

  private Map<String, Object> buildTriggerPayload(String datasetId) {
    var payload = new HashMap<String, Object>();
    payload.put("id", datasetId);
    payload.put("datasetId", datasetId);
    payload.put("datasetName", "E2E Test Dataset");
    payload.put("name", "E2E Test Dataset");
    payload.put("description", "Integration test dataset");
    payload.put("openDataAccess", true);
    payload.put("upstreamUrl", frostBaseUrl);
    payload.put("datasources", List.of());
    payload.put("dataPipelines", List.of());
    return payload;
  }

  // ─── Verification Helpers ──────────────────────────────────────────────────

  private Map<String, Object> awaitSagaResultMessage(String sagaId, String expectedType) {
    var resultHolder = new ArrayList<Map<String, Object>>(1);

    await()
        .atMost(90, SECONDS)
        .pollInterval(1, SECONDS)
        .untilAsserted(
            () -> {
              ConsumerRecords<String, byte[]> records =
                  verificationConsumer.poll(Duration.ofMillis(500));
              for (ConsumerRecord<String, byte[]> record : records) {
                Map<String, Object> message = OBJECT_MAPPER.readValue(record.value(), MAP_TYPE);
                if (sagaId.equals(message.get("sagaId"))
                    && expectedType.equals(message.get("type"))) {
                  resultHolder.add(message);
                }
              }
              assertFalse(
                  resultHolder.isEmpty(), "Expected " + expectedType + " for saga " + sagaId);
            });

    return resultHolder.getFirst();
  }

  private void waitForFrostReady() {
    await()
        .atMost(60, SECONDS)
        .pollInterval(2, SECONDS)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              try (Response response =
                  httpClient.target(frostBaseUrl).path("Projects").request().get()) {
                assertEquals(200, response.getStatus());
              }
            });
  }

  // ─── Kafka Client Factories ────────────────────────────────────────────────

  private KafkaProducer<String, byte[]> createProducer() {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props);
  }

  private KafkaConsumer<String, byte[]> createByteConsumer(String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }

  // ─── Simple AdapterConfig backed by a Map ─────────────────────────────────

  private static AdapterConfig mapConfig(Map<String, String> properties) {
    return new AdapterConfig() {
      @Override
      public String getProperty(String key) {
        return properties.get(key);
      }

      @Override
      public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
      }
    };
  }

  // ─── Recorded Request ──────────────────────────────────────────────────────

  record RecordedApisixRequest(String method, String path) {}
}

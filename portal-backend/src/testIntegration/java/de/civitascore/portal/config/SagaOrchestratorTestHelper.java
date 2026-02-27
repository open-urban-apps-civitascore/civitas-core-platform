package de.civitascore.portal.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.civitascore.configadapter.apisix.ApisixSagaHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.frost.FrostSagaHandler;
import de.civitascore.configadapter.orchestrator.DatasetSagaOrchestrator;
import de.civitascore.configadapter.orchestrator.kafka.SagaTriggerConsumer;
import de.civitascore.configadapter.redpanda.RedpandaSagaHandler;
import de.civitascore.event.handler.kafka.KafkaSagaCommandConsumer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * In-process helper that wires the full saga orchestrator stack for E2E integration tests.
 *
 * <p>Creates all required Kafka topics, then starts:
 *
 * <ol>
 *   <li>{@link DatasetSagaOrchestrator} — state machine + result consumer
 *   <li>{@link SagaTriggerConsumer} — bridges portal-backend trigger messages to the orchestrator
 *   <li>{@link KafkaSagaCommandConsumer} — executes adapter commands (FROST, APISIX, Redpanda)
 * </ol>
 *
 * <p>Also starts a lightweight APISIX mock HTTP server.
 *
 * <p>Implements {@link AutoCloseable} for clean shutdown in reverse order.
 */
@Slf4j
public class SagaOrchestratorTestHelper implements AutoCloseable {

  private static final List<String> SAGA_TOPICS =
      List.of(
          "de.civitascore.dataset.saga.trigger",
          "de.civitascore.saga.state",
          "de.civitascore.saga.result",
          "de.civitascore.saga.manual-intervention",
          "de.civitascore.dataset.frost.execute",
          "de.civitascore.dataset.frost.compensate",
          "de.civitascore.dataset.frost.result",
          "de.civitascore.dataset.apisix.execute",
          "de.civitascore.dataset.apisix.compensate",
          "de.civitascore.dataset.apisix.result",
          "de.civitascore.dataset.redpanda.execute",
          "de.civitascore.dataset.redpanda.compensate",
          "de.civitascore.dataset.redpanda.result");

  private final DatasetSagaOrchestrator orchestrator;
  private final SagaTriggerConsumer triggerConsumer;
  private final KafkaSagaCommandConsumer commandConsumer;
  private final HttpServer apisixMock;

  @Getter private final String apisixMockUrl;
  @Getter private final List<RecordedApisixRequest> apisixRequests;

  public SagaOrchestratorTestHelper(
      String kafkaBrokers, String frostBaseUrl, String redpandaConnectUrl) {

    log.info("Initializing SagaOrchestratorTestHelper");

    createTopics(kafkaBrokers);

    apisixRequests = Collections.synchronizedList(new ArrayList<>());
    try {
      apisixMock = HttpServer.create(new InetSocketAddress(0), 0);
      apisixMock.createContext("/apisix/admin/", this::handleApisixRequest);
      apisixMock.setExecutor(null);
      apisixMock.start();
      apisixMockUrl = "http://localhost:" + apisixMock.getAddress().getPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start APISIX mock", e);
    }
    log.info("APISIX mock started at {}", apisixMockUrl);

    FrostSagaHandler frostHandler = new FrostSagaHandler();
    frostHandler.initialize(
        mapConfig(
            Map.of(
                "frost.url", frostBaseUrl,
                "frost.api.key", "test-api-key",
                "frost.api.key.header", "X-API-Key")));

    ApisixSagaHandler apisixHandler = new ApisixSagaHandler();
    apisixHandler.initialize(
        mapConfig(Map.of("apisix.admin.url", apisixMockUrl, "apisix.admin.key", "test-admin-key")));

    RedpandaSagaHandler redpandaHandler = new RedpandaSagaHandler();
    redpandaHandler.initialize(mapConfig(Map.of("redpanda.url", redpandaConnectUrl)));

    orchestrator = new DatasetSagaOrchestrator();
    orchestrator.initialize(kafkaBrokers);
    orchestrator.start();
    log.info("DatasetSagaOrchestrator started");

    KafkaConsumer<String, byte[]> triggerKafkaConsumer =
        createByteConsumer(kafkaBrokers, "saga-trigger-e2e-" + System.nanoTime());
    triggerConsumer = new SagaTriggerConsumer(triggerKafkaConsumer, orchestrator);
    triggerConsumer.start();
    log.info("SagaTriggerConsumer started");

    KafkaConsumer<String, byte[]> commandKafkaConsumer =
        createByteConsumer(kafkaBrokers, "saga-command-e2e-" + System.nanoTime());
    KafkaProducer<String, byte[]> commandKafkaProducer = createProducer(kafkaBrokers);
    commandConsumer =
        new KafkaSagaCommandConsumer(
            commandKafkaConsumer,
            commandKafkaProducer,
            Map.of("frost", frostHandler, "apisix", apisixHandler, "redpanda", redpandaHandler));
    commandConsumer.start();
    log.info("KafkaSagaCommandConsumer started");
  }

  @Override
  public void close() {
    log.info("Shutting down SagaOrchestratorTestHelper");

    // Reverse startup order
    if (commandConsumer != null) {
      try {
        commandConsumer.close();
      } catch (Exception e) {
        log.warn("Error closing command consumer: {}", e.getMessage());
      }
    }

    if (triggerConsumer != null) {
      try {
        triggerConsumer.stop();
      } catch (Exception e) {
        log.warn("Error stopping trigger consumer: {}", e.getMessage());
      }
    }

    if (orchestrator != null) {
      try {
        orchestrator.stop();
      } catch (Exception e) {
        log.warn("Error stopping orchestrator: {}", e.getMessage());
      }
    }

    if (apisixMock != null) {
      apisixMock.stop(0);
    }

    log.info("SagaOrchestratorTestHelper shut down");
  }

  private void createTopics(String kafkaBrokers) {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers);
    try (AdminClient admin = AdminClient.create(props)) {
      List<NewTopic> topics =
          SAGA_TOPICS.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList();
      var result = admin.createTopics(topics);
      for (var entry : result.values().entrySet()) {
        try {
          entry.getValue().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          if (e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException) {
            log.debug("Topic {} already exists, skipping", entry.getKey());
          } else {
            throw new IllegalStateException("Failed to create topic: " + entry.getKey(), e);
          }
        }
      }
      log.info("Ensured {} Kafka saga topics exist", topics.size());
    } catch (InterruptedException | TimeoutException e) {
      throw new IllegalStateException("Failed to create Kafka saga topics", e);
    }
  }

  private void handleApisixRequest(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
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

  private static KafkaConsumer<String, byte[]> createByteConsumer(
      String bootstrapServers, String groupId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    return new KafkaConsumer<>(props);
  }

  private static KafkaProducer<String, byte[]> createProducer(String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new KafkaProducer<>(props);
  }

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

  public record RecordedApisixRequest(String method, String path) {}
}

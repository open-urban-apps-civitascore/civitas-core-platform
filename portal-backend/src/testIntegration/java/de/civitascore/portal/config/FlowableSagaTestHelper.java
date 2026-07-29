package de.civitascore.portal.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.civitascore.configadapter.adapter.SagaCommandHandler;
import de.civitascore.configadapter.adapter.SagaCommandMessage;
import de.civitascore.configadapter.adapter.SagaCommandResult;
import de.civitascore.configadapter.apisix.ApisixSagaHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.flowable.common.FlowableSagaOrchestrator;
import de.civitascore.configadapter.frost.FrostSagaHandler;
import de.civitascore.configadapter.postgis.PostgisSagaHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

/**
 * In-process helper that runs the production saga stack — the Flowable orchestrator with real
 * FROST, APISIX, and PostGIS handlers — for E2E integration tests.
 *
 * <p>The pipeline-engine step ({@code nifi}) and the GeoServer steps are stubbed with
 * always-success handlers: pipeline deployment awaits the new engine handler, and GeoServer
 * provisioning is covered by the config-adapter's own integration tests.
 */
@Slf4j
public class FlowableSagaTestHelper implements AutoCloseable {

  private static final List<String> SAGA_TOPICS =
      List.of("de.civitascore.dataset.saga.trigger", "de.civitascore.saga.result");

  private final FlowableSagaOrchestrator orchestrator;
  private final PostgisSagaHandler postgisHandler;
  private final HttpServer apisixMock;

  @Getter private final String apisixMockUrl;
  @Getter private final List<String> apisixRequests;

  public FlowableSagaTestHelper(
      String kafkaBrokers,
      String frostBaseUrl,
      String frostPublicUrl,
      String flowableJdbcUrl,
      String postgisJdbcUrl,
      String dbUser,
      String dbPassword) {

    createTopics(kafkaBrokers);

    apisixRequests = Collections.synchronizedList(new ArrayList<>());
    try {
      apisixMock = HttpServer.create(new InetSocketAddress(0), 0);
      apisixMock.createContext("/apisix/admin/", this::handleApisixRequest);
      apisixMock.start();
      apisixMockUrl = "http://localhost:" + apisixMock.getAddress().getPort();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start APISIX mock", e);
    }

    FrostSagaHandler frostHandler = new FrostSagaHandler();
    frostHandler.initialize(
        mapConfig(
            Map.of(
                "frost.url",
                frostBaseUrl,
                "frost.public.url",
                frostPublicUrl,
                "frost.api.key",
                "test-api-key",
                "frost.api.key.header",
                "X-API-Key")));

    ApisixSagaHandler apisixHandler = new ApisixSagaHandler();
    apisixHandler.initialize(
        mapConfig(
            Map.of(
                "apisix.admin.url", apisixMockUrl,
                "apisix.admin.key", "test-admin-key",
                "apisix.api.host", "api.civitas.test",
                "apisix.api.public.url", "https://api.civitas.test",
                "apisix.plugin.config.id", "auth-plugin-default",
                "apisix.service.id", "svc-frost-server",
                "apisix.frost.api.key", "test-api-key")));

    postgisHandler = new PostgisSagaHandler();
    postgisHandler.initialize(
        mapConfig(
            Map.of(
                "postgis.jdbc.url", postgisJdbcUrl,
                "postgis.jdbc.user", dbUser,
                "postgis.jdbc.password", dbPassword)));

    Map<String, SagaCommandHandler> handlers = new HashMap<>();
    handlers.put("frost", frostHandler);
    handlers.put("apisix", apisixHandler);
    handlers.put("postgis", postgisHandler);
    handlers.put("geoserver", stubSuccess("geoserver"));
    handlers.put("nifi", stubSuccess("nifi"));

    orchestrator =
        new FlowableSagaOrchestrator(
            mapConfig(
                Map.of(
                    "flowable.jdbc.url", flowableJdbcUrl,
                    "flowable.jdbc.username", dbUser,
                    "flowable.jdbc.password", dbPassword,
                    "kafka.bootstrap.servers", kafkaBrokers,
                    "flowable.kafka.group.id", "flowable-saga-e2e-" + System.nanoTime())),
            handlers);
    orchestrator.initialize();
    orchestrator.start();
    log.info("FlowableSagaOrchestrator started (flowable db: {})", flowableJdbcUrl);
  }

  @Override
  public void close() {
    if (orchestrator != null) {
      orchestrator.close();
    }
    if (postgisHandler != null) {
      postgisHandler.close();
    }
    if (apisixMock != null) {
      apisixMock.stop(0);
    }
  }

  private static SagaCommandHandler stubSuccess(String adapter) {
    return new SagaCommandHandler() {
      @Override
      public String adapter() {
        return adapter;
      }

      @Override
      public void initialize(AdapterConfig config) {}

      @Override
      public SagaCommandResult handle(SagaCommandMessage message) {
        return "COMPENSATE_STEP".equals(message.type())
            ? SagaCommandResult.compensationSuccess(message.sagaId(), message.stepId())
            : SagaCommandResult.success(message.sagaId(), message.stepId(), Map.of(), Map.of());
      }
    };
  }

  private void createTopics(String kafkaBrokers) {
    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers);
    try (AdminClient admin = AdminClient.create(props)) {
      var result =
          admin.createTopics(
              SAGA_TOPICS.stream().map(name -> new NewTopic(name, 1, (short) 1)).toList());
      for (var entry : result.values().entrySet()) {
        try {
          entry.getValue().get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          if (!(e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
            throw new IllegalStateException("Failed to create topic: " + entry.getKey(), e);
          }
        }
      }
    } catch (InterruptedException | TimeoutException e) {
      throw new IllegalStateException("Failed to create Kafka saga topics", e);
    }
  }

  private void handleApisixRequest(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    apisixRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
    byte[] response = "{\"value\":{}}".getBytes();
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response);
    }
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
}

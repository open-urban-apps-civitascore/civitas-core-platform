/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.flowable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.civitascore.configadapter.apisix.ApisixSagaHandler;
import de.civitascore.configadapter.configuration.AdapterConfig;
import de.civitascore.configadapter.flowable.bpmn.BpmnProcessDeployer;
import de.civitascore.configadapter.flowable.common.FlowableEngineFactory;
import de.civitascore.configadapter.flowable.common.SagaHandlerRegistry;
import de.civitascore.configadapter.flowable.common.kafka.FlowableResultPublisher;
import de.civitascore.configadapter.frost.FrostSagaHandler;
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
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DatasetCreateFlowableIT {

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

  private ProcessEngine processEngine;
  private HttpServer apisixMock;
  private List<String> apisixRequestPaths;
  private FlowableResultPublisher resultPublisher;
  private Client httpClient;
  private String frostBaseUrl;

  @BeforeEach
  void setUp() throws IOException {
    frostBaseUrl =
        "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + "/FROST-Server/v1.1";
    httpClient = ClientBuilder.newClient();
    waitForFrostReady();

    apisixRequestPaths = Collections.synchronizedList(new ArrayList<>());
    apisixMock = HttpServer.create(new InetSocketAddress(0), 0);
    apisixMock.createContext("/apisix/admin/", this::handleApisixRequest);
    apisixMock.start();
    String apisixMockUrl = "http://localhost:" + apisixMock.getAddress().getPort();

    FrostSagaHandler frostHandler = new FrostSagaHandler();
    frostHandler.initialize(
        mapConfig(
            Map.of(
                "frost.url", frostBaseUrl,
                "frost.api.key", "test-api-key",
                "frost.api.key.header", "X-API-Key")));

    ApisixSagaHandler apisixHandler = new ApisixSagaHandler();
    apisixHandler.initialize(
        mapConfig(
            Map.of(
                "apisix.admin.url", apisixMockUrl,
                "apisix.admin.key", "test-key",
                // Required since #1368 — the handler fails fast without these.
                "apisix.api.host", "api.test.local",
                "apisix.api.public.url", "http://api.test.local",
                "apisix.plugin.config.id", "test-plugin-config",
                "apisix.service.id", "svc-frost-server",
                "apisix.frost.api.key", "test-frost-upstream-key")));

    SagaHandlerRegistry registry = new SagaHandlerRegistry();
    registry.register(frostHandler);
    registry.register(apisixHandler);

    resultPublisher = mock(FlowableResultPublisher.class);

    processEngine =
        FlowableEngineFactory.createWithH2(
            Map.of("sagaHandlerRegistry", registry, "resultPublisher", resultPublisher));
  }

  @AfterEach
  void tearDown() {
    if (processEngine != null) processEngine.close();
    if (apisixMock != null) apisixMock.stop(0);
    if (resultPublisher != null) resultPublisher.close();
    if (httpClient != null) httpClient.close();
  }

  @AfterAll
  static void tearDownNetwork() {
    network.close();
  }

  @Test
  void datasetCreateSaga_completesWithRealFrostAndMockApisix() {
    deployProcesses();
    String datasetId = "ds-flowable-e2e-" + UUID.randomUUID().toString().substring(0, 8);

    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-flowable-" + datasetId);
    variables.put("datasetId", datasetId);
    variables.put("datasetName", "Flowable E2E Test Dataset");
    variables.put("description", "Integration test via Flowable");
    variables.put("hasPipelines", false);
    variables.put("datasources", List.of());
    variables.put("dataPipelines", List.of());
    // Per-NamedApi route model (#1311/#1379): CREATE_ROUTE provisions one route per named-API
    // slug and NO route (and no upstream) without one.
    variables.put("namedApis", List.of(Map.of("slug", "sta", "standard", "STA", "version", "v1")));

    RuntimeService runtimeService = processEngine.getRuntimeService();
    ProcessInstance instance =
        runtimeService.startProcessInstanceByKey("dataset-create", variables);
    executeAllJobs();

    HistoryService historyService = processEngine.getHistoryService();
    var historic =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .finished()
            .singleResult();
    assertNotNull(historic, "Process should be completed");

    List<String> tasks =
        historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instance.getId())
            .activityType("serviceTask")
            .finished()
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list()
            .stream()
            .map(HistoricActivityInstance::getActivityId)
            .filter(id -> !id.startsWith("compensate-") && !id.startsWith("publish-"))
            .toList();
    assertEquals(
        List.of("create-project", "create-route"),
        tasks,
        "Should execute FROST then APISIX (no pipelines = skip Pipeline)");

    Object projectId =
        historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(instance.getId())
            .variableName("projectId")
            .singleResult()
            .getValue();
    assertNotNull(projectId, "projectId should be set by FROST handler");

    try (Response frostResponse =
        httpClient
            .target(frostBaseUrl)
            .path("Projects(" + projectId + ")")
            .request(MediaType.APPLICATION_JSON)
            .get()) {
      assertEquals(200, frostResponse.getStatus(), "FROST project should exist");
      String body = frostResponse.readEntity(String.class);
      assertTrue(body.contains("Flowable E2E Test Dataset"));
    }

    assertFalse(apisixRequestPaths.isEmpty(), "APISIX mock should have received requests");
    assertTrue(
        apisixRequestPaths.stream().anyMatch(p -> p.contains("/apisix/admin/upstreams/")),
        "Should have created upstream");
    assertTrue(
        apisixRequestPaths.stream().anyMatch(p -> p.contains("/apisix/admin/routes/")),
        "Should have created route");
  }

  @Test
  void datasetCreateSaga_compensatesOnApisixFailure() throws IOException {
    deployProcesses();
    apisixMock.stop(0);
    apisixMock = HttpServer.create(new InetSocketAddress(0), 0);
    apisixMock.createContext(
        "/apisix/admin/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          exchange.sendResponseHeaders(500, 0);
          exchange.close();
        });
    apisixMock.start();

    String datasetId = "ds-flowable-fail-" + UUID.randomUUID().toString().substring(0, 8);
    Map<String, Object> variables = new HashMap<>();
    variables.put("sagaId", "saga-fail-" + datasetId);
    variables.put("datasetId", datasetId);
    variables.put("datasetName", "Flowable Fail Test");
    variables.put("description", "Should compensate");
    variables.put("hasPipelines", false);
    variables.put("datasources", List.of());
    variables.put("dataPipelines", List.of());
    // Without a named API the APISIX step is a contract-mandated no-op (no route, no upstream),
    // so the 500-mock would never be hit and no compensation would run.
    variables.put("namedApis", List.of(Map.of("slug", "sta", "standard", "STA", "version", "v1")));

    ProcessInstance instance =
        processEngine.getRuntimeService().startProcessInstanceByKey("dataset-create", variables);
    executeAllJobs();

    var historic =
        processEngine
            .getHistoryService()
            .createHistoricProcessInstanceQuery()
            .processInstanceId(instance.getId())
            .finished()
            .singleResult();
    assertNotNull(historic, "Process should have finished after compensation");

    List<String> allTasks =
        processEngine
            .getHistoryService()
            .createHistoricActivityInstanceQuery()
            .processInstanceId(instance.getId())
            .activityType("serviceTask")
            .finished()
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list()
            .stream()
            .map(HistoricActivityInstance::getActivityId)
            .toList();
    assertTrue(
        allTasks.stream().anyMatch(t -> t.startsWith("compensate-")),
        "Should have executed a compensation task");
  }

  private void deployProcesses() {
    BpmnProcessDeployer.deploy(processEngine.getRepositoryService());
  }

  private void executeAllJobs() {
    var mgmt = processEngine.getManagementService();
    for (int i = 0; i < 100; i++) {
      List<Job> jobs = mgmt.createJobQuery().list();
      if (jobs.isEmpty()) break;
      for (Job job : jobs) {
        try {
          mgmt.executeJob(job.getId());
        } catch (Exception e) {
          // may already be executed
        }
      }
    }
  }

  private void handleApisixRequest(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    apisixRequestPaths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
    byte[] response = "{\"value\":{}}".getBytes();
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(response);
    }
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

  private AdapterConfig mapConfig(Map<String, String> props) {
    return new AdapterConfig() {
      @Override
      public String getProperty(String key) {
        return props.get(key);
      }

      @Override
      public String getProperty(String key, String defaultValue) {
        return props.getOrDefault(key, defaultValue);
      }
    };
  }
}

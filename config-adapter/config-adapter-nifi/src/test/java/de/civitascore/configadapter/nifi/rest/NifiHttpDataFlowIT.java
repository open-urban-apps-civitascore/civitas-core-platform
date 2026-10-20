/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.RuntimeMonitorProbe;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.SqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.rest.NifiRestClient.RuntimeStatus;
import de.civitascore.configadapter.nifi.rest.NifiRestClient.RuntimeStatus.State;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Runs a Pipeline from an HTTP/REST Data source through a cron node and a Mapping to a PostGIS Data
 * storage in a real NiFi, with WireMock as the API, and watches it with the runtime monitor.
 */
class NifiHttpDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18449;
  private static final String EVERY_SECOND = "* * * * * ?";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";
  private static final String DATASET_ID = "ds-1";

  private static Network network;
  private static GenericContainer<?> api;
  private static PostgreSQLContainer<?> postgres;
  private static WireMock wireMock;

  private RuntimeMonitorProbe monitor;

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi HTTP/REST data-flow IT");
    assumeTrue(
        extensionNar("nifi-http-nar").isPresent(),
        "HTTP NAR not built — skipping NiFi HTTP/REST data-flow IT; build nifi-extensions first");

    network = Network.newNetwork();

    api =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.WIREMOCK))
            .withNetwork(network)
            .withNetworkAliases("api")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200).forStatusCode(404));
    api.start();
    wireMock = new WireMock(api.getHost(), api.getMappedPort(8080));

    postgres =
        new PostgreSQLContainer<>(
                DockerImageName.parse(TestContainerImages.POSTGIS)
                    .asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName(DB)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);
    postgres.start();
    createTables();

    startNifi(
        HOST_PORT,
        network,
        container -> {
          container.withCopyFileToContainer(
              MountableFile.forHostPath(postgresDriverJar()), "/opt/nifi/drivers/postgresql.jar");
          installExtensionNars(container);
        });
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
    if (postgres != null) {
      postgres.stop();
    }
    if (api != null) {
      api.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @BeforeEach
  void startMonitor() {
    monitor = new RuntimeMonitorProbe(client);
  }

  @AfterEach
  void stopMonitor() {
    monitor.close();
  }

  @Test
  void jsonArrayGivesOneRowForEachElement() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/stations"))
            .willReturn(
                okJson("[{\"id\":\"s1\",\"name\":\"North\"},{\"id\":\"s2\",\"name\":\"South\"}]")));

    deploy("http-array", "/stations", "http_array");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_array").equals(List.of("North", "South")));
  }

  @Test
  void jsonObjectGivesOneRow() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/station")).willReturn(okJson("{\"id\":\"s1\",\"name\":\"North\"}")));

    deploy("http-object", "/station", "http_object");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_object").equals(List.of("North")));
  }

  @Test
  void emptyArrayGivesNoRowAndNoError() throws Exception {
    wireMock.register(get(urlPathEqualTo("/none")).willReturn(okJson("[]")));

    String processGroupId = deploy("http-empty", "/none", "http_empty");

    awaitRequests("/none", 3);
    RuntimeStatus status = client.readRuntimeStatus(processGroupId);
    assertTrue(status.healthy(), status.message());
    assertEquals(0, rowCount("http_empty"));
  }

  @Test
  void paginatedApiCallReadsEachPageUntilAShortPage() throws Exception {
    registerPage("/pages", 0, "[{\"id\":\"s1\",\"name\":\"A\"},{\"id\":\"s2\",\"name\":\"B\"}]");
    registerPage("/pages", 1, "[{\"id\":\"s3\",\"name\":\"C\"},{\"id\":\"s4\",\"name\":\"D\"}]");
    registerPage("/pages", 2, "[{\"id\":\"s5\",\"name\":\"E\"}]");

    deploy(
        "http-pages",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/pages",
              "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page",
                              "pageSizeParameter": "size", "pageSize": 2 } }
            """),
        "http_pages");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_pages").equals(List.of("A", "B", "C", "D", "E")));
    assertEquals(0, pageRequests("/pages", 3));
  }

  @Test
  void paginatedApiCallStopsAfterTheEndPage() throws Exception {
    registerPage("/end-page", 1, "[{\"id\":\"s1\",\"name\":\"A\"},{\"id\":\"s2\",\"name\":\"B\"}]");
    registerPage("/end-page", 2, "[{\"id\":\"s3\",\"name\":\"C\"},{\"id\":\"s4\",\"name\":\"D\"}]");
    registerPage("/end-page", 3, "[{\"id\":\"s5\",\"name\":\"E\"},{\"id\":\"s6\",\"name\":\"F\"}]");
    registerPage("/end-page", 4, "[{\"id\":\"s7\",\"name\":\"G\"},{\"id\":\"s8\",\"name\":\"H\"}]");

    deploy(
        "http-end-page",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/end-page",
              "pagination": { "type": "PAGE_NUMBER", "pageParameter": "page",
                              "pageSizeParameter": "size", "pageSize": 2,
                              "startPage": 1, "endPage": 3 } }
            """),
        "http_end_page");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_end_page").equals(List.of("A", "B", "C", "D", "E", "F")));
    assertEquals(0, pageRequests("/end-page", 4));
  }

  @Test
  void redirectStopsThePipelineAndIsNotFollowed() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/moved"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", "/target")));
    wireMock.register(get(urlPathEqualTo("/target")).willReturn(okJson("[]")));

    String processGroupId = deploy("http-redirect", "/moved", "http_redirect");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(
            () -> {
              RuntimeStatus status = client.readRuntimeStatus(processGroupId);
              return status.state() == State.STOPPED
                  && status.message().contains("failed with status 302");
            });
    assertEquals(0, wireMock.find(getRequestedFor(urlPathEqualTo("/target"))).size());
  }

  @Test
  void notFoundStopsTheProcessGroupAndPublishesStoppedWithTheCause() throws Exception {
    wireMock.register(get(urlPathEqualTo("/gone")).willReturn(aResponse().withStatus(404)));
    String pipelineId = UUID.randomUUID().toString();

    String processGroupId = deploy(pipelineId, "/gone", "http_gone");

    Map<String, Object> stopped = awaitEvent(pipelineId, processGroupId, "STOPPED");
    assertEquals("API call \"readings\": GET /gone failed with status 404", stopped.get("message"));
    assertEquals("", stopped.get("stacktrace"));
    assertEquals(DATASET_ID, stopped.get("datasetId"));
    assertTrue(
        stopSinkBulletinTimes(processGroupId).contains(stopped.get("occurredAt")),
        "occurredAt " + stopped.get("occurredAt") + " is the time of no bulletin of the stop sink");
    assertRequestsStay("/gone");
  }

  @Test
  void invalidRecordWritesNoRowAndStopsTheProcessGroup() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/invalid"))
            .willReturn(okJson("[{\"id\":\"s1\",\"name\":\"North\"},{\"id\":\"s2\",\"name\":7}]")));
    String pipelineId = UUID.randomUUID().toString();

    String processGroupId = deploy(pipelineId, "/invalid", "http_invalid");

    Map<String, Object> stopped = awaitEvent(pipelineId, processGroupId, "STOPPED");
    assertEquals(
        "API call \"readings\": 1 of 2 records violate the Data structure: record 1: /name type",
        stopped.get("message"));
    assertRequestsStay("/invalid");
    assertEquals(0, rowCount("http_invalid"));
  }

  @Test
  void temporaryErrorOfTheApiPublishesErrorAndKeepsTheProcessGroupRunning() throws Exception {
    wireMock.register(get(urlPathEqualTo("/unavailable")).willReturn(aResponse().withStatus(503)));
    String pipelineId = UUID.randomUUID().toString();

    String processGroupId = deploy(pipelineId, "/unavailable", "http_unavailable");

    Map<String, Object> error = awaitEvent(pipelineId, processGroupId, "ERROR");
    assertTrue(
        ((String) error.get("message")).contains("failed with status 503"),
        String.valueOf(error.get("message")));
    awaitRequests("/unavailable", requests("/unavailable") + 3);
    assertTrue(
        monitor.poll(pipelineId).stream()
            .noneMatch(event -> "STOPPED".equals(event.get("status"))));
  }

  @Test
  void runLongerThanTheCronIntervalStartsNoSecondRun() throws Exception {
    Duration delay = Duration.ofSeconds(3);
    Duration window = Duration.ofSeconds(15);
    wireMock.register(
        get(urlPathEqualTo("/slow"))
            .willReturn(okJson("[]").withFixedDelay((int) delay.toMillis())));

    deploy("http-slow", "/slow", "http_slow");

    awaitRequests("/slow", 1);
    int before = requests("/slow");
    // A negative assertion: the requests that a second run would add can only show over time.
    Thread.sleep(window.toMillis());
    int during = requests("/slow") - before;
    assertTrue(
        during <= window.dividedBy(delay) + 1,
        during + " requests in " + window + " with a response delay of " + delay);
  }

  private String deploy(String pipelineId, String path, String table) throws Exception {
    return deploy(pipelineId, Map.of("name", "readings", "method", "GET", "path", path), table);
  }

  private String deploy(String pipelineId, Map<String, Object> apiCall, String table)
      throws Exception {
    Datasource source = new Datasource();
    source.setId("ds-" + pipelineId);
    source.setType("http");
    source.handleUnknownProperty("element", "el-1");
    source.handleUnknownProperty("baseUrl", "http://api:8080");
    source.handleUnknownProperty("apiCalls", List.of(apiCall));
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "c", "kind": "cron", "cronExpression": "%s" },
                { "id": "src", "kind": "source", "sourceRef": "src-1", "apiCallName": "readings" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e0", "source": "s", "target": "c" },
                { "id": "e1", "source": "c", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """
                .formatted(EVERY_SECOND));
    Map<String, Map<String, Object>> artifacts =
        NifiTestFixtures.artifacts(
            """
            { "map-1": { "fields": { "$.id": "$.id", "$.name": "$.name" } },
              "el-1": { "type": "object",
                "properties": { "id": { "type": "string" }, "name": { "type": "string" } } } }
            """);
    DeploymentPlan plan =
        NifiTestFixtures.planner(
                new CredentialResolver(new byte[0]),
                SqlSourceProbe.NO_OP,
                new PlatformSinkConfig(
                    "jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
                null)
            .plan(
                new PipelineDeploymentRequest(
                    pipelineId,
                    graph,
                    source,
                    new PostgisSinkSpec(table, List.of("id")),
                    artifacts));
    return client.deployFlow(plan);
  }

  private Map<String, Object> awaitEvent(String pipelineId, String processGroupId, String status) {
    monitor.register(pipelineId, DATASET_ID, processGroupId);
    return await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .until(
            () ->
                monitor.poll(pipelineId).stream()
                    .filter(event -> status.equals(event.get("status")))
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private static List<String> stopSinkBulletinTimes(String processGroupId) throws Exception {
    List<String> times = new ArrayList<>();
    for (JsonNode entry : client.readBulletins()) {
      JsonNode bulletin = entry.path("bulletin");
      if (processGroupId.equals(bulletin.path("groupId").asText())
          && NifiRestClient.STOP_SINK.equals(bulletin.path("sourceName").asText())) {
        times.add(bulletin.path("timestampIso").asText());
      }
    }
    return times;
  }

  private static void assertRequestsStay(String path) throws InterruptedException {
    int before = requests(path);
    // A negative assertion: a request of a process group that still runs can only show over time.
    Thread.sleep(Duration.ofSeconds(5).toMillis());
    assertEquals(before, requests(path), "requests to " + path + " after the stop");
  }

  private static void awaitRequests(String path, int count) {
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> requests(path) >= count);
  }

  /** Registers the response of one page; a request for a page without a stub gets a 404. */
  private static void registerPage(String path, int page, String body) {
    wireMock.register(
        get(urlPathEqualTo(path))
            .withQueryParam("page", equalTo(String.valueOf(page)))
            .withQueryParam("size", equalTo("2"))
            .willReturn(okJson(body)));
  }

  private static int pageRequests(String path, int page) {
    return wireMock
        .find(
            getRequestedFor(urlPathEqualTo(path))
                .withQueryParam("page", equalTo(String.valueOf(page))))
        .size();
  }

  private static int requests(String path) {
    return wireMock.find(getRequestedFor(urlPathEqualTo(path))).size();
  }

  private static List<String> names(String table) throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT name FROM " + table + " ORDER BY id")) {
      List<String> names = new ArrayList<>();
      while (rs.next()) {
        names.add(rs.getString(1));
      }
      return names;
    }
  }

  private static int rowCount(String table) throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  private static void createTables() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement()) {
      for (String table :
          List.of(
              "http_array",
              "http_object",
              "http_empty",
              "http_redirect",
              "http_gone",
              "http_invalid",
              "http_pages",
              "http_end_page",
              "http_unavailable",
              "http_slow")) {
        st.execute("CREATE TABLE " + table + " (id text PRIMARY KEY, name text)");
      }
    }
  }

  private static Connection dbConnection() throws Exception {
    return DriverManager.getConnection(postgres.getJdbcUrl(), DB_USER, DB_PASSWORD);
  }

  private static String postgresDriverJar() {
    try {
      return new File(Driver.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .getAbsolutePath();
    } catch (Exception e) {
      throw new IllegalStateException("cannot locate the postgresql driver jar", e);
    }
  }
}

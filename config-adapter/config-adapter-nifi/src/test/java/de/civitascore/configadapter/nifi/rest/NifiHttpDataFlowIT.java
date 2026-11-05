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
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.encrypted;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.stretchedKey;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.Driver;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Runs a Pipeline from an HTTP/REST Data source through a cron node and a Mapping to a PostGIS Data
 * storage in a real NiFi, with WireMock as the API, and watches it with the runtime monitor.
 *
 * <p>The authenticated cases need https. A test CA signs the certificates of both API hosts and of
 * the client certificate, and NiFi trusts it as a JVM default CA, the source the Pipeline uses.
 */
class NifiHttpDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18449;
  private static final String EVERY_SECOND = "* * * * * ?";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";
  private static final String DATASET_ID = "ds-1";
  private static final String TLS_BASE_URL = "https://api:8443";
  private static final String MUTUAL_TLS_BASE_URL = "https://api-mtls:8443";
  private static final String ROWS = "[{\"id\":\"s1\",\"name\":\"North\"}]";
  // WireMock reads a client-certificate truststore only with this password.
  private static final String STORE_PASSWORD = "password";
  private static final String WIREMOCK_TLS = "/home/wiremock/tls/";

  /** Each element of a response array is one record. */
  private static final String ARRAY_RECORDS =
      """
      { "map-1": { "fields": { "$.id": "$.id", "$.name": "$.name" } },
        "el-1": { "type": "object",
          "properties": { "id": { "type": "string" }, "name": { "type": "string" } } } }
      """;

  /**
   * A response is one record that holds the records under {@code data}, beside a cursor or a count;
   * the Mapping makes one row of each element of {@code data}.
   */
  private static final String OBJECT_RECORD =
      """
      { "map-1": { "fields": { "$.id": "$.data[].id", "$.name": "$.data[].name" } },
        "el-1": { "type": "object",
          "properties": {
            "data": { "type": "array", "items": { "type": "object",
              "properties": { "id": { "type": "string" }, "name": { "type": "string" } } } },
            "next_cursor": { "type": ["string", "null"] },
            "meta": { "type": "object", "properties": { "count": { "type": "integer" } } } } } }
      """;

  private static Network network;
  private static GenericContainer<?> api;
  private static GenericContainer<?> mutualTlsApi;
  private static PostgreSQLContainer<?> postgres;
  private static WireMock wireMock;
  private static WireMock mutualTlsWireMock;
  private static TestPki pki;

  private RuntimeMonitorProbe monitor;

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi HTTP/REST data-flow IT");
    assumeTrue(
        extensionNar("nifi-http-nar").isPresent(),
        "HTTP NAR not built — skipping NiFi HTTP/REST data-flow IT; build nifi-extensions first");

    network = Network.newNetwork();
    pki = TestPki.create();

    api = httpsWireMock("api");
    api.start();
    wireMock = new WireMock(api.getHost(), api.getMappedPort(8080));

    mutualTlsApi =
        httpsWireMock(
                "api-mtls",
                "--https-require-client-cert",
                "--https-truststore",
                WIREMOCK_TLS + "ca.p12",
                "--truststore-type",
                "PKCS12",
                "--truststore-password",
                STORE_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(pki.caTruststore(), 0644), WIREMOCK_TLS + "ca.p12");
    mutualTlsApi.start();
    mutualTlsWireMock = new WireMock(mutualTlsApi.getHost(), mutualTlsApi.getMappedPort(8080));

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
          // The JVM reads jssecacerts in place of cacerts, so the test CA is the one default CA.
          container.withCopyFileToContainer(
              MountableFile.forHostPath(pki.jvmDefaultTruststore(), 0644),
              javaHome(container) + "/lib/security/jssecacerts");
          installExtensionNars(container);
        });
  }

  /** A WireMock that answers over http on 8080 and over https on 8443. */
  private static GenericContainer<?> httpsWireMock(String alias, String... options) {
    List<String> command =
        new ArrayList<>(
            List.of(
                "--https-port",
                "8443",
                "--https-keystore",
                WIREMOCK_TLS + "server.p12",
                "--keystore-type",
                "PKCS12",
                "--keystore-password",
                STORE_PASSWORD,
                "--key-manager-password",
                STORE_PASSWORD));
    command.addAll(List.of(options));
    return new GenericContainer<>(DockerImageName.parse(TestContainerImages.WIREMOCK))
        .withNetwork(network)
        .withNetworkAliases(alias)
        .withExposedPorts(8080)
        .withCopyFileToContainer(
            MountableFile.forHostPath(pki.serverKeystore(), 0644), WIREMOCK_TLS + "server.p12")
        .withCommand(command.toArray(String[]::new))
        .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200).forStatusCode(404));
  }

  private static String javaHome(GenericContainer<?> container) {
    try {
      String image = container.getImage().get();
      for (String variable :
          DockerClientFactory.lazyClient().inspectImageCmd(image).exec().getConfig().getEnv()) {
        if (variable.startsWith("JAVA_HOME=")) {
          return variable.substring("JAVA_HOME=".length());
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("cannot inspect the NiFi image", e);
    }
    throw new IllegalStateException("the NiFi image sets no JAVA_HOME");
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
    if (mutualTlsApi != null) {
      mutualTlsApi.stop();
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
  void nextLinksReadTheLastResponseAndStopAtMaxLinks() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/next-link"))
            .willReturn(
                okJson(objectRecord(records("A", "B"), "\"next\":\"next-link-end?page=2\""))));
    wireMock.register(
        get(urlPathEqualTo("/next-link-end"))
            .withQueryParam("page", equalTo("2"))
            .willReturn(okJson(objectRecord(records("C"), "\"next\":\"/must-not-follow\""))));
    deploy(
        "http-next-link",
        map(
            """
        {"name":"readings","path":"/next-link",
         "pagination":{"type":"NEXT_LINK","nextLinkPath":"$.next","maxLinks":1}}
        """),
        OBJECT_RECORD,
        "http_next_link");
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_next_link").equals(List.of("A", "B", "C")));
    assertEquals(0, wireMock.find(getRequestedFor(urlPathEqualTo("/must-not-follow"))).size());
  }

  @Test
  void aFailedNextLinkLeavesNoRecords() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/next-link-failed"))
            .willReturn(okJson(objectRecord(records("A"), "\"next\":\"/next-link-missing\""))));
    wireMock.register(
        get(urlPathEqualTo("/next-link-missing")).willReturn(aResponse().withStatus(404)));
    String pipelineId = UUID.randomUUID().toString();
    String processGroupId =
        deploy(
            pipelineId,
            map(
                """
        {"name":"readings","path":"/next-link-failed",
         "pagination":{"type":"NEXT_LINK","nextLinkPath":"$.next"}}
        """),
            OBJECT_RECORD,
            "http_next_link_failed");
    Map<String, Object> stopped = awaitEvent(pipelineId, processGroupId, "STOPPED");
    assertEquals(
        "API call \"readings\": GET /next-link-failed request 2 failed with status 404",
        stopped.get("message"));
    assertTrue(names("http_next_link_failed").isEmpty());
  }

  @Test
  void cursorInTheQueryReadsEachPageUntilAResponseWithoutACursor() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/cursor-query"))
            .withQueryParam("limit", equalTo("2"))
            .withQueryParam("cursor", absent())
            .willReturn(okJson(objectRecord(records("A", "B"), "\"next_cursor\":\"abc123\""))));
    wireMock.register(
        get(urlPathEqualTo("/cursor-query"))
            .withQueryParam("limit", equalTo("2"))
            .withQueryParam("cursor", equalTo("abc123"))
            .willReturn(okJson(objectRecord(records("C", "D"), "\"next_cursor\":\"def456\""))));
    wireMock.register(
        get(urlPathEqualTo("/cursor-query"))
            .withQueryParam("limit", equalTo("2"))
            .withQueryParam("cursor", equalTo("def456"))
            .willReturn(okJson(objectRecord(records("E"), "\"next_cursor\":null"))));

    deploy(
        "http-cursor-query",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/cursor-query",
              "pagination": { "type": "CURSOR", "cursorPath": "$.next_cursor",
                              "cursorParameter": "cursor", "cursorIn": "QUERY",
                              "limitParameter": "limit", "limit": 2 } }
            """),
        OBJECT_RECORD,
        "http_cursor_query");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_cursor_query").equals(List.of("A", "B", "C", "D", "E")));
  }

  @Test
  void cursorInAHeaderReadsEachPageUntilAResponseWithoutACursor() throws Exception {
    wireMock.register(
        get(urlPathEqualTo("/cursor-header"))
            .withQueryParam("limit", absent())
            .withHeader("X-Cursor", absent())
            .willReturn(okJson(objectRecord(records("A", "B"), "\"next_cursor\":\"abc123\""))));
    wireMock.register(
        get(urlPathEqualTo("/cursor-header"))
            .withQueryParam("limit", absent())
            .withQueryParam("X-Cursor", absent())
            .withHeader("X-Cursor", equalTo("abc123"))
            .willReturn(okJson(objectRecord(records("C"), "\"next_cursor\":\"\""))));

    deploy(
        "http-cursor-header",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/cursor-header",
              "pagination": { "type": "CURSOR", "cursorPath": "$.next_cursor",
                              "cursorParameter": "X-Cursor", "cursorIn": "HEADER" } }
            """),
        OBJECT_RECORD,
        "http_cursor_header");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_cursor_header").equals(List.of("A", "B", "C")));
  }

  @Test
  void limitOffsetReadsEachPageUntilAPageWithFewerRecordsThanTheLimit() throws Exception {
    registerOffset("/offset-limit", 80, okJson(records("A", "B")));
    registerOffset("/offset-limit", 82, okJson(records("C", "D")));
    registerOffset("/offset-limit", 84, okJson(records("E")));

    deploy(
        "http-offset-limit",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/offset-limit",
              "pagination": { "type": "LIMIT_OFFSET", "offsetParameter": "offset", "offset": 80,
                              "limitParameter": "limit", "limit": 2 } }
            """),
        "http_offset_limit");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_offset_limit").equals(List.of("A", "B", "C", "D", "E")));
    assertEquals(0, offsetRequests("/offset-limit", 86));
  }

  @Test
  void offsetWithoutALimitReadsEachPageUntilAnEmptyPage() throws Exception {
    registerOffset("/offset", 0, okJson(records("A", "B")));
    registerOffset("/offset", 2, okJson(records("C")));
    registerOffset("/offset", 3, okJson("[]"));

    deploy(
        "http-offset",
        map(
            """
            { "name": "readings", "method": "GET", "path": "/offset",
              "pagination": { "type": "LIMIT_OFFSET", "offsetParameter": "offset" } }
            """),
        "http_offset");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_offset").equals(List.of("A", "B", "C")));
    assertEquals(0, offsetRequests("/offset", 4));
  }

  @Test
  void countPathCountsTheRecordsOfAResponseThatIsOneRecord() throws Exception {
    registerOffset("/offset-count", 0, okJson(objectRecord(records("A", "B"), count(2))));
    registerOffset("/offset-count", 2, okJson(objectRecord(records("C"), count(1))));
    registerOffset("/offset-count", 3, okJson(objectRecord(records(), count(0))));

    String processGroupId =
        deploy(
            "http-offset-count",
            map(
                """
                { "name": "readings", "method": "GET", "path": "/offset-count",
                  "pagination": { "type": "LIMIT_OFFSET", "offsetParameter": "offset",
                                  "countPath": "$.meta.count" } }
                """),
            OBJECT_RECORD,
            "http_offset_count");

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_offset_count").equals(List.of("A", "B", "C")));
    awaitOffsetRequests("/offset-count", 3, 3);
    assertEquals(0, offsetRequests("/offset-count", 4));
    RuntimeStatus status = client.readRuntimeStatus(processGroupId);
    assertTrue(status.healthy(), status.message());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void limitOffsetEndsTheRunAtAClientErrorAfterTheFirstRequest(int status) throws Exception {
    String path = "/offset-end-" + status;
    registerOffset(path, 0, okJson(records("A", "B")));
    registerOffset(path, 2, okJson(records("C", "D")));
    registerOffset(path, 4, aResponse().withStatus(status));

    String processGroupId =
        deploy(
            "http-offset-end-" + status,
            map(
                """
                { "name": "readings", "method": "GET", "path": "%s",
                  "pagination": { "type": "LIMIT_OFFSET", "offsetParameter": "offset",
                                  "limitParameter": "limit", "limit": 2 } }
                """
                    .formatted(path)),
            "http_offset_end_" + status);

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names("http_offset_end_" + status).equals(List.of("A", "B", "C", "D")));
    awaitOffsetRequests(path, 4, 3);
    RuntimeStatus runtimeStatus = client.readRuntimeStatus(processGroupId);
    assertTrue(runtimeStatus.healthy(), runtimeStatus.message());
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

  @ParameterizedTest
  @EnumSource(AuthenticationCase.class)
  void eachMethodReadsRows(AuthenticationCase authentication) throws Exception {
    String table = authentication.table("http_auth_");
    Credential credential = credential(authentication);
    credential
        .api()
        .register(
            credential
                .authenticated()
                .apply(get(urlPathEqualTo("/" + table)))
                .willReturn(okJson(ROWS)));

    deploy(UUID.randomUUID().toString(), credential, "/" + table, table);

    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> names(table).equals(List.of("North")));
  }

  @Test
  void refusedTokenRequestStopsThePipelineWithTheCause() throws Exception {
    wireMock.register(
        post(urlPathEqualTo("/token-refused"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"error\":\"invalid_client\",\"error_description\":\"unknown client\"}")));
    String pipelineId = UUID.randomUUID().toString();
    Credential credential =
        new Credential(
            wireMock,
            TLS_BASE_URL,
            Map.of(
                "type",
                "OAUTH2",
                "tokenUrl",
                TLS_BASE_URL + "/token-refused",
                "clientId",
                "pipeline",
                "clientSecret",
                encrypted("refused-client-secret")),
            List.of(),
            UnaryOperator.identity());

    String processGroupId =
        deploy(pipelineId, credential, "/token-refused-api", "http_token_refused");

    Map<String, Object> stopped = awaitEvent(pipelineId, processGroupId, "STOPPED");
    assertEquals(
        "API call \"readings\": OAuth2 token request to api:8443/token-refused failed with status"
            + " 401 (invalid_client)",
        stopped.get("message"));
    assertEquals(0, requests("/token-refused-api"));
  }

  @ParameterizedTest
  @EnumSource(AuthenticationCase.class)
  void uniqueSecretsAppearInNoFlowExportBulletinOrCause(AuthenticationCase authentication)
      throws Exception {
    String table = authentication.table("http_refused_");
    Credential credential = credential(authentication);
    credential
        .api()
        .register(get(urlPathEqualTo("/" + table)).willReturn(aResponse().withStatus(401)));
    String pipelineId = UUID.randomUUID().toString();

    String processGroupId = deploy(pipelineId, credential, "/" + table, table);

    Map<String, Object> stopped = awaitEvent(pipelineId, processGroupId, "STOPPED");
    assertEquals(
        "API call \"readings\": GET /"
            + table
            + " failed with status 401 ("
            + authentication.method
            + ")",
        stopped.get("message"));
    String export = flowExport(processGroupId);
    String bulletins = client.readBulletins().toString();
    String log = nifi.execInContainer("cat", "logs/nifi-app.log").getStdout();
    for (String secret : credential.secrets()) {
      assertFalse(export.contains(secret), "the flow export holds a secret");
      assertFalse(bulletins.contains(secret), "a bulletin holds a secret");
      assertFalse(String.valueOf(stopped).contains(secret), "the cause holds a secret");
      assertFalse(log.contains(secret), "the NiFi log holds a secret");
    }
    if (authentication == AuthenticationCase.CLIENT_CERTIFICATE) {
      assertTrue(export.contains(pki.clientCertificateLine()), "the export holds the certificate");
    }
  }

  private String deploy(String pipelineId, Credential credential, String path, String table)
      throws Exception {
    return deploy(
        pipelineId,
        credential.baseUrl(),
        credential.authentication(),
        apiCall(path),
        ARRAY_RECORDS,
        table);
  }

  private String deploy(String pipelineId, String path, String table) throws Exception {
    return deploy(pipelineId, apiCall(path), table);
  }

  private String deploy(String pipelineId, Map<String, Object> apiCall, String table)
      throws Exception {
    return deploy(pipelineId, apiCall, ARRAY_RECORDS, table);
  }

  private String deploy(
      String pipelineId, Map<String, Object> apiCall, String records, String table)
      throws Exception {
    return deploy(pipelineId, "http://api:8080", Map.of(), apiCall, records, table);
  }

  /**
   * @param records the Mapping {@code map-1} and the Data structure {@code el-1} of the records
   */
  private String deploy(
      String pipelineId,
      String baseUrl,
      Map<String, Object> authentication,
      Map<String, Object> apiCall,
      String records,
      String table)
      throws Exception {
    Datasource source = new Datasource();
    source.setId("ds-" + pipelineId);
    source.setType("http");
    source.handleUnknownProperty("element", "el-1");
    source.handleUnknownProperty("baseUrl", baseUrl);
    source.handleUnknownProperty("authentication", authentication);
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
    Map<String, Map<String, Object>> artifacts = NifiTestFixtures.artifacts(records);
    DeploymentPlan plan =
        NifiTestFixtures.planner(
                new CredentialResolver(stretchedKey()),
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

  private static Map<String, Object> apiCall(String path) {
    return Map.of("name", "readings", "method", "GET", "path", path);
  }

  private static Credential credential(AuthenticationCase authentication) throws Exception {
    String unique = UUID.randomUUID().toString();
    return switch (authentication) {
      case BEARER_TOKEN -> {
        String token = "bearer-token-" + unique;
        yield new Credential(
            wireMock,
            TLS_BASE_URL,
            Map.of("type", "BEARER_TOKEN", "token", encrypted(token)),
            List.of(token),
            request -> request.withHeader("Authorization", equalTo("Bearer " + token)));
      }
      case API_TOKEN_IN_HEADER -> {
        String value = "header-token-" + unique;
        yield new Credential(
            wireMock,
            TLS_BASE_URL,
            Map.of(
                "type",
                "API_TOKEN",
                "name",
                "X-Api-Key",
                "in",
                "HEADER",
                "value",
                encrypted(value)),
            List.of(value),
            request -> request.withHeader("X-Api-Key", equalTo(value)));
      }
      case API_TOKEN_IN_QUERY -> {
        String value = "query-token-" + unique;
        // The space and the separators reach the API intact only through percent-encoding.
        String sent = value + " &=";
        yield new Credential(
            wireMock,
            TLS_BASE_URL,
            Map.of("type", "API_TOKEN", "name", "api_key", "in", "QUERY", "value", encrypted(sent)),
            List.of(value),
            request -> request.withQueryParam("api_key", equalTo(sent)));
      }
      case OAUTH2 -> {
        String clientSecret = "client-secret-" + unique;
        String accessToken = "access-token-" + UUID.randomUUID();
        String tokenPath = "/token/" + UUID.randomUUID();
        wireMock.register(
            post(urlPathEqualTo(tokenPath))
                .withBasicAuth("pipeline", clientSecret)
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(equalTo("grant_type=client_credentials&scope=read"))
                .willReturn(
                    okJson(
                        "{\"access_token\":\""
                            + accessToken
                            + "\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        yield new Credential(
            wireMock,
            TLS_BASE_URL,
            Map.of(
                "type", "OAUTH2",
                "tokenUrl", TLS_BASE_URL + tokenPath,
                "clientId", "pipeline",
                "clientSecret", encrypted(clientSecret),
                "scope", "read"),
            List.of(clientSecret, accessToken),
            request -> request.withHeader("Authorization", equalTo("Bearer " + accessToken)));
      }
      case CLIENT_CERTIFICATE ->
          new Credential(
              mutualTlsWireMock,
              MUTUAL_TLS_BASE_URL,
              Map.of(
                  "type", "CLIENT_CERTIFICATE",
                  "certificate", encrypted(pki.clientCertificateChain()),
                  "privateKey", encrypted(pki.clientPrivateKey())),
              List.of(pki.clientPrivateKeyLine()),
              UnaryOperator.identity());
    };
  }

  private static String flowExport(String processGroupId) throws Exception {
    Request request =
        new Request.Builder()
            .url(
                "https://"
                    + dockerHost
                    + ":"
                    + HOST_PORT
                    + "/nifi-api/process-groups/"
                    + processGroupId
                    + "/download?includeReferencedServices=true")
            .header("Authorization", "Bearer " + client.authenticate())
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      String body = response.body().string();
      assertEquals(200, response.code(), body);
      return body;
    }
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

  /** Registers the response to one offset; a request for an offset without a stub gets a 404. */
  private static void registerOffset(String path, long offset, ResponseDefinitionBuilder response) {
    wireMock.register(
        get(urlPathEqualTo(path))
            .withQueryParam("offset", equalTo(String.valueOf(offset)))
            .willReturn(response));
  }

  private static int offsetRequests(String path, long offset) {
    return wireMock
        .find(
            getRequestedFor(urlPathEqualTo(path))
                .withQueryParam("offset", equalTo(String.valueOf(offset))))
        .size();
  }

  private static void awaitOffsetRequests(String path, long offset, int count) {
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(1))
        .until(() -> offsetRequests(path, offset) >= count);
  }

  /** A JSON array with one record for each name, whose id is its name. */
  private static String records(String... names) {
    return Stream.of(names)
        .map("{\"id\":\"%1$s\",\"name\":\"%1$s\"}"::formatted)
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String objectRecord(String records, String member) {
    return "{\"data\":" + records + "," + member + "}";
  }

  private static String count(int count) {
    return "\"meta\":{\"count\":" + count + "}";
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
              "http_next_link",
              "http_next_link_failed",
              "http_cursor_query",
              "http_cursor_header",
              "http_offset_limit",
              "http_offset",
              "http_offset_count",
              "http_offset_end_400",
              "http_offset_end_404",
              "http_unavailable",
              "http_slow",
              "http_token_refused")) {
        st.execute("CREATE TABLE " + table + " (id text PRIMARY KEY, name text)");
      }
      for (AuthenticationCase authentication : AuthenticationCase.values()) {
        for (String prefix : List.of("http_auth_", "http_refused_")) {
          st.execute(
              "CREATE TABLE " + authentication.table(prefix) + " (id text PRIMARY KEY, name text)");
        }
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

  /** One authentication method of an HTTP Data source, with the name its causes give it. */
  private enum AuthenticationCase {
    BEARER_TOKEN("Bearer Token"),
    API_TOKEN_IN_HEADER("API Token"),
    API_TOKEN_IN_QUERY("API Token"),
    OAUTH2("OAuth2"),
    CLIENT_CERTIFICATE("Client certificate");

    private final String method;

    AuthenticationCase(String method) {
      this.method = method;
    }

    String table(String prefix) {
      return prefix + name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * The {@code authentication} of a Data source with its unique secrets and the request match of
   * the API host that only a request with these credentials passes.
   */
  private record Credential(
      WireMock api,
      String baseUrl,
      Map<String, Object> authentication,
      List<String> secrets,
      UnaryOperator<MappingBuilder> authenticated) {}

  /** A test CA, the certificate of both API hosts it signed, and a client certificate it signed. */
  private record TestPki(
      Path serverKeystore,
      Path caTruststore,
      Path jvmDefaultTruststore,
      String clientCertificateChain,
      String clientPrivateKey) {

    private static final String KEYTOOL = System.getProperty("java.home") + "/bin/keytool";

    static TestPki create() throws Exception {
      Path dir = Files.createTempDirectory("http-auth-pki");
      Path ca = dir.resolve("ca-key.p12");
      Path caCertificate = dir.resolve("ca.crt");
      keytool(
          ca,
          "-genkeypair",
          "-alias",
          "ca",
          "-keyalg",
          "EC",
          "-dname",
          "CN=Test CA",
          "-ext",
          "bc:c");
      keytool(ca, "-exportcert", "-alias", "ca", "-rfc", "-file", caCertificate.toString());

      Path server = dir.resolve("server.p12");
      keytool(server, "-genkeypair", "-alias", "server", "-keyalg", "EC", "-dname", "CN=api");
      Path serverCertificate = sign(ca, server, "server", "-ext", "SAN=dns:api,dns:api-mtls");
      keytool(
          server, "-importcert", "-alias", "ca", "-noprompt", "-file", caCertificate.toString());
      keytool(server, "-importcert", "-alias", "server", "-file", serverCertificate.toString());

      Path client = dir.resolve("client.p12");
      keytool(client, "-genkeypair", "-alias", "client", "-keyalg", "RSA", "-dname", "CN=pipeline");
      Path clientCertificate = sign(ca, client, "client");

      Path caTruststore = dir.resolve("ca.p12");
      keytool(
          caTruststore,
          "-importcert",
          "-alias",
          "ca",
          "-noprompt",
          "-file",
          caCertificate.toString());
      Path jvmDefaultTruststore = dir.resolve("jssecacerts");
      // The JVM opens its default truststore without a password, so nothing in it is encrypted.
      runKeytool(
          KEYTOOL,
          "-J-Dkeystore.pkcs12.certProtectionAlgorithm=NONE",
          "-J-Dkeystore.pkcs12.macAlgorithm=NONE",
          "-importcert",
          "-alias",
          "civitas-http-test-ca",
          "-noprompt",
          "-file",
          caCertificate.toString(),
          "-storetype",
          "PKCS12",
          "-keystore",
          jvmDefaultTruststore.toString(),
          "-storepass",
          STORE_PASSWORD);

      return new TestPki(
          server,
          caTruststore,
          jvmDefaultTruststore,
          Files.readString(clientCertificate, StandardCharsets.US_ASCII)
              + Files.readString(caCertificate, StandardCharsets.US_ASCII),
          "-----BEGIN PRIVATE KEY-----\n"
              + Base64.getMimeEncoder(64, new byte[] {'\n'})
                  .encodeToString(privateKey(client, "client"))
              + "\n-----END PRIVATE KEY-----\n");
    }

    /** The first line of the PEM body, unique to this key. */
    String clientPrivateKeyLine() {
      return clientPrivateKey.lines().skip(1).findFirst().orElseThrow();
    }

    /** The first line of the PEM body of the client certificate. */
    String clientCertificateLine() {
      return clientCertificateChain.lines().skip(1).findFirst().orElseThrow();
    }

    private static Path sign(Path ca, Path store, String alias, String... extensions)
        throws Exception {
      Path request = store.resolveSibling(alias + ".csr");
      Path certificate = store.resolveSibling(alias + ".crt");
      keytool(store, "-certreq", "-alias", alias, "-file", request.toString());
      List<String> arguments =
          new ArrayList<>(
              List.of(
                  "-gencert",
                  "-alias",
                  "ca",
                  "-rfc",
                  "-infile",
                  request.toString(),
                  "-outfile",
                  certificate.toString()));
      arguments.addAll(List.of(extensions));
      keytool(ca, arguments.toArray(String[]::new));
      return certificate;
    }

    private static byte[] privateKey(Path store, String alias) throws Exception {
      KeyStore keys = KeyStore.getInstance("PKCS12");
      try (InputStream in = Files.newInputStream(store)) {
        keys.load(in, STORE_PASSWORD.toCharArray());
      }
      return keys.getKey(alias, STORE_PASSWORD.toCharArray()).getEncoded();
    }

    private static void keytool(Path store, String... arguments) throws Exception {
      List<String> command = new ArrayList<>(List.of(KEYTOOL));
      command.addAll(List.of(arguments));
      command.addAll(
          List.of(
              "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", STORE_PASSWORD));
      runKeytool(command.toArray(String[]::new));
    }
  }
}

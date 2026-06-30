/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.rest;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import java.io.File;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Cross-adapter DATA-CORRECTNESS integration test for the SQL pull source: deploys the real
 * QueryDatabaseTableRecord→mapping→PutDatabaseRecord flow built by {@link NifiFlowBuilder} onto an
 * actual NiFi 2.9.0 that READS rows from one PostgreSQL table and WRITES them into another. Closes
 * the "it deploys" vs "data actually flows" gap and proves two SQL-specific behaviours end to end:
 *
 * <ul>
 *   <li>typed read+write: rows are read from a SQL table, mapped, and written with type coercion
 *   <li>the geoPoint op building a real PostGIS geometry from scalar lon/lat SQL columns
 * </ul>
 *
 * <p>The source DB password is supplied encrypted and pushed via the sensitive-property path, so
 * this also exercises credential decryption + the post-upload sensitive push for a SQL source. A
 * fast per-second cron ({@code * * * * * ?}) drives the source so rows land within the test window.
 * Skipped when Docker is unavailable.
 */
class NifiSqlDataFlowIT {

  private static final int HOST_PORT = 18446;
  private static final String USER = "admin";
  private static final String PASSWORD = "ctsNiFiTestPassword123";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";
  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String CRON_EVERY_SECOND = "* * * * * ?";

  private static Network network;
  private static PostgreSQLContainer<?> postgres;
  private static FixedHostPortGenericContainer<?> nifi;
  private static Client httpClient;
  private static NifiRestClient client;
  private static String dockerHost;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping NiFi/SQL data-flow IT");

    dockerHost = DockerClientFactory.instance().dockerHostIpAddress();
    network = Network.newNetwork();

    postgres =
        new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                    .asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName(DB)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);
    postgres.start();
    seed();

    nifi =
        new FixedHostPortGenericContainer<>("apache/nifi:2.9.0")
            .withFixedExposedPort(HOST_PORT, 8443)
            .withNetwork(network)
            .withEnv("SINGLE_USER_CREDENTIALS_USERNAME", USER)
            .withEnv("SINGLE_USER_CREDENTIALS_PASSWORD", PASSWORD)
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv(
                "NIFI_WEB_PROXY_HOST", dockerHost + ":" + HOST_PORT + ",localhost:" + HOST_PORT)
            .withCopyFileToContainer(
                MountableFile.forHostPath(postgresDriverJar()), "/opt/nifi/drivers/postgresql.jar")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    nifi.start();

    httpClient =
        ClientBuilder.newBuilder()
            .sslContext(trustAll())
            .hostnameVerifier((host, session) -> true)
            .build();
    client =
        new NifiRestClient("https://" + dockerHost + ":" + HOST_PORT, USER, PASSWORD, httpClient);

    await()
        .atMost(Duration.ofMinutes(3))
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .until(
            () -> {
              client.authenticate();
              return true;
            });
  }

  @AfterAll
  static void stopStack() {
    if (httpClient != null) {
      httpClient.close();
    }
    if (nifi != null) {
      nifi.stop();
    }
    if (postgres != null) {
      postgres.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @Test
  void readsTypedRowFromSqlSource() throws Exception {
    // mapping copies the columns through; temperature is text "42" the int sink column must coerce
    // —
    // proving the row was actually READ from SQL, mapped and written. (Duplicate prevention on
    // repeated reads is a sink-side concern — PutDatabaseRecord UPSERT on the target primary key —
    // not covered here.)
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "c", "type": "cron", "data": { "cronExpression": "%s" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.temperature": "$.temperature" } } } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "m" },
                { "id": "e3", "source": "m", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("sensor_input");
    // a cron-scheduled SQL source requires a sink primary key (dedup of re-read rows)
    deploy(
        "sql-typed-it",
        graph,
        source,
        new SinkSpec(SinkType.POSTGIS, "observation", List.of("stationid")));

    // the row must arrive…
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> count("SELECT count(*) FROM observation WHERE stationid = 'S1'") >= 1);
    assertTypedRow();
  }

  @Test
  void buildsGeometryFromGeoPointOverSqlSource() throws Exception {
    // the geoPoint op assembles a PostGIS Point from two scalar SQL columns and writes it into a
    // geometry column — the same geometry path the MQTT IT covers, but sourced from SQL
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "c", "type": "cron", "data": { "cronExpression": "%s" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid",
                                "$.geom": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" } } } } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "m" },
                { "id": "e3", "source": "m", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("geo_input");
    deploy(
        "sql-geo-it",
        graph,
        source,
        new SinkSpec(SinkType.POSTGIS, "geo_observation", List.of("stationid")));

    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(this::geometryRowLanded);
  }

  @Test
  void upsertDeduplicatesRowsOnRepeatedReads() throws Exception {
    // with a primary key on the sink, PutDatabaseRecord UPSERTs — the per-second cron re-reads the
    // single source row every tick, but the sink must hold exactly ONE row, not one per tick.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "c", "type": "cron", "data": { "cronExpression": "%s" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.temperature": "$.temperature" } } } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "m" },
                { "id": "e3", "source": "m", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("sensor_input");
    deploy(
        "sql-dedup-it",
        graph,
        source,
        new SinkSpec(SinkType.POSTGIS, "dedup_observation", List.of("stationid")));

    // the row lands…
    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(() -> count("SELECT count(*) FROM dedup_observation WHERE stationid = 'S1'") >= 1);

    // …and stays a single row across several more cron ticks (UPSERT, not duplicate INSERTs)
    Thread.sleep(Duration.ofSeconds(8).toMillis());
    assertEquals(
        1,
        count("SELECT count(*) FROM dedup_observation WHERE stationid = 'S1'"),
        "UPSERT on the primary key must keep exactly one row despite repeated reads");
  }

  private void deploy(
      String pipelineId, Map<String, Object> graph, Datasource source, SinkSpec sink)
      throws Exception {
    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    FlowDeploymentPlanner planner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(key),
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(new PipelineDeploymentRequest(pipelineId, graph, source, sink));
    client.deployFlow(plan);
  }

  private Datasource sqlSource(String table) throws Exception {
    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    String encryptedPassword =
        "ENC("
            + CredentialEncryptor.encrypt(
                DB_PASSWORD, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";
    Datasource source = new Datasource();
    source.setId("ds-" + table);
    source.setType("SQL");
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://postgres:5432/" + DB);
    source.handleUnknownProperty("user", DB_USER);
    source.handleUnknownProperty("password", encryptedPassword);
    source.handleUnknownProperty("table", table);
    source.handleUnknownProperty("columns", List.of("*"));
    return source;
  }

  private void assertTypedRow() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT stationid, temperature FROM observation WHERE stationid = 'S1'")) {
      rs.next();
      assertEquals("S1", rs.getString("stationid"));
      assertEquals(42, rs.getInt("temperature"));
    }
  }

  private boolean geometryRowLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT ST_X(geom) AS x, ST_Y(geom) AS y, ST_SRID(geom) AS srid"
                    + " FROM geo_observation WHERE stationid = 'G1'")) {
      if (!rs.next()) {
        return false;
      }
      assertEquals(8.4, rs.getDouble("x"), 1e-9);
      assertEquals(49.0, rs.getDouble("y"), 1e-9);
      assertEquals(4326, rs.getInt("srid"));
      return true;
    }
  }

  private int count(String sql) throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }

  private static void seed() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement()) {
      st.execute("CREATE TABLE sensor_input (id bigint, stationid text, temperature text)");
      st.execute("INSERT INTO sensor_input (id, stationid, temperature) VALUES (1, 'S1', '42')");
      st.execute("CREATE TABLE observation (stationid text PRIMARY KEY, temperature integer)");
      st.execute(
          "CREATE TABLE dedup_observation (stationid text PRIMARY KEY, temperature integer)");

      st.execute("CREATE TABLE geo_input (id bigint, stationid text, lon text, lat text)");
      st.execute("INSERT INTO geo_input (id, stationid, lon, lat) VALUES (1, 'G1', '8.4', '49.0')");
      st.execute(
          "CREATE TABLE geo_observation (stationid text PRIMARY KEY, geom geometry(Point,4326))");
    }
  }

  private static Connection dbConnection() throws Exception {
    return DriverManager.getConnection(postgres.getJdbcUrl(), DB_USER, DB_PASSWORD);
  }

  private static String postgresDriverJar() {
    try {
      return new File(
              org.postgresql.Driver.class
                  .getProtectionDomain()
                  .getCodeSource()
                  .getLocation()
                  .toURI())
          .getAbsolutePath();
    } catch (Exception e) {
      throw new IllegalStateException("cannot locate the postgresql driver jar", e);
    }
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private static SSLContext trustAll() {
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              @Override
              public void checkClientTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public void checkServerTrusted(X509Certificate[] chain, String authType) {}

              @Override
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }
            }
          },
          new SecureRandom());
      return ctx;
    } catch (Exception e) {
      throw new IllegalStateException("cannot build trust-all SSL context", e);
    }
  }
}

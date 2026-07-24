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

import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.PlatformSinkConfig;
import de.civitascore.configadapter.nifi.flow.SqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
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
class NifiSqlDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18446;
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";
  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String CRON_EVERY_SECOND = "* * * * * ?";

  private static Network network;
  private static PostgreSQLContainer<?> postgres;

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/SQL data-flow IT");

    network = Network.newNetwork();

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
    seed();

    startNifi(
        HOST_PORT,
        network,
        container ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(postgresDriverJar()),
                "/opt/nifi/drivers/postgresql.jar"));
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
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
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.temperature": "$.temperature" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("sensor_input");
    // a cron-scheduled SQL source requires a sink primary key (dedup of re-read rows)
    deploy("sql-typed-it", graph, source, new PostgisSinkSpec("observation", List.of("stationid")));

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
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid",
                                "$.geom": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" } } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("geo_input");
    deploy(
        "sql-geo-it", graph, source, new PostgisSinkSpec("geo_observation", List.of("stationid")));

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
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.temperature": "$.temperature" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    Datasource source = sqlSource("sensor_input");
    deploy(
        "sql-dedup-it",
        graph,
        source,
        new PostgisSinkSpec("dedup_observation", List.of("stationid")));

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

  @Test
  void upsertsCamelCaseColumnsAgainstQuotedIdentifiers() throws Exception {
    // the sink table uses quoted camelCase identifiers ("stationId"/"tempValue"), the normal UML
    // style. The UPSERT ON CONFLICT key must resolve against the quoted identifier, not a
    // lowercase-folded one — this is the case the earlier all-lowercase tests could not surface.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "c", "type": "cron", "data": { "cronExpression": "%s" } },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationId": "$.stationId", "$.tempValue": "$.tempValue" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

    deploy(
        "sql-mixedcase-it",
        graph,
        sqlSource("mixed_input"),
        new PostgisSinkSpec("mixedObservation", List.of("stationId")));

    await()
        .atMost(Duration.ofSeconds(90))
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .until(
            () ->
                count("SELECT count(*) FROM \"mixedObservation\" WHERE \"stationId\" = 'M1'") >= 1);

    Thread.sleep(Duration.ofSeconds(8).toMillis());
    assertEquals(
        1,
        count("SELECT count(*) FROM \"mixedObservation\" WHERE \"stationId\" = 'M1'"),
        "UPSERT on a quoted camelCase primary key must keep exactly one row across repeated reads");
  }

  private void deploy(
      String pipelineId, Map<String, Object> graph, Datasource source, SinkSpec sink)
      throws Exception {
    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FlowDeploymentPlanner planner =
          NifiTestFixtures.planner(
              resolver,
              SqlSourceProbe.NO_OP,
              new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
              null);
      DeploymentPlan plan =
          planner.plan(new PipelineDeploymentRequest(pipelineId, graph, source, sink));
      client.deployFlow(plan);
    }
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

      // Mixed-case source and sink with QUOTED (case-preserving) identifiers, exactly as the
      // PostGIS
      // adapter's DDL emits them. This exercises the UPSERT ON CONFLICT path against a camelCase
      // key
      // — the normal UML style — which an unquoted PutDatabaseRecord would fold to lowercase and
      // fail on.
      st.execute("CREATE TABLE mixed_input (id bigint, \"stationId\" text, \"tempValue\" text)");
      st.execute(
          "INSERT INTO mixed_input (id, \"stationId\", \"tempValue\") VALUES (1, 'M1', '42')");
      st.execute(
          "CREATE TABLE \"mixedObservation\" (\"stationId\" text PRIMARY KEY, \"tempValue\""
              + " integer)");
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
}

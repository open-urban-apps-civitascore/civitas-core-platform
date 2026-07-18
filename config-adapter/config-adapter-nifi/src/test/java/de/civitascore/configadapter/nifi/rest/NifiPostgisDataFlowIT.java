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
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Cross-adapter DATA-CORRECTNESS integration test for the POSTGIS sink: deploys the real
 * MQTT→mapping→PutDatabaseRecord flow built by {@link NifiFlowBuilder} onto an actual NiFi 2.9.0
 * writing into a real PostgreSQL table, publishes a known MQTT message, and asserts the row that
 * lands in the DB is correctly TYPED — proving {@code PutDatabaseRecord} coerces a string field to
 * an {@code int} column at runtime (the typing the NiFi adapter intentionally delegates to the
 * sink, since the PostGIS adapter owns the typed table DDL).
 *
 * <p>Topology (one Docker network): Mosquitto ({@code mqtt}) → NiFi → PostgreSQL ({@code
 * postgres}). The PostgreSQL JDBC driver is mounted into the NiFi container at the path the DBCP
 * fragment expects ({@code /opt/nifi/drivers/postgresql.jar}). Skipped when Docker is unavailable.
 */
class NifiPostgisDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18445;
  private static final String TOPIC = "civitas/it/postgis";
  private static final String ARRAY_TOPIC = "civitas/it/postgis-array";
  private static final String GEO_TOPIC = "civitas/it/postgis-geo";
  private static final String GEO_25832_TOPIC = "civitas/it/postgis-geo-25832";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static PostgreSQLContainer<?> postgres;

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/PostGIS data-flow IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

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
    createTable();

    // the DBCP fragment loads the JDBC driver from this path
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
    if (mosquitto != null) {
      mosquitto.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @Test
  void deployedFlowWritesTypedRowToPostgres() throws Exception {
    // mapping copies the source fields through; count arrives as the string "42", meta is a nested
    // object that must land in the jsonb column
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.count": "$.count",
                                "$.meta": "$.meta" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("ds-pg-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(TOPIC));

    FlowDeploymentPlanner planner =
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "pg-data-it", graph, source, new PostgisSinkSpec("observation")));

    client.deployFlow(plan);

    // publish a known message and wait until the typed row lands in PostgreSQL
    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl)) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(
                    TOPIC,
                    "{\"stationid\":\"S1\",\"count\":\"42\",\"meta\":{\"a\":1,\"b\":\"x\"}}");
                return rowLanded();
              });
    }
  }

  @Test
  void deployedFlowMapsFieldsWithinEachArrayElement() throws Exception {
    // CORE uses [] for an all-elements path. The compiler must emit NiFi's [*] selector and a
    // relative source path, so each item receives its own sourceName rather than the complete
    // multi-value selection.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid",
                                "$.items[].name": "$.items[].sourceName" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("ds-pg-array-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(ARRAY_TOPIC));

    FlowDeploymentPlanner planner =
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "pg-array-it", graph, source, new PostgisSinkSpec("array_observation")));

    client.deployFlow(plan);

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl)) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(
                    ARRAY_TOPIC,
                    "{\"stationid\":\"S4\",\"items\":["
                        + "{\"sourceName\":\"A\",\"name\":null},"
                        + "{\"sourceName\":\"B\",\"name\":null}]}");
                return arrayRowLanded();
              });
    }
  }

  @Test
  void deployedFlowWritesGeometryFromGeoPoint() throws Exception {
    // The geoPoint op builds a Point from two scalar coordinate fields. For a PostGIS sink it must
    // compile to WKT (POINT(lon lat)) that PutDatabaseRecord binds into the geometry column —
    // exercising the sink-dependent geometry path AND the stringtype=unspecified DBCP fix end to
    // end
    // on real NiFi + PostGIS.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid",
                                "$.geom": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" } } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("ds-pg-geo-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(GEO_TOPIC));

    FlowDeploymentPlanner planner =
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "pg-geo-it", graph, source, new PostgisSinkSpec("geo_observation")));

    client.deployFlow(plan);

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl)) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(
                    GEO_TOPIC, "{\"stationid\":\"S2\",\"lon\":\"8.4\",\"lat\":\"49.0\"}");
                return geometryRowLanded();
              });
    }
  }

  @Test
  void deployedFlowWritesGeometryToNon4326Column() throws Exception {
    // The platform's typical CRS is EPSG:25832. geoPoint emits a SRID-less POINT(lon lat); the
    // geometry(Point,25832) column stamps its own SRID on insert — this proves the non-4326 path
    // end to end (the scenario RecordPathCompiler's "no SRID prefix would clash with a non-4326
    // column" comment exists for), not just the 4326 default.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid",
                                "$.geom": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" } } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("ds-pg-geo25832-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(GEO_25832_TOPIC));

    FlowDeploymentPlanner planner =
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "pg-geo25832-it", graph, source, new PostgisSinkSpec("geo_observation_25832")));

    client.deployFlow(plan);

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl)) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                // 25832-style easting/northing in metres (the values are stamped, not reprojected)
                publisher.publish(
                    GEO_25832_TOPIC,
                    "{\"stationid\":\"S3\",\"lon\":\"500000.0\",\"lat\":\"5400000.0\"}");
                return geometry25832RowLanded();
              });
    }
  }

  /** Asserts the geoPoint landed in the 25832 column with the column SRID stamped onto it. */
  private boolean geometry25832RowLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT ST_X(geom) AS x, ST_Y(geom) AS y, ST_SRID(geom) AS srid"
                    + " FROM geo_observation_25832 WHERE stationid = 'S3'")) {
      if (!rs.next()) {
        return false;
      }
      assertEquals(500000.0, rs.getDouble("x"), 1e-6);
      assertEquals(5400000.0, rs.getDouble("y"), 1e-6);
      // the WKT carried no SRID; the geometry(Point,25832) column stamps 25832 — the platform's CRS
      assertEquals(25832, rs.getInt("srid"));
      return true;
    }
  }

  /** Queries PostgreSQL and asserts the row is present with the int column correctly coerced. */
  private boolean rowLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT stationid, count, meta FROM observation WHERE stationid = 'S1'")) {
      if (!rs.next()) {
        return false;
      }
      assertEquals("S1", rs.getString("stationid"));
      // the source sent the string "42"; PutDatabaseRecord must have coerced it to the int column
      assertEquals(42, rs.getInt("count"));
      // the nested object must have landed in the jsonb column with its structure intact
      var meta = mapper.readTree(rs.getString("meta"));
      assertEquals(1, meta.get("a").asInt());
      assertEquals("x", meta.get("b").asText());
      return true;
    }
  }

  /** Asserts that UpdateRecord evaluated the source relative to each selected array element. */
  private boolean arrayRowLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery("SELECT items FROM array_observation WHERE stationid = 'S4'")) {
      if (!rs.next()) {
        return false;
      }
      var items = mapper.readTree(rs.getString("items"));
      assertEquals("A", items.get(0).get("name").asText());
      assertEquals("B", items.get(1).get("name").asText());
      return true;
    }
  }

  /**
   * Asserts the geoPoint landed as a real PostGIS Point with the column's SRID and right coords.
   */
  private boolean geometryRowLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT ST_X(geom) AS x, ST_Y(geom) AS y, ST_SRID(geom) AS srid"
                    + " FROM geo_observation WHERE stationid = 'S2'")) {
      if (!rs.next()) {
        return false;
      }
      assertEquals(8.4, rs.getDouble("x"), 1e-9);
      assertEquals(49.0, rs.getDouble("y"), 1e-9);
      // the WKT carried no SRID; the geometry(Point,4326) column stamps 4326 on insert
      assertEquals(4326, rs.getInt("srid"));
      return true;
    }
  }

  private static void createTable() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement()) {
      st.execute("CREATE EXTENSION IF NOT EXISTS postgis");
      st.execute("CREATE TABLE observation (stationid text, count integer, meta jsonb)");
      st.execute("CREATE TABLE array_observation (stationid text, items jsonb)");
      st.execute("CREATE TABLE geo_observation (stationid text, geom geometry(Point,4326))");
      st.execute("CREATE TABLE geo_observation_25832 (stationid text, geom geometry(Point,25832))");
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

  /** Minimal retained-message MQTT publisher. */
  private static final class MqttPublisher implements AutoCloseable {
    private final MqttClient mqtt;

    MqttPublisher(String brokerUrl) throws Exception {
      mqtt = new MqttClient(brokerUrl, "civitas-pg-it-publisher", new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      mqtt.connect(options);
    }

    void publish(String topic, String payload) throws Exception {
      MqttMessage message =
          new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      message.setQos(1);
      message.setRetained(true);
      mqtt.publish(topic, message);
    }

    @Override
    public void close() throws Exception {
      if (mqtt.isConnected()) {
        mqtt.disconnect();
      }
      mqtt.close();
    }
  }
}

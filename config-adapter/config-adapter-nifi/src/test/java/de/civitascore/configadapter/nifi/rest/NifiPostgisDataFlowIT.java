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
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final String FANOUT_TOPIC = "civitas/it/postgis-fanout";
  private static final String UPSERT_FANOUT_TOPIC = "civitas/it/postgis-fanout-upsert";
  private static final String COLLISION_FANOUT_TOPIC = "civitas/it/postgis-fanout-collision";
  private static final String SPARSE_FANOUT_TOPIC = "civitas/it/postgis-fanout-sparse";
  private static final String GEO_FANOUT_TOPIC = "civitas/it/postgis-fanout-geo";
  private static final String CHAINED_FANOUT_TOPIC = "civitas/it/postgis-fanout-chained";
  private static final String GEO_TOPIC = "civitas/it/postgis-geo";
  private static final String GEO_25832_TOPIC = "civitas/it/postgis-geo-25832";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";

  private static final AtomicInteger PUBLISHER_SEQUENCE = new AtomicInteger();

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static PostgreSQLContainer<?> postgres;

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/PostGIS data-flow IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

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

    Datasource source = mqttSource("ds-pg-it", TOPIC);

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

    Datasource source = mqttSource("ds-pg-array-it", ARRAY_TOPIC);

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
  void deployedFlowWritesOneRowPerSourceArrayElement() throws Exception {
    // An array source mapped onto a flat target must fan out — one row per element — with the
    // parent-level fields repeated on every row.
    Map<String, Object> graph =
        fanoutGraph(
            """
            { "$.stationid": "$.stationid",
              "$.measured_at": "$.measurements[].ts",
              "$.value": "$.measurements[].value" }
            """);

    deployAndPublishOnce(
        "ds-pg-fanout-it",
        "pg-fanout-it",
        FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("fanout_observation"),
        "{\"stationid\":\"S5\",\"measurements\":["
            + "{\"ts\":\"2026-01-01T00:00:00Z\",\"value\":1},"
            + "{\"ts\":\"2026-01-01T00:15:00Z\",\"value\":2},"
            + "{\"ts\":\"2026-01-01T00:30:00Z\",\"value\":3}]}",
        publisher -> fanoutRowsLanded());
  }

  /** Builds a single-mapping-node graph; every fan-out scenario differs only in the field map. */
  private Map<String, Object> fanoutGraph(String fields) throws Exception {
    return map(
        """
        { "nodes": [
            { "id": "s", "type": "start", "data": {} },
            { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "m", "type": "mapping", "data": { "mappingConfig": { "fields": %s } } },
            { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
            { "id": "e", "type": "end", "data": {} } ],
          "edges": [
            { "id": "e1", "source": "s", "target": "src" },
            { "id": "e2", "source": "src", "target": "m" },
            { "id": "e3", "source": "m", "target": "k" },
            { "id": "e4", "source": "k", "target": "e" } ] }
        """
            .formatted(fields));
  }

  @Test
  void redeliveredFanoutPayloadDoesNotDuplicateOrCollapseRows() throws Exception {
    // The highest-risk correctness case. After a fan-out the parent-level stationid repeats across
    // rows, so the primary key must include an element-level column. Keyed on (stationid,
    // measured_at) a redelivery must UPSERT the same 3 rows — not append 3 more (INSERT semantics),
    // and not collapse them into 1 (which a parent-only key would do, silently losing readings).
    Map<String, Object> graph =
        fanoutGraph(
            """
            { "$.stationid": "$.stationid",
              "$.measured_at": "$.measurements[].ts",
              "$.value": "$.measurements[].value" }
            """);
    String payload =
        "{\"stationid\":\"S10\",\"measurements\":["
            + "{\"ts\":\"2026-03-01T00:00:00Z\",\"value\":1},"
            + "{\"ts\":\"2026-03-01T00:15:00Z\",\"value\":2},"
            + "{\"ts\":\"2026-03-01T00:30:00Z\",\"value\":3}]}";

    deployAndPublishOnce(
        "ds-pg-upsert-it",
        "pg-upsert-it",
        UPSERT_FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("upsert_fanout_observation", List.of("stationid", "measured_at")),
        payload,
        publisher -> {
          if (rowCount("upsert_fanout_observation", "stationid = 'S10'") < 3) {
            return false;
          }
          // deliver the identical payload again; the assertion after the poll checks the outcome.
          // Asserting inside the predicate would be swallowed by ignoreExceptions() and retried
          // until the timeout, turning a real mismatch into a bare ConditionTimeoutException.
          publisher.publishOnce(UPSERT_FANOUT_TOPIC, payload);
          Thread.sleep(Duration.ofSeconds(6).toMillis());
          return true;
        });

    assertEquals(
        3,
        rowCount("upsert_fanout_observation", "stationid = 'S10'"),
        "a redelivery must upsert the same rows, neither duplicate nor collapse them");
  }

  @Test
  void childFieldWinsOverAParentFieldOfTheSameName() throws Exception {
    // ForkRecord's Include Parent Fields is documented to let the child win on a name clash. That
    // claim is load-bearing here: if the precedence were reversed, every row would silently carry
    // the parent's id instead of the element's.
    Map<String, Object> graph =
        fanoutGraph(
            """
            { "$.stationid": "$.stationid",
              "$.id": "$.measurements[].id" }
            """);

    deployAndPublishOnce(
        "ds-pg-collision-it",
        "pg-collision-it",
        COLLISION_FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("collision_fanout_observation"),
        "{\"stationid\":\"S12\",\"id\":\"PARENT\",\"measurements\":["
            + "{\"id\":\"CHILD-A\"},{\"id\":\"CHILD-B\"}]}",
        publisher -> {
          if (rowCount("collision_fanout_observation", "stationid = 'S12'") < 2) {
            return false;
          }
          assertEquals(
              2,
              rowCount("collision_fanout_observation", "stationid = 'S12' AND id LIKE 'CHILD-%'"),
              "the element's id must win over the identically named parent field");
          return true;
        });
  }

  @Test
  void nullAndSparseElementsDoNotBreakTheFlow() throws Exception {
    // The reader infers its schema from the first document, so a null element or one missing a
    // mapped field is the case most likely to break at runtime rather than at compile time. The
    // well-formed elements must still land and the flow must stay alive — proven by a later
    // payload.
    Map<String, Object> graph =
        fanoutGraph(
            """
            { "$.stationid": "$.stationid",
              "$.measured_at": "$.measurements[].ts" }
            """);

    deployAndPublishOnce(
        "ds-pg-sparse-it",
        "pg-sparse-it",
        SPARSE_FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("sparse_fanout_observation"),
        "{\"stationid\":\"S13\",\"measurements\":["
            + "{\"ts\":\"2026-04-01T00:00:00Z\"},null,{\"other\":\"x\"}]}",
        publisher -> {
          if (rowCount("sparse_fanout_observation", "stationid = 'S13' AND measured_at IS NOT NULL")
              < 1) {
            return false;
          }
          // the flow must still be running afterwards
          publisher.publishOnce(
              SPARSE_FANOUT_TOPIC,
              "{\"stationid\":\"S14\",\"measurements\":[{\"ts\":\"2026-04-02T00:00:00Z\"}]}");
          await()
              .atMost(Duration.ofSeconds(60))
              .pollInterval(Duration.ofSeconds(3))
              .ignoreExceptions()
              .until(() -> rowCount("sparse_fanout_observation", "stationid = 'S14'") >= 1);
          return true;
        });
  }

  @Test
  void geoPointBuiltFromForkedElementCoordinates() throws Exception {
    // geoPoint renders a concat over two nested copies. A rewrite that handles a top-level CopyNode
    // but not the copies inside GeoPointNode would emit POINT(null null), or broadcast the first
    // element's coordinates onto every row — distinct per-row geometries prove neither happened.
    Map<String, Object> graph =
        fanoutGraph(
            """
            { "$.stationid": "$.stationid",
              "$.measured_at": "$.measurements[].ts",
              "$.geom": { "op": "geoPoint", "lon": "$.measurements[].lon",
                          "lat": "$.measurements[].lat" } }
            """);

    deployAndPublishOnce(
        "ds-pg-geofan-it",
        "pg-geofan-it",
        GEO_FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("geo_fanout_observation"),
        "{\"stationid\":\"S17\",\"measurements\":["
            + "{\"ts\":\"2026-07-01T00:00:00Z\",\"lon\":8.1,\"lat\":49.1},"
            + "{\"ts\":\"2026-07-01T00:15:00Z\",\"lon\":8.2,\"lat\":49.2}]}",
        publisher -> {
          if (rowCount("geo_fanout_observation", "stationid = 'S17' AND geom IS NOT NULL") < 2) {
            return false;
          }
          try (Connection c = dbConnection();
              Statement st = c.createStatement();
              ResultSet rs =
                  st.executeQuery(
                      "SELECT ST_X(geom) AS x FROM geo_fanout_observation"
                          + " WHERE stationid = 'S17' ORDER BY x")) {
            List<Double> xs = new ArrayList<>();
            while (rs.next()) {
              xs.add(rs.getDouble("x"));
            }
            assertEquals(List.of(8.1, 8.2), xs, "each row's geometry must use its own element");
          }
          return true;
        });
  }

  @Test
  void secondMappingNodeSeesThePostForkRecordShape() throws Exception {
    // Two mapping nodes in sequence: the first forks, so the second one's paths must resolve
    // against
    // the already-forked (flat) record. A second fork would double the fan-out.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m1", "type": "mapping", "data": { "mappingConfig": { "fields": {
                    "$.stationid": "$.stationid",
                    "$.measured_at": "$.measurements[].ts" } } } },
                { "id": "m2", "type": "mapping", "data": { "mappingConfig": { "fields": {
                    "$.stationid": "$.stationid" } } } },
                { "id": "k", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m1" },
                { "id": "e3", "source": "m1", "target": "m2" },
                { "id": "e4", "source": "m2", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """);

    deployAndPublishOnce(
        "ds-pg-chained-it",
        "pg-chained-it",
        CHAINED_FANOUT_TOPIC,
        graph,
        new PostgisSinkSpec("chained_fanout_observation"),
        "{\"stationid\":\"S18\",\"measurements\":["
            + "{\"ts\":\"2026-08-01T00:00:00Z\"},{\"ts\":\"2026-08-01T00:15:00Z\"}]}",
        publisher -> rowCount("chained_fanout_observation", "stationid = 'S18'") >= 2);

    // Asserted after the poll, not inside it: a predicate that waits for '>= 2' and then asserts
    // '== 2' can never see the over-fork it exists to catch, since the extra rows only make the
    // guard pass sooner. Settling first and then counting does.
    Thread.sleep(Duration.ofSeconds(6).toMillis());
    assertEquals(
        2,
        rowCount("chained_fanout_observation", "stationid = 'S18'"),
        "a second mapping node must not fork the array again");
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

    Datasource source = mqttSource("ds-pg-geo-it", GEO_TOPIC);

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

    Datasource source = mqttSource("ds-pg-geo25832-it", GEO_25832_TOPIC);

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

  /**
   * A payload-driven poll step: republishes and reports whether the expected outcome has landed.
   * Republishing on every attempt is what the existing tests do — ConsumeMQTT may not be scheduled
   * yet when the first message is sent.
   */
  private interface PollStep {
    boolean check(MqttPublisher publisher) throws Exception;
  }

  /**
   * Deploys and delivers the payload exactly once <em>into the flow</em>, then polls the assertion.
   * Required wherever the expected row count is exact: republishing on every poll attempt would
   * grow the table each time, which turns any count into a lower bound and lets a cross product
   * pass.
   */
  private void deployAndPublishOnce(
      String sourceId,
      String pipelineId,
      String topic,
      Map<String, Object> graph,
      PostgisSinkSpec sinkSpec,
      String payload,
      PollStep step)
      throws Exception {
    Datasource source = mqttSource(sourceId, topic);

    FlowDeploymentPlanner planner =
        NifiTestFixtures.planner(
            new CredentialResolver(new byte[0]),
            SqlSourceProbe.NO_OP,
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(new PipelineDeploymentRequest(pipelineId, graph, source, sinkSpec));

    client.deployFlow(plan);

    // Retention is not an option here: ConsumeMQTT re-receives a retained message on every
    // resubscribe, so one publish would keep producing rows for the whole poll window and inflate
    // every exact-count assertion. The trade-off is that a non-retained publish is dropped by the
    // broker while no subscriber exists, and deployFlow only waits for NiFi to report the processor
    // RUNNING, which precedes the MQTT CONNECT/SUBSCRIBE — so a delivery can be lost outright.
    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl)) {
      publisher.publishOnce(topic, payload);
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(() -> step.check(publisher));
    }
  }

  /**
   * An MQTT datasource for one flow. Every flow needs its OWN client id: the fragment ships a fixed
   * one, and a broker evicts the existing connection whenever a second client presents the same id
   * — so shared ids make concurrently deployed flows knock each other offline.
   */
  private static Datasource mqttSource(String sourceId, String topic) {
    Datasource source = new Datasource();
    source.setId(sourceId);
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(topic));
    source.handleUnknownProperty("client_id", sourceId);
    return source;
  }

  private static int rowCount(String table, String where) throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table + " WHERE " + where)) {
      return rs.next() ? rs.getInt(1) : 0;
    }
  }

  /**
   * Asserts EXACTLY one row per source array element from a single delivery, each carrying the
   * parent-level station id and its own timestamp. The exact count is the point: a cross product
   * over two array contexts, or a broadcast of one element's values onto all rows, both produce a
   * row count that a lower-bound check would accept.
   */
  private boolean fanoutRowsLanded() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement();
        ResultSet rs =
            st.executeQuery(
                "SELECT measured_at, value FROM fanout_observation"
                    + " WHERE stationid = 'S5' ORDER BY value")) {
      List<Integer> values = new ArrayList<>();
      List<String> timestamps = new ArrayList<>();
      while (rs.next()) {
        values.add(rs.getInt("value"));
        timestamps.add(rs.getString("measured_at"));
      }
      if (values.size() < 3) {
        return false;
      }
      assertEquals(List.of(1, 2, 3), values, "one row per element, no cross product");
      // distinct timestamps prove each row took its own element's value rather than a broadcast
      assertEquals(
          List.of("2026-01-01T00:00:00Z", "2026-01-01T00:15:00Z", "2026-01-01T00:30:00Z"),
          timestamps);
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
      // Fan-out targets are FLAT: one row per source array element, parent fields repeated.
      st.execute(
          "CREATE TABLE fanout_observation (stationid text, measured_at text, value integer)");
      // A real PRIMARY KEY so PutDatabaseRecord runs in UPSERT mode: after a fan-out the
      // parent-level stationid repeats across rows, so a parent-only key would collapse them.
      st.execute(
          "CREATE TABLE upsert_fanout_observation ("
              + "stationid text, measured_at text, value integer,"
              + " PRIMARY KEY (stationid, measured_at))");
      st.execute("CREATE TABLE collision_fanout_observation (id text, stationid text)");
      st.execute("CREATE TABLE sparse_fanout_observation (stationid text, measured_at text)");
      st.execute(
          "CREATE TABLE geo_fanout_observation ("
              + "stationid text, geom geometry(Point,4326), measured_at text)");
      st.execute("CREATE TABLE chained_fanout_observation (stationid text, measured_at text)");
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
      // A per-instance id: the broker evicts the older connection when a second client presents an
      // id already in use, so a shared one would let overlapping publishers disconnect each other.
      mqtt =
          new MqttClient(
              brokerUrl,
              "civitas-pg-it-publisher-" + PUBLISHER_SEQUENCE.incrementAndGet(),
              new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      mqtt.connect(options);
    }

    void publish(String topic, String payload) throws Exception {
      publish(topic, payload, true);
    }

    /**
     * Publishes without retention, for scenarios asserting an exact row count. A retained message
     * is redelivered on every ConsumeMQTT resubscribe, so it would keep producing rows for the
     * whole poll window — the values stay correct but the count becomes a function of test
     * duration.
     */
    void publishOnce(String topic, String payload) throws Exception {
      publish(topic, payload, false);
    }

    private void publish(String topic, String payload, boolean retained) throws Exception {
      MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
      message.setRetained(retained);
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

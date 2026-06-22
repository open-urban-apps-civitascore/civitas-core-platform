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
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
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
class NifiPostgisDataFlowIT {

  private static final int HOST_PORT = 18445;
  private static final String USER = "admin";
  private static final String PASSWORD = "ctsNiFiTestPassword123";
  private static final String TOPIC = "civitas/it/postgis";
  private static final String DB = "nifi_demo";
  private static final String DB_USER = "nifi";
  private static final String DB_PASSWORD = "nifi-db-secret";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static PostgreSQLContainer<?> postgres;
  private static FixedHostPortGenericContainer<?> nifi;
  private static Client httpClient;
  private static NifiRestClient client;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeAll
  static void startStack() throws Exception {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping NiFi/PostGIS data-flow IT");

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
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withDatabaseName(DB)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);
    postgres.start();
    createTable();

    nifi =
        new FixedHostPortGenericContainer<>("apache/nifi:2.9.0")
            .withFixedExposedPort(HOST_PORT, 8443)
            .withNetwork(network)
            .withEnv("SINGLE_USER_CREDENTIALS_USERNAME", USER)
            .withEnv("SINGLE_USER_CREDENTIALS_PASSWORD", PASSWORD)
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv("NIFI_WEB_PROXY_HOST", "localhost:" + HOST_PORT)
            // the DBCP fragment loads the driver from this path
            .withCopyFileToContainer(
                MountableFile.forHostPath(postgresDriverJar()), "/opt/nifi/drivers/postgresql.jar")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(5)));
    nifi.start();

    httpClient =
        ClientBuilder.newBuilder()
            .sslContext(trustAll())
            .hostnameVerifier((host, session) -> true)
            .build();
    client = new NifiRestClient("https://localhost:" + HOST_PORT, USER, PASSWORD, httpClient);

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
                { "id": "m", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.stationid": "$.stationid", "$.count": "$.count",
                                "$.meta": "$.meta" } } } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "m" },
                { "id": "e2", "source": "m", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("ds-pg-it");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(TOPIC));

    FlowDeploymentPlanner planner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(new byte[0]),
            new PlatformSinkConfig("jdbc:postgresql://postgres:5432/" + DB, DB_USER, DB_PASSWORD),
            null);
    DeploymentPlan plan =
        planner.plan(
            new PipelineDeploymentRequest(
                "pg-data-it", graph, source, new SinkSpec(SinkType.POSTGIS, "observation")));

    client.deployFlow(plan);

    // publish a known message and wait until the typed row lands in PostgreSQL
    String brokerUrl = "tcp://localhost:" + mosquitto.getMappedPort(1883);
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

  private static void createTable() throws Exception {
    try (Connection c = dbConnection();
        Statement st = c.createStatement()) {
      st.execute("CREATE TABLE observation (stationid text, count integer, meta jsonb)");
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

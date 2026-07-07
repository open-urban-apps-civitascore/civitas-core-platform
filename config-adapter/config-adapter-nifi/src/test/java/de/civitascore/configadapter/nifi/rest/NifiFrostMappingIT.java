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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.FlowDeploymentPlanner;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.SqlSourceProbe;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkSpec;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * DATA-CORRECTNESS integration test for the mapped FROST path (record mapping → generated STA
 * envelope template → find-or-create) against a <b>real FROST-Server</b>. Two pipelines are planned
 * through the real {@link FlowDeploymentPlanner} (so the {@code StaEnvelopeCompiler} output is what
 * deploys) and exercised end to end:
 *
 * <ul>
 *   <li><b>MQTT (non-STA payload)</b>: the envelope is rebuilt from mapped record fields — Thing
 *       find-or-create stays idempotent across re-deliveries, the observation lands typed on the
 *       pre-provisioned Datastream, and tenant values containing NiFi-EL/backreference syntax
 *       ({@code ${HOSTNAME}}, {@code $1}, {@code ${SINGLE_USER_CREDENTIALS_PASSWORD}}) arrive
 *       <i>literally</i> in FROST — the injection-hardening proof for the template path.
 *   <li><b>SQL (table rows)</b>: a multi-record batch is split into individual STA elements ({@code
 *       $[*]}), deduplicating the Thing across rows and re-reads.
 * </ul>
 *
 * <p>Failure paths must never drop silently: a missing source field renders a JSON {@code null}
 * result (valid envelope), an unmatched Datastream routes to the error sink without creating
 * anything, and a FROST-rejected observation (bad {@code phenomenonTime}) never becomes an
 * observation while the flow keeps running. Skipped when Docker is unavailable.
 */
class NifiFrostMappingIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18448;
  private static final String FROST_PATH = "/FROST-Server/v1.1";
  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String CRON_EVERY_SECOND = "* * * * * ?";

  private static final String TOPIC = "civitas/it/frost-mapping";

  // EL/backreference-shaped tenant values: they must arrive in FROST byte-identically, never
  // expanded against the NiFi environment or interpreted as a regex backreference. Both referenced
  // env vars ARE set in the container (HOSTNAME always; NIFI_SECURITY_USER_OIDC_CLIENT_SECRET holds
  // exactly the secret the assertion below forbids from leaking), so an expansion bug would be
  // caught here.
  private static final String INJECTION_NAME = "Station ${HOSTNAME} $1";
  private static final String INJECTION_DESCRIPTION =
      "unit ${NIFI_SECURITY_USER_OIDC_CLIENT_SECRET}";

  private static final String REF_MAP = "REF-MAP-1";
  private static final String DS_MAP = "DS-MAP-1";
  private static final String REF_NULL = "REF-NULL-1";
  private static final String DS_NULL = "DS-NULL-1";
  private static final String REF_NEVER = "REF-NEVER-1";
  private static final String DS_NEVER = "DS-NEVER-1";
  private static final String REF_BADTS = "REF-BADTS-1";
  private static final String DS_BADTS = "DS-BADTS-1";
  private static final String REF_SQL = "REF-SQL-1";
  private static final String DS_SQL = "DS-SQL-1";

  private static final String SRC_DB = "frost_src";
  private static final String SRC_USER = "reader";
  private static final String SRC_PASSWORD = "src-db-secret";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> frostDb;
  private static GenericContainer<?> frost;
  private static PostgreSQLContainer<?> srcdb;
  private static long projectId;
  private static long dsMapId;
  private static long dsNullId;
  private static long dsBadTsId;
  private static long dsSqlId;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  @SuppressWarnings("resource")
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/FROST mapping IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

    frostDb =
        new GenericContainer<>(DockerImageName.parse("postgis/postgis:16-3.4-alpine"))
            .withNetwork(network)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    frostDb.start();

    frost =
        new GenericContainer<>(DockerImageName.parse("hylkevds/frost-http-projects:latest"))
            .withNetwork(network)
            .withNetworkAliases("frost")
            .withExposedPorts(8080)
            .withEnv("serviceRootUrl", "http://frost:8080" + FROST_PATH + "/")
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
                Wait.forHttp(FROST_PATH + "/Things")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    frost.start();

    srcdb =
        new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:16-alpine").asCompatibleSubstituteFor("postgres"))
            .withNetwork(network)
            .withNetworkAliases("srcdb")
            .withDatabaseName(SRC_DB)
            .withUsername(SRC_USER)
            .withPassword(SRC_PASSWORD);
    srcdb.start();
    seedSourceDb();

    startNifi(
        HOST_PORT,
        network,
        container ->
            container.withCopyFileToContainer(
                MountableFile.forHostPath(postgresDriverJar()),
                "/opt/nifi/drivers/postgresql.jar"));

    projectId = createProject();
    dsMapId = createDatastream(DS_MAP, REF_MAP, "HOLDER-MAP");
    dsNullId = createDatastream(DS_NULL, REF_NULL, "HOLDER-NULL");
    dsBadTsId = createDatastream(DS_BADTS, REF_BADTS, "HOLDER-BADTS");
    dsSqlId = createDatastream(DS_SQL, REF_SQL, "HOLDER-SQL");

    deployMqttPipeline();
    deploySqlPipeline();
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
    for (GenericContainer<?> container : List.of(frost, frostDb, mosquitto)) {
      if (container != null) {
        container.stop();
      }
    }
    if (srcdb != null) {
      srcdb.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  // ─── MQTT → mapping → FROST ─────────────────────────────────────────────────

  @Test
  void mappedMqttPayloadCreatesThingAndTypedObservation() throws Exception {
    String payload = payload(INJECTION_NAME, REF_MAP, DS_MAP, "21.5", "\"2026-01-01T00:00:00Z\"");

    try (MqttPublisher publisher = publisher("civitas-it-map")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return countThings(REF_MAP) >= 1 && observations(dsMapId).size() >= 1;
              });

      // repeated delivery of the same message must never duplicate the Thing (find-or-create
      // idempotency holds through the rebuilt envelope, not just the raw one)
      for (int i = 0; i < 5; i++) {
        publisher.publish(TOPIC, payload);
        Thread.sleep(Duration.ofSeconds(2).toMillis());
      }
      assertEquals(1, countThings(REF_MAP), "re-delivered message must reuse the Thing");
    }

    // Injection hardening: the tenant-supplied name (data path) and const description (mapping
    // path) must arrive literally — no EL expansion, no $1 backreference, no leaked env secret.
    JsonNode thing = thingByReference(REF_MAP);
    assertEquals(INJECTION_NAME, thing.path("name").asText(), "EL in a data value must stay data");
    assertEquals(
        INJECTION_DESCRIPTION,
        thing.path("description").asText(),
        "EL in a const value must stay literal (\\$ escaped as \\$\\$)");
    assertFalse(
        thing.toString().contains(OIDC_CLIENT_SECRET),
        "the NiFi OIDC client secret must never leak into FROST");

    JsonNode observation = observations(dsMapId).get(0);
    assertTrue(observation.path("result").isNumber(), "toFloat result must serialize unquoted");
    assertEquals(21.5, observation.path("result").asDouble(), 1e-9);
    assertTrue(
        observation.path("phenomenonTime").asText().startsWith("2026-01-01T00:00:00"),
        "phenomenonTime must survive as the mapped ISO instant");
  }

  @Test
  void missingSourceFieldRendersJsonNullResult() throws Exception {
    // No 'temp' in the payload: the flat attribute is empty, the placeholder's isEmpty()/ifElse
    // branch must render result:null — a VALID envelope (null is correct semantics, not an error),
    // proven by the Thing and the observation both landing.
    String payload = payload("Null Station", REF_NULL, DS_NULL, null, "\"2026-01-03T00:00:00Z\"");

    try (MqttPublisher publisher = publisher("civitas-it-null")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return countThings(REF_NULL) >= 1 && observations(dsNullId).size() >= 1;
              });
    }
    assertTrue(
        observations(dsNullId).get(0).path("result").isNull(),
        "a missing source field must become a JSON null result");
  }

  @Test
  void unmatchedDatastreamIsRoutedToErrorSinkNotCreated() throws Exception {
    // DS_NEVER is never provisioned: the observation leg must route to the error sink — the
    // pipeline keeps running (the Thing leg still creates its Thing) and no Datastream appears.
    String payload =
        payload("Never Station", REF_NEVER, DS_NEVER, "9.9", "\"2026-01-04T00:00:00Z\"");

    try (MqttPublisher publisher = publisher("civitas-it-never")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return countThings(REF_NEVER) >= 1;
              });
    }
    assertEquals(
        0,
        countDatastreamsByFilter(REF_NEVER, DS_NEVER),
        "the pipeline must not create a Datastream for an unmatched reference");
  }

  @Test
  void invalidPhenomenonTimeNeverBecomesAnObservation() throws Exception {
    // 'not-a-date' renders a valid envelope (it is a string), FROST rejects the POST with 400 →
    // obsPost routes to the error sink. The Thing leg proves the message was fully processed.
    String payload = payload("BadTs Station", REF_BADTS, DS_BADTS, "12.3", "\"not-a-date\"");

    try (MqttPublisher publisher = publisher("civitas-it-badts")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return countThings(REF_BADTS) >= 1;
              });
      // give the observation leg ample time to (wrongly) post before asserting it never did
      Thread.sleep(Duration.ofSeconds(10).toMillis());
    }
    assertEquals(
        0,
        observations(dsBadTsId).size(),
        "a FROST-rejected observation must go to the error sink, not into the Datastream");
  }

  // ─── SQL → mapping → FROST ──────────────────────────────────────────────────

  @Test
  void sqlRowsAreSplitIntoIndividualStaElements() throws Exception {
    // Row 1 is seeded before deploy; the per-second cron re-reads the whole table each tick.
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(3))
        .ignoreExceptions()
        .until(() -> resultValues(dsSqlId).contains(30.5));
    assertEquals(1, countThings(REF_SQL), "the first row's Thing exists exactly once");

    // Row 2 (same reference) now makes every batch a 2-record array: its observation can only
    // appear if the $[*] split really turns the record-writer array into individual STA elements.
    try (Connection c = sourceDbConnection();
        Statement st = c.createStatement()) {
      st.execute(
          "INSERT INTO frost_input (id, station, ref, dsname, temp, ts) VALUES"
              + " (2, 'SQL Station', '"
              + REF_SQL
              + "', '"
              + DS_SQL
              + "', 31.5, '2026-01-02T01:00:00Z')");
    }
    await()
        .atMost(Duration.ofSeconds(120))
        .pollInterval(Duration.ofSeconds(3))
        .ignoreExceptions()
        .until(() -> resultValues(dsSqlId).contains(31.5));

    // both rows share the reference — the Thing must stay deduplicated across rows AND re-reads
    assertEquals(1, countThings(REF_SQL), "same-reference rows must share one Thing");
  }

  // ─── Deployment ─────────────────────────────────────────────────────────────

  /** The STA mapping every pipeline of this IT uses (things + observations, const injection). */
  private static String mappingFields() {
    return """
        {
          "$.things[].name": "$.station",
          "$.things[].description": { "op": "const", "value": "%s" },
          "$.things[].properties.reference": "$.ref",
          "$.observations[].result": { "op": "toFloat", "input": "$.temp" },
          "$.observations[].phenomenonTime": "$.ts",
          "$.observations[].parameters.reference": "$.ref",
          "$.observations[].parameters.name": "$.dsname"
        }
        """
        .formatted(INJECTION_DESCRIPTION);
  }

  private static void deployMqttPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": { "fields": %s } } },
                { "id": "k", "type": "frost", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """
                .formatted(mappingFields()));

    Datasource source = new Datasource();
    source.setId("mqtt-frost-map");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(TOPIC));
    source.handleUnknownProperty("client_id", "civitas-frost-map");
    source.handleUnknownProperty("qos", 1);

    deploy("pipeline-frost-map-mqtt-it", graph, source);
  }

  private static void deploySqlPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "type": "start", "data": {} },
                { "id": "src", "type": "dataSource", "data": { "entityId": "src-1" } },
                { "id": "c", "type": "cron", "data": { "cronExpression": "%s" } },
                { "id": "m", "type": "mapping", "data": { "mappingConfig": { "fields": %s } } },
                { "id": "k", "type": "frost", "data": { "entityId": "sink-1" } },
                { "id": "e", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND, mappingFields()));

    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    String encryptedPassword =
        "ENC("
            + CredentialEncryptor.encrypt(
                SRC_PASSWORD, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";
    Datasource source = new Datasource();
    source.setId("sql-frost-map");
    source.setType("SQL");
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://srcdb:5432/" + SRC_DB);
    source.handleUnknownProperty("user", SRC_USER);
    source.handleUnknownProperty("password", encryptedPassword);
    source.handleUnknownProperty("table", "frost_input");
    source.handleUnknownProperty("columns", List.of("*"));

    deploy("pipeline-frost-map-sql-it", graph, source);
  }

  private static void deploy(String pipelineId, Map<String, Object> graph, Datasource source)
      throws Exception {
    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FlowDeploymentPlanner planner =
          NifiTestFixtures.planner(
              resolver, SqlSourceProbe.NO_OP, null, "http://frost:8080" + FROST_PATH);
      DeploymentPlan plan =
          planner.plan(
              new PipelineDeploymentRequest(
                  pipelineId, graph, source, new FrostSinkSpec(String.valueOf(projectId))));
      client.deployFlow(plan);
    }
  }

  // ─── FROST fixtures & queries ───────────────────────────────────────────────

  private static long createProject() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder()
                    .uri(URI.create(staticFrostUrl("/Projects")))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"frost-mapping-it\",\"description\":\"IT project\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    return idFromLocation(response, "Projects");
  }

  /**
   * Provisions a Datastream (with holder Thing, Sensor, ObservedProperty) the observation leg can
   * resolve by {@code properties/reference} + {@code name}, project-linked so the {@code
   * Thing/Projects/id} filter matches.
   */
  private static long createDatastream(String dsName, String dsReference, String holderReference)
      throws Exception {
    HttpClient staticHttp = HttpClient.newHttpClient();
    String thingBody =
        "{\"name\":\"Holder "
            + dsName
            + "\",\"description\":\"mapping IT holder\","
            + "\"properties\":{\"reference\":\""
            + holderReference
            + "\"},"
            + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
            + "\"encodingType\":\"application/geo+json\","
            + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}]}";
    long thingId =
        idFromLocation(
            staticHttp.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(staticFrostUrl("/Projects(" + projectId + ")/Things")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(thingBody))
                    .build(),
                HttpResponse.BodyHandlers.ofString()),
            "Things");

    String dsBody =
        "{\"name\":\""
            + dsName
            + "\",\"description\":\"mapping IT\","
            + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
            + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\",\"definition\":\"ucum:Cel\"},"
            + "\"properties\":{\"reference\":\""
            + dsReference
            + "\"},"
            + "\"Sensor\":{\"name\":\"Sensor-IT\",\"description\":\"s\","
            + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
            + "\"ObservedProperty\":{\"name\":\"Temperature\","
            + "\"definition\":\"http://example.org/temp\",\"description\":\"t\"}}";
    return idFromLocation(
        staticHttp.send(
            HttpRequest.newBuilder()
                .uri(URI.create(staticFrostUrl("/Things(" + thingId + ")/Datastreams")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(dsBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()),
        "Datastreams");
  }

  private static String staticFrostUrl(String path) {
    return "http://" + frost.getHost() + ":" + frost.getMappedPort(8080) + FROST_PATH + path;
  }

  private String frostUrl(String path) {
    return "http://" + dockerHost + ":" + frost.getMappedPort(8080) + FROST_PATH + path;
  }

  private int countThings(String reference) throws Exception {
    return thingsByReference(reference).size();
  }

  private JsonNode thingByReference(String reference) throws Exception {
    return thingsByReference(reference).get(0);
  }

  private JsonNode thingsByReference(String reference) throws Exception {
    String filter =
        URLEncoder.encode("properties/reference eq '" + reference + "'", StandardCharsets.UTF_8)
            .replace("+", "%20");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Projects(" + projectId + ")/Things?$filter=" + filter)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value");
  }

  private int countDatastreamsByFilter(String reference, String name) throws Exception {
    String filter =
        URLEncoder.encode(
                "properties/reference eq '" + reference + "' and name eq '" + name + "'",
                StandardCharsets.UTF_8)
            .replace("+", "%20");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Datastreams?$filter=" + filter)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value").size();
  }

  private JsonNode observations(long datastreamId) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Datastreams(" + datastreamId + ")/Observations")))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value");
  }

  private List<Double> resultValues(long datastreamId) throws Exception {
    JsonNode observations = observations(datastreamId);
    List<Double> results = new ArrayList<>();
    for (JsonNode observation : observations) {
      if (observation.path("result").isNumber()) {
        results.add(observation.path("result").asDouble());
      }
    }
    return results;
  }

  private static long idFromLocation(HttpResponse<String> response, String collection) {
    Matcher matcher =
        Pattern.compile(collection + "\\((\\d+)\\)")
            .matcher(response.headers().firstValue("Location").orElse(""));
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException(
        "could not determine created "
            + collection
            + " id (status "
            + response.statusCode()
            + "): "
            + response.body());
  }

  // ─── Source DB & payloads ───────────────────────────────────────────────────

  private static void seedSourceDb() throws Exception {
    try (Connection c = sourceDbConnection();
        Statement st = c.createStatement()) {
      st.execute(
          "CREATE TABLE frost_input (id bigint, station text, ref text, dsname text,"
              + " temp double precision, ts text)");
      st.execute(
          "INSERT INTO frost_input (id, station, ref, dsname, temp, ts) VALUES"
              + " (1, 'SQL Station', '"
              + REF_SQL
              + "', '"
              + DS_SQL
              + "', 30.5, '2026-01-02T00:00:00Z')");
    }
  }

  private static Connection sourceDbConnection() throws Exception {
    return DriverManager.getConnection(srcdb.getJdbcUrl(), SRC_USER, SRC_PASSWORD);
  }

  /** A non-STA source payload; {@code temp} may be null to omit the field entirely. */
  private static String payload(
      String station, String ref, String dsName, String temp, String tsJson) {
    return "{\"station\":\""
        + station
        + "\",\"ref\":\""
        + ref
        + "\",\"dsname\":\""
        + dsName
        + "\","
        + (temp == null ? "" : "\"temp\":" + temp + ",")
        + "\"ts\":"
        + tsJson
        + "}";
  }

  private static Map<String, Object> json(String raw) throws Exception {
    return new ObjectMapper().readValue(raw, new TypeReference<Map<String, Object>>() {});
  }

  private MqttPublisher publisher(String clientId) throws Exception {
    return new MqttPublisher("tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883), clientId);
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

    MqttPublisher(String brokerUrl, String clientId) throws Exception {
      mqtt = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      mqtt.connect(options);
    }

    void publish(String topic, String payload) throws Exception {
      MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
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

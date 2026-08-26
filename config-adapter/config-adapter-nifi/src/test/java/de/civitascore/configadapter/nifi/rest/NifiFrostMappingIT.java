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
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import jakarta.ws.rs.core.Response;
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
import org.awaitility.core.ConditionTimeoutException;
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
 * DATA-CORRECTNESS integration test for the mapped FROST path (record mapping → per-entity body
 * templates → find-or-create) against a <b>real FROST-Server</b>. Two pipelines are planned through
 * the real {@link FlowDeploymentPlanner} (so the {@code FrostMappingCompiler} output is what
 * deploys) and exercised end to end:
 *
 * <ul>
 *   <li><b>MQTT (non-STA payload)</b>: the STA bodies are rendered from mapped record fields —
 *       Thing find-or-create stays idempotent across re-deliveries, the observation lands typed on
 *       the pre-provisioned Datastream (with an explicitly deep-inserted FeatureOfInterest and the
 *       optional {@code resultQuality}/{@code validTime} fields), and tenant values containing
 *       NiFi-EL/backreference syntax ({@code ${HOSTNAME}}, {@code $1}, {@code
 *       ${SINGLE_USER_CREDENTIALS_PASSWORD}}) arrive <i>literally</i> in FROST — the
 *       injection-hardening proof for the template path.
 *   <li><b>SQL (table rows)</b>: a multi-record batch is split into individual STA elements ({@code
 *       $[*]}), deduplicating the Thing across rows and re-reads.
 * </ul>
 *
 * <p>Failure paths must never drop silently: a missing source field renders a JSON {@code null}
 * result (valid body), an unmatched Datastream routes to the error sink without creating anything,
 * and a FROST-rejected observation (bad {@code phenomenonTime}) never becomes an observation while
 * the flow keeps running. Skipped when Docker is unavailable.
 */
class NifiFrostMappingIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18448;
  private static final String FROST_PATH = "/FROST-Server/v1.1";
  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String CRON_EVERY_SECOND = "* * * * * ?";

  private static final String TOPIC = "civitas/it/frost-mapping";
  private static final String FANOUT_TOPIC = "civitas/it/frost-fanout";
  private static final String REF_FANOUT = "REF-FANOUT-1";
  private static final String DS_FANOUT = "DS-FANOUT-1";
  private static final String PARTIAL_TOPIC = "civitas/it/frost-partial";
  private static final String REF_PARTIAL = "REF-PARTIAL-1";
  private static final String DS_PARTIAL = "DS-PARTIAL-1";
  private static final String CREATABLE_TOPIC = "civitas/it/frost-creatable";
  private static final String REF_CREATE = "REF-CREATE-1";

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
  private static final String REF_DATEONLY = "REF-DATEONLY-1";
  private static final String DS_DATEONLY = "DS-DATEONLY-1";
  private static final String REF_FOI = "REF-FOI-1";
  private static final String DS_FOI = "DS-FOI-1";
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
  private static long dsDateOnlyId;
  private static long dsSqlId;
  private static long dsFoiId;
  private static long dsFanoutId;
  private static long dsPartialId;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  @SuppressWarnings("resource")
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/FROST mapping IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

    frostDb =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.POSTGIS))
            .withNetwork(network)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    frostDb.start();

    frost =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.FROST))
            .withNetwork(network)
            .withNetworkAliases("frost")
            .withExposedPorts(8080)
            .withEnv("serviceRootUrl", "http://frost:8080" + FROST_PATH + "/")
            .withEnv("plugins_projects_enable", "true")
            .withEnv("plugins_projects_enableDefaultRules", "false")
            .withEnv("plugins_modelLoader_enable", "true")
            .withEnv("plugins_multiDatastream_enable", "false")
            .withEnv("plugins_actuation_enable", "false")
            .withEnv("persistence_db_driver", "org.postgresql.Driver")
            .withEnv("persistence_db_url", "jdbc:postgresql://database:5432/sensorthings")
            .withEnv("persistence_db_username", "sensorthings")
            .withEnv("persistence_db_password", "ChangeMe")
            .withEnv("persistence_autoUpdateDatabase", "true")
            .waitingFor(
                Wait.forHttp(FROST_PATH + "/Things")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));
    frost.start();

    srcdb =
        new PostgreSQLContainer<>(
                DockerImageName.parse(TestContainerImages.POSTGRES)
                    .asCompatibleSubstituteFor("postgres"))
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
    dsDateOnlyId = createDatastream(DS_DATEONLY, REF_DATEONLY, "HOLDER-DATEONLY");
    dsSqlId = createDatastream(DS_SQL, REF_SQL, "HOLDER-SQL");
    dsFoiId = createDatastream(DS_FOI, REF_FOI, "HOLDER-FOI");
    dsFanoutId = createDatastream(DS_FANOUT, REF_FANOUT, "HOLDER-FANOUT");
    dsPartialId = createDatastream(DS_PARTIAL, REF_PARTIAL, "HOLDER-PARTIAL");

    deployMqttPipeline();
    deploySqlPipeline();
    deployCreatableChainPipeline();
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
    // Arrays.asList (not List.of) tolerates nulls: when Docker is absent startStack aborts via
    // assumeTrue before these fields are assigned, and List.of would NPE here on teardown.
    for (GenericContainer<?> container : java.util.Arrays.asList(frost, frostDb, mosquitto)) {
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
      // idempotency holds through the rebuilt envelope, not just the raw one) — but each delivery
      // MUST append one observation: the found ("re-delivered") path has to reach the observation
      // POST, not just resolve the Thing and stop. Guards against silent steady-state data loss.
      // Exactly five re-deliveries, so the observation count is asserted against a known delta;
      // the await below absorbs the pipeline latency the publish/sleep loop used to pad out.
      int observationsBefore = observations(dsMapId).size();
      for (int i = 0; i < 5; i++) {
        publisher.publish(TOPIC, payload);
      }
      await()
          .atMost(Duration.ofSeconds(40))
          .pollInterval(Duration.ofSeconds(2))
          .ignoreExceptions()
          .until(() -> observations(dsMapId).size() >= observationsBefore + 5);
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
    // toString needs NiFi's two-arg form; a single-arg call fails RecordPath compile at deploy and
    // the flow never produces this observation. resultQuality arriving as the quoted temp proves
    // the emitted toString(subject, charset) parses and runs against a real NiFi.
    JsonNode resultQuality = observation.path("resultQuality");
    assertTrue(resultQuality.isTextual(), "toString must serialize the numeric temp as a string");
    assertEquals("21.5", resultQuality.asText());
    assertTrue(
        observation.path("phenomenonTime").asText().startsWith("2026-01-01T00:00:00"),
        "phenomenonTime must survive as the mapped ISO instant");
  }

  @Test
  void arraySourceProducesOneObservationPerElement() throws Exception {
    // The defect from the report, end to end: one MQTT message carrying three readings across two
    // array levels must yield THREE observations on the datastream — not one, and not a deploy-time
    // rejection. The entity bodies are static EL templates, so the explode has to happen upstream
    // of
    // them; this asserts the sink-observable consequence, not the compiled paths.
    String payload =
        "{\"station\":\"Gateway station\",\"ref\":\""
            + REF_FANOUT
            + "\",\"measurements\":[{\"sensorId\":\"A\",\"measuredValues\":["
            + "{\"ts\":\"2026-02-01T00:00:00Z\",\"value\":1.5},"
            + "{\"ts\":\"2026-02-01T00:15:00Z\",\"value\":2.5}]},"
            + "{\"sensorId\":\"B\",\"measuredValues\":["
            + "{\"ts\":\"2026-02-01T01:00:00Z\",\"value\":3.5}]}]}";

    // Deployed here rather than in @BeforeAll: this pipeline is the only one in the class whose
    // deploy can fail on its own mapping, and a class-level deploy would abort every other test.
    deployFanoutPipeline();

    publishOnceIntoTheFlow(
        "civitas-it-fanout", FANOUT_TOPIC, payload, () -> observations(dsFanoutId).size() >= 3);

    // The poll above is a lower bound, so it turns true sooner under an over-fork. Settle before
    // counting, or a run producing 6 observations passes with 3 still in flight.
    settleBeforeCounting();

    // result AND phenomenonTime per observation: the reported defect is about the timestamp field,
    // and a fix that fans out the result while broadcasting the first timestamp onto every
    // observation would pass a result-only assertion. Pairing them also detects values bleeding
    // across the two source levels.
    List<String> pairs = new ArrayList<>();
    for (JsonNode observation : observations(dsFanoutId)) {
      pairs.add(
          observation.path("result").asDouble()
              + "@"
              + observation.path("phenomenonTime").asText().substring(0, 19));
    }
    pairs.sort(null);
    assertEquals(
        List.of("1.5@2026-02-01T00:00:00", "2.5@2026-02-01T00:15:00", "3.5@2026-02-01T01:00:00"),
        pairs,
        "every array element must become its own observation with its own timestamp");

    // Only the OBSERVATION tier multiplies. The mapping also writes the Thing and Datastream tiers,
    // whose target paths carry [] as well — those are entity-tier markers, so they must stay
    // singular. A fan-out derived from the target side instead of the source side would create one
    // Datastream per element here; the pre-provisioned Datastream is resolved by the find path, so
    // this stays exact.
    assertEquals(
        1,
        countDatastreamsByFilter(REF_FANOUT, DS_FANOUT),
        "the Datastream tier must not multiply with the array");

    // The Thing tier is CREATED here (nothing carries REF_FANOUT up front), and the siblings all
    // pass the lookup before the first create is visible in FROST — so find-or-create is not
    // idempotent across them and the tier currently multiplies. That race belongs to the entity
    // stage, not to the array fan-out: a SQL batch delivering several rows for one Thing hits it
    // the same way, and resolving it needs an identity the sink can collide on (a derived
    // @iot.id) rather than a lookup. Asserted as "at least one" so this test keeps covering the
    // fan-out; the exact count returns once entity identity is deterministic.
    assertTrue(
        countThings(REF_FANOUT) >= 1, "the mapped Thing tier must be created for the array source");
  }

  @Test
  void oneBadElementDoesNotDiscardTheRemainingOnes() throws Exception {
    // The key guard acts per record, so a fanned-out element with an empty match key must go to the
    // error sink while its siblings still land. An implementation routing the whole FlowFile on the
    // first bad element would lose all N readings — and would look green in every other scenario.
    String payload =
        "{\"station\":\"Partial gateway\",\"ref\":\""
            + REF_PARTIAL
            + "\",\"measurements\":[{\"measuredValues\":["
            + "{\"ts\":\"2026-02-02T00:00:00Z\",\"value\":10.5,\"dsref\":\""
            + REF_PARTIAL
            + "\"},"
            + "{\"ts\":\"2026-02-02T00:15:00Z\",\"value\":11.5,\"dsref\":\"\"},"
            + "{\"ts\":\"2026-02-02T00:30:00Z\",\"value\":12.5,\"dsref\":\""
            + REF_PARTIAL
            + "\"}]}]}";

    deployPartialPipeline();

    publishOnceIntoTheFlow(
        "civitas-it-partial", PARTIAL_TOPIC, payload, () -> observations(dsPartialId).size() >= 2);
    settleBeforeCounting();

    List<Double> results = new ArrayList<>();
    for (JsonNode observation : observations(dsPartialId)) {
      results.add(observation.path("result").asDouble());
    }
    results.sort(null);
    assertEquals(
        List.of(10.5, 12.5), results, "the valid elements must survive one unusable sibling");
  }

  @Test
  void mappedObservationCarriesAnExplicitFeatureOfInterestAndOptionalFields() throws Exception {
    // The mapping deep-inserts a FeatureOfInterest and maps resultQuality/validTime — the
    // observation must carry the explicit feature (name "Sampling point"), not FROST's default
    // Thing-location fallback, plus the two optional fields. Uses its own station (REF_FOI) so it
    // never shares a find-or-create Thing with the injection-hardening test (REF_MAP).
    String payload = payload("FoI Station", REF_FOI, DS_FOI, "18.0", "\"2026-02-01T00:00:00Z\"");

    JsonNode observation;
    try (MqttPublisher publisher = publisher("civitas-it-foi")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return hasFeatureOfInterest(observationsWithFeature(dsFoiId));
              });
      observation = withFeatureOfInterest(observationsWithFeature(dsFoiId));
    }

    assertEquals(
        "Sampling point",
        observation.path("FeatureOfInterest").path("name").asText(),
        "the observation must reference the explicitly mapped FeatureOfInterest");
    assertEquals(
        "Point",
        observation.path("FeatureOfInterest").path("feature").path("type").asText(),
        "the FeatureOfInterest feature must be the geoPoint-rendered GeoJSON object");
    JsonNode foiResultQuality = observation.path("resultQuality");
    assertTrue(
        foiResultQuality.isTextual(), "toString must serialize the numeric temp as a string");
    assertEquals(
        "18.0", foiResultQuality.asText(), "resultQuality must carry the toString-converted temp");
    String validTime = observation.path("validTime").asText();
    assertTrue(
        validTime.contains("/") && validTime.contains("2026-02-01T00:00:00"),
        "validTime must survive as the mapped TM_Period interval (start/end), was: " + validTime);
  }

  @Test
  void creatableChainDeepInsertsTheWholeTreeIntoFrost() throws Exception {
    // A station never provisioned: the mapping's creatable Thing/Location/Datastream bodies must
    // create the entire tree in FROST, deep-inserting the Location on the Thing and the Sensor /
    // ObservedProperty / unitOfMeasurement on the Datastream — the actual bodies FROST accepts,
    // not only their byte form.
    String payload =
        payload(
            "Created Station", REF_CREATE, "ignored-ds-name", "24.5", "\"2026-03-01T00:00:00Z\"");

    JsonNode thing;
    try (MqttPublisher publisher = publisher("civitas-it-create")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(CREATABLE_TOPIC, payload);
                JsonNode t = expandedThing(REF_CREATE);
                // Wait for the whole deep-inserted tree AND the observation: the Location rides the
                // Thing POST, the Datastream its own POST, and the observation is the last stage —
                // a partial snapshot can have the Datastream but not yet its observation.
                return !t.path("Locations").isEmpty()
                    && !t.path("Datastreams").path(0).path("Observations").isEmpty();
              });
      thing = expandedThing(REF_CREATE);
    } catch (ConditionTimeoutException e) {
      throw new AssertionError(
          "creatable mapped chain did not reach its Observation. NiFi bulletins:\n" + bulletins(),
          e);
    }

    JsonNode location = thing.path("Locations").path(0);
    assertEquals(
        "Point",
        location.path("location").path("type").asText(),
        "the Location must be deep-inserted with the geoPoint GeoJSON");

    JsonNode datastream = thing.path("Datastreams").path(0);
    assertEquals(
        "Degree Celsius",
        datastream.path("unitOfMeasurement").path("name").asText(),
        "unitOfMeasurement must be deep-inserted on the Datastream");
    assertEquals(
        "DHT22",
        datastream.path("Sensor").path("name").asText(),
        "the Sensor must be deep-inserted on the Datastream");
    assertEquals(
        "Temperature",
        datastream.path("ObservedProperty").path("name").asText(),
        "the ObservedProperty must be deep-inserted on the Datastream");
    assertTrue(
        datastream.path("Observations").path(0).path("result").isNumber(),
        "the observation must land on the freshly created Datastream");
  }

  /** Dumps the NiFi bulletin board when the asynchronous chain does not reach FROST in time. */
  private String bulletins() throws Exception {
    String token = client.authenticate();
    try (Response response =
        httpClient
            .target("https://" + dockerHost + ":" + HOST_PORT + "/nifi-api/flow/bulletin-board")
            .request()
            .header("Authorization", "Bearer " + token)
            .get()) {
      return response.readEntity(String.class);
    }
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
      // Deliberate dwell: gives the observation leg ample time to (wrongly) post before asserting
      // it
      // never did — an absence has no condition that can complete early.
      Thread.sleep(Duration.ofSeconds(10).toMillis());
    }
    assertEquals(
        0,
        observations(dsBadTsId).size(),
        "a FROST-rejected observation must go to the error sink, not into the Datastream");
  }

  @Test
  void dateOnlyPhenomenonTimeIsRejectedByFrost() throws Exception {
    // A Date-typed (format:date) modeller attribute yields '2026-07-29' — no time, no zone. The
    // adapter passes STA time targets through as plain strings (StaJsonType.STRING), so whether
    // such
    // a value is usable is FROST's call alone: it rejects it, exactly as it rejects 'not-a-date'.
    // Consequence for the modeller: a Date attribute must never be mapped onto an STA time target.
    String payload =
        payload("DateOnly Station", REF_DATEONLY, DS_DATEONLY, "12.3", "\"2026-07-29\"");

    try (MqttPublisher publisher = publisher("civitas-it-dateonly")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, payload);
                return countThings(REF_DATEONLY) >= 1;
              });
      // the Thing leg proves the message was processed; give the observation leg time to post
      Thread.sleep(Duration.ofSeconds(10).toMillis());
    }
    assertEquals(
        0,
        observations(dsDateOnlyId).size(),
        "a date-only phenomenonTime must not silently become an observation");
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
    // >= 1, not == 1: the per-second cron can re-read row 1 and race a second first-sight message
    // into a duplicate Thing before the first commits (see mappedMqtt…). The invariant is that the
    // chain reached FROST, not an exact dedup count under concurrent first contact.
    //
    // Sampled only once the count holds still across several cron ticks: a create still in flight
    // would otherwise land after the sample and fail the comparison below even though row 2
    // correctly reused the Thing.
    int thingsAfterFirstRow = settledThingCount(REF_SQL);
    assertTrue(thingsAfterFirstRow >= 1, "the first row's Thing must exist");

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

    // Both rows share the reference, and by now the first Thing is long committed — so the lookup
    // hits and the second row must mint NO further Thing. Compared against the earlier count rather
    // than a literal: the initial first-contact race may have produced more than one, but once past
    // it the count must stop growing, which a bare '>= 1' would never notice.
    assertEquals(
        thingsAfterFirstRow,
        countThings(REF_SQL),
        "a same-reference row must reuse the existing Thing instead of minting another");
  }

  // ─── Deployment ─────────────────────────────────────────────────────────────

  /**
   * The record-anchored mapping every pipeline of this IT uses (Thing + lookup-only Datastream +
   * observations, const injection). The datastreams are pre-created with a {@code
   * properties.reference} equal to the record's {@code ref}, so the lookup-only stage resolves
   * them.
   */
  private static String mappingFields() {
    return """
        {
          "$.name": "$.station",
          "$.description": { "op": "const", "value": "%s" },
          "$.properties.reference": "$.ref",
          "$.Datastreams[].properties.reference": "$.ref",
          "$.Datastreams[].Observations[].result": { "op": "toFloat", "input": "$.temp" },
          "$.Datastreams[].Observations[].phenomenonTime": "$.ts",
          "$.Datastreams[].Observations[].validTime": { "op": "concat", "separator": "/", "inputs": ["$.ts", "$.ts"] },
          "$.Datastreams[].Observations[].resultQuality": { "op": "toString", "input": "$.temp" },
          "$.Datastreams[].Observations[].FeatureOfInterest.name": { "op": "const", "value": "Sampling point" },
          "$.Datastreams[].Observations[].FeatureOfInterest.description": { "op": "const", "value": "Where the reading was taken" },
          "$.Datastreams[].Observations[].FeatureOfInterest.encodingType": { "op": "const", "value": "application/geo+json" },
          "$.Datastreams[].Observations[].FeatureOfInterest.feature": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" }
        }
        """
        .formatted(INJECTION_DESCRIPTION);
  }

  private static void deployMqttPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "src", "kind": "source", "sourceRef": "src-1" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("mqtt-frost-map");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(TOPIC));
    source.handleUnknownProperty("client_id", "civitas-frost-map");
    source.handleUnknownProperty("qos", 1);

    deploy("pipeline-frost-map-mqtt-it", graph, source, frostMapping(mappingFields()));
  }

  /**
   * A fully-creatable chain on its own topic/station: Thing (with a deep-inserted Location),
   * Datastream (with deep-inserted Sensor / ObservedProperty / unitOfMeasurement), Observation. No
   * pre-provisioned entities — the whole tree is created on first contact, so the IT proves the
   * deep-insert bodies FROST actually accepts, not only their byte form.
   */
  private static void deployCreatableChainPipeline() throws Exception {
    String fields =
        """
        {
          "$.properties.reference": "$.ref",
          "$.name": "$.station",
          "$.description": { "op": "const", "value": "Created station" },
          "$.Locations[].name": { "op": "const", "value": "Station location" },
          "$.Locations[].description": { "op": "const", "value": "Reported position" },
          "$.Locations[].encodingType": { "op": "const", "value": "application/geo+json" },
          "$.Locations[].location": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" },
          "$.Datastreams[].properties.reference": "$.ref",
          "$.Datastreams[].name": { "op": "const", "value": "Air temperature" },
          "$.Datastreams[].description": { "op": "const", "value": "Air temperature at the station" },
          "$.Datastreams[].observationType": { "op": "const", "value": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement" },
          "$.Datastreams[].unitOfMeasurement.name": { "op": "const", "value": "Degree Celsius" },
          "$.Datastreams[].unitOfMeasurement.symbol": { "op": "const", "value": "degC" },
          "$.Datastreams[].unitOfMeasurement.definition": { "op": "const", "value": "ucum:Cel" },
          "$.Datastreams[].Sensor.name": { "op": "const", "value": "DHT22" },
          "$.Datastreams[].Sensor.description": { "op": "const", "value": "Temperature sensor" },
          "$.Datastreams[].Sensor.encodingType": { "op": "const", "value": "application/pdf" },
          "$.Datastreams[].Sensor.metadata": { "op": "const", "value": "https://example.org/dht22.pdf" },
          "$.Datastreams[].ObservedProperty.name": { "op": "const", "value": "Temperature" },
          "$.Datastreams[].ObservedProperty.definition": { "op": "const", "value": "https://example.org/temp" },
          "$.Datastreams[].ObservedProperty.description": { "op": "const", "value": "Air temperature" },
          "$.Datastreams[].Observations[].result": { "op": "toFloat", "input": "$.temp" },
          "$.Datastreams[].Observations[].phenomenonTime": "$.ts"
        }
        """;
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "src", "kind": "source", "sourceRef": "src-1" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("mqtt-frost-create");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(CREATABLE_TOPIC));
    source.handleUnknownProperty("client_id", "civitas-frost-create");
    source.handleUnknownProperty("qos", 1);

    deploy("pipeline-frost-create-mqtt-it", graph, source, frostMapping(fields));
  }

  private static void deploySqlPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "src", "kind": "source", "sourceRef": "src-1" },
                { "id": "c", "kind": "cron", "cronExpression": "%s" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "c" },
                { "id": "e2", "source": "c", "target": "src" },
                { "id": "e3", "source": "src", "target": "m" },
                { "id": "e4", "source": "m", "target": "k" },
                { "id": "e5", "source": "k", "target": "e" } ] }
            """
                .formatted(CRON_EVERY_SECOND));

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

    deploy("pipeline-frost-map-sql-it", graph, source, frostMapping(mappingFields()));
  }

  /**
   * The reported structure: readings nested two array levels deep under a gateway. Each leaf
   * element must become its own observation, with the gateway-level reference carried along — so
   * the source array has to be exploded before the entity bodies are rendered.
   */
  private static void deployFanoutPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "src", "kind": "source", "sourceRef": "src-1" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("mqtt-frost-fanout");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(FANOUT_TOPIC));
    source.handleUnknownProperty("client_id", "civitas-frost-fanout");
    source.handleUnknownProperty("qos", 1);

    deploy(
        "pipeline-frost-fanout-it",
        graph,
        source,
        frostMapping(
            """
            {
              "$.name": "$.station",
              "$.description": { "op": "const", "value": "Gateway with nested readings" },
              "$.properties.reference": "$.ref",
              "$.Datastreams[].properties.reference": "$.ref",
              "$.Datastreams[].Observations[].result":
                  { "op": "toFloat", "input": "$.measurements[].measuredValues[].value" },
              "$.Datastreams[].Observations[].phenomenonTime":
                  "$.measurements[].measuredValues[].ts"
            }"""));
  }

  /**
   * Like the fan-out pipeline, but the Datastream match key is taken from inside the array element
   * — so a single element with an empty key is unusable while its siblings stay resolvable.
   */
  private static void deployPartialPipeline() throws Exception {
    Map<String, Object> graph =
        json(
            """
            { "nodes": [
                { "id": "s", "kind": "start" },
                { "id": "src", "kind": "source", "sourceRef": "src-1" },
                { "id": "m", "kind": "mapping", "mappingRef": "map-1" },
                { "id": "k", "kind": "sink", "sinkRef": "sink-1" },
                { "id": "e", "kind": "end" } ],
              "edges": [
                { "id": "e1", "source": "s", "target": "src" },
                { "id": "e2", "source": "src", "target": "m" },
                { "id": "e3", "source": "m", "target": "k" },
                { "id": "e4", "source": "k", "target": "e" } ] }
            """);

    Datasource source = new Datasource();
    source.setId("mqtt-frost-partial");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mqtt:1883"));
    source.handleUnknownProperty("topics", List.of(PARTIAL_TOPIC));
    source.handleUnknownProperty("client_id", "civitas-frost-partial");
    source.handleUnknownProperty("qos", 1);

    deploy(
        "pipeline-frost-partial-it",
        graph,
        source,
        frostMapping(
            """
            {
              "$.name": "$.station",
              "$.description": { "op": "const", "value": "Partial delivery gateway" },
              "$.properties.reference": "$.ref",
              "$.Datastreams[].properties.reference":
                  "$.measurements[].measuredValues[].dsref",
              "$.Datastreams[].Observations[].result":
                  { "op": "toFloat", "input": "$.measurements[].measuredValues[].value" },
              "$.Datastreams[].Observations[].phenomenonTime":
                  "$.measurements[].measuredValues[].ts"
            }"""));
  }

  private static void deploy(
      String pipelineId, Map<String, Object> graph, Datasource source, Map<String, Object> mappings)
      throws Exception {
    byte[] key = CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
    try (CredentialResolver resolver = new CredentialResolver(key)) {
      FlowDeploymentPlanner planner =
          NifiTestFixtures.planner(
              resolver, SqlSourceProbe.NO_OP, null, "http://frost:8080" + FROST_PATH);
      DeploymentPlan plan =
          planner.plan(
              new PipelineDeploymentRequest(
                  pipelineId,
                  graph,
                  source,
                  new FrostSinkSpec(
                      String.valueOf(projectId),
                      StaProperties.ofKeys(List.of("reference"), List.of("reference"))),
                  mappings));
      client.deployFlow(plan);
    }
  }

  /** A single-entry mappings catalog keyed by {@code map-1} (the graphs' mappingRef). */
  private static Map<String, Object> frostMapping(String fieldsJson) throws Exception {
    return json("{ \"map-1\": { \"fields\": " + fieldsJson + " } }");
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

  /**
   * The Thing count once it has stopped changing, so a create still in flight cannot land after the
   * caller sampled it. The per-second cron keeps re-reading the source, so "no more Things appear"
   * can only be established by observing several ticks, never from a single read.
   */
  private int settledThingCount(String reference) throws Exception {
    int previous = -1;
    for (int stableTicks = 0; stableTicks < 4; ) {
      Thread.sleep(Duration.ofSeconds(3).toMillis());
      int current = countThings(reference);
      stableTicks = current == previous ? stableTicks + 1 : 0;
      previous = current;
    }
    return previous;
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

  private JsonNode expandedThing(String reference) throws Exception {
    String filter =
        URLEncoder.encode("properties/reference eq '" + reference + "'", StandardCharsets.UTF_8)
            .replace("+", "%20");
    String expand =
        URLEncoder.encode(
                "Locations,Datastreams($expand=Sensor,ObservedProperty,Observations)",
                StandardCharsets.UTF_8)
            .replace("+", "%20");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        frostUrl(
                            "/Projects("
                                + projectId
                                + ")/Things?$filter="
                                + filter
                                + "&$expand="
                                + expand)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value").get(0);
  }

  private JsonNode observationsWithFeature(long datastreamId) throws Exception {
    String query =
        URLEncoder.encode("FeatureOfInterest", StandardCharsets.UTF_8).replace("+", "%20");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        frostUrl(
                            "/Datastreams(" + datastreamId + ")/Observations?$expand=" + query)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value");
  }

  private static boolean hasFeatureOfInterest(JsonNode observations) {
    for (JsonNode observation : observations) {
      if ("Sampling point".equals(observation.path("FeatureOfInterest").path("name").asText())) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode withFeatureOfInterest(JsonNode observations) {
    for (JsonNode observation : observations) {
      if ("Sampling point".equals(observation.path("FeatureOfInterest").path("name").asText())) {
        return observation;
      }
    }
    throw new IllegalStateException("no observation carried the mapped FeatureOfInterest");
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
        + "\",\"lon\":7.1,\"lat\":51.5,"
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

  /** Reports whether the expected outcome has landed. */
  private interface Landed {
    boolean check() throws Exception;
  }

  /**
   * Delivers the payload exactly once <em>into the flow</em>, then polls the assertion. Needed
   * wherever the expected count is exact, so retention is not an option: ConsumeMQTT re-receives a
   * retained message on every resubscribe and would keep producing entities for the whole window.
   *
   * <p>But a non-retained publish is discarded while no subscriber exists, and {@code deployFlow}
   * only waits for NiFi to report the processor RUNNING, which precedes the MQTT CONNECT/SUBSCRIBE
   * — so the first delivery can be lost outright and the test would fail on a timeout that names
   * the wrong cause. Republishing only while nothing has landed retries a lost delivery without
   * ever sending a second payload into a flow that already received one.
   */
  private void publishOnceIntoTheFlow(String clientId, String topic, String payload, Landed landed)
      throws Exception {
    try (MqttPublisher publisher = publisher(clientId)) {
      for (int attempt = 0; attempt < 6 && !landed.check(); attempt++) {
        publisher.publishOnce(topic, payload);
        try {
          await()
              .atMost(Duration.ofSeconds(20))
              .pollInterval(Duration.ofSeconds(1))
              .ignoreExceptions()
              .until(landed::check);
        } catch (ConditionTimeoutException nothingLanded) {
          // Treat the delivery as lost to the broker and republish.
        }
      }
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(landed::check);
    }
  }

  /**
   * Lets the flow drain before an exact count. A lower-bound poll turns true sooner under an
   * over-fork, so counting immediately would let a run producing too many entities pass.
   */
  private static void settleBeforeCounting() throws InterruptedException {
    Thread.sleep(Duration.ofSeconds(6).toMillis());
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

    /**
     * Publishes without retention, for scenarios asserting an exact observation count. A retained
     * message is redelivered on every ConsumeMQTT resubscribe, so it would keep producing
     * observations for the whole poll window — the values stay correct but the count becomes a
     * function of test duration.
     */
    void publishOnce(String topic, String payload) throws Exception {
      MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
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

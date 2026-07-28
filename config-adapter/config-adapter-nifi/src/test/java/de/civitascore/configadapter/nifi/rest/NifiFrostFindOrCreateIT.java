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

import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * DATA-CORRECTNESS integration test for the FROST upsert sub-flow against a <b>real
 * FROST-Server</b>: deploys the MQTT→upsert flow built by {@link NifiFlowBuilder} onto an actual
 * NiFi 2.9.0, publishes STA-envelope messages repeatedly, and asserts that FROST ends up with
 * exactly ONE updated Thing for the reference.
 *
 * <p>Topology (one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → FROST (alias {@code
 * frost}) → PostGIS (alias {@code database}). Skipped when Docker is unavailable.
 */
class NifiFrostFindOrCreateIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18447;
  private static final String TOPIC = "civitas/it/frost";
  private static final String REFERENCE = "STATION-IT-1";
  private static final String ENVELOPE =
      "{\"things\":[{\"name\":\"Station IT 1\",\"description\":\"find-or-create IT\","
          + "\"properties\":{\"reference\":\""
          + REFERENCE
          + "\"}}]}";
  private static final String UPDATED_ENVELOPE =
      "{\"things\":[{\"name\":\"Station IT 1 updated\",\"description\":\"upsert IT\","
          + "\"properties\":{\"reference\":\""
          + REFERENCE
          + "\"}}]}";
  private static final String FROST_PATH = "/FROST-Server/v1.1";
  private static final String OBS_TOPIC = "civitas/it/frost-obs";
  private static final String DS_REFERENCE = "DS-REF-IT";
  private static final String DS_NAME = "DS-IT";
  private static final String OBS_ENVELOPE =
      "{\"things\":[],\"observations\":[{\"result\":21.5,"
          + "\"phenomenonTime\":\"2026-01-01T00:00:00Z\","
          + "\"parameters\":{\"reference\":\""
          + DS_REFERENCE
          + "\",\"name\":\""
          + DS_NAME
          + "\"}}]}";

  // An observation whose Datastream (reference+name) was never provisioned: the lookup resolves
  // nothing, so the flow must route it to the error sink and never POST it.
  private static final String NO_DS_TOPIC = "civitas/it/frost-obs-nods";
  private static final String MISSING_DS_REFERENCE = "DS-REF-MISSING";
  private static final String MISSING_DS_NAME = "DS-MISSING";
  private static final String OBS_ENVELOPE_UNKNOWN_DS =
      "{\"things\":[],\"observations\":[{\"result\":9.9,"
          + "\"phenomenonTime\":\"2026-01-01T00:00:00Z\","
          + "\"parameters\":{\"reference\":\""
          + MISSING_DS_REFERENCE
          + "\",\"name\":\""
          + MISSING_DS_NAME
          + "\"}}]}";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> postgis;
  private static GenericContainer<?> frost;
  // The FROST project the deployed flows are scoped to (mirrors the saga's create-project step).
  private static long projectId;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  @SuppressWarnings("resource")
  static void startStack() throws Exception {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi/FROST find-or-create IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

    postgis =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.POSTGIS))
            .withNetwork(network)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    postgis.start();

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
    projectId = createProject();

    startNifi(HOST_PORT, network);
  }

  /** Creates the FROST project the flows are scoped to and returns its {@code @iot.id}. */
  private static long createProject() throws Exception {
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder()
                    .uri(
                        URI.create(
                            "http://"
                                + frost.getHost()
                                + ":"
                                + frost.getMappedPort(8080)
                                + FROST_PATH
                                + "/Projects"))
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            "{\"name\":\"find-or-create-it\",\"description\":\"IT project\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    return idFromLocation(response, "Projects");
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
    if (frost != null) {
      frost.stop();
    }
    if (postgis != null) {
      postgis.stop();
    }
    if (mosquitto != null) {
      mosquitto.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @Test
  void upsertCreatesOnceAndUpdatesOnRepeatedReference() throws Exception {
    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-frost-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", TOPIC),
                    SinkType.FROST,
                    Map.of(
                        FrostSinkStage.FROST_BASE_URL,
                        "http://frost:8080" + FROST_PATH,
                        FrostSinkStage.FROST_PROJECT_ID,
                        String.valueOf(projectId)),
                    List.of(),
                    Map.of(),
                    null,
                    null));
    client.deployFlow(new DeploymentPlan("pipeline-frost-it", snapshot, Map.of()));

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-frost")) {
      // The first delivery creates the Thing via POST.
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, ENVELOPE);
                return countThings(REFERENCE) >= 1;
              });

      // A changed record with the same reference must PATCH the existing Thing, never create a
      // duplicate. Republishing each poll replaces the hand-rolled publish/sleep retry loop.
      await()
          .atMost(Duration.ofSeconds(70))
          .pollInterval(Duration.ofSeconds(2))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                publisher.publish(TOPIC, UPDATED_ENVELOPE);
                assertEquals(1, countThings(REFERENCE), "upsert must not duplicate the Thing");
                assertEquals(
                    "Station IT 1 updated",
                    thingName(REFERENCE),
                    "the existing Thing must be updated by reference");
              });
    }
  }

  @Test
  void linksObservationToExistingDatastream() throws Exception {
    // Pre-provision a Datastream (deep-insert Thing+Sensor+ObservedProperty) the observation refers
    // to; the observation leg must resolve it by reference+name and POST the observation into it.
    long datastreamId = createDatastream();
    // sanity: the exact lookup filter the flow uses must find the Datastream in FROST
    assertEquals(
        1,
        countDatastreamsByFilter(DS_REFERENCE, DS_NAME),
        "FROST must resolve the Datastream by reference+name");

    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-frost-obs-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", OBS_TOPIC),
                    SinkType.FROST,
                    Map.of(
                        FrostSinkStage.FROST_BASE_URL,
                        "http://frost:8080" + FROST_PATH,
                        FrostSinkStage.FROST_PROJECT_ID,
                        String.valueOf(projectId)),
                    List.of(),
                    Map.of(),
                    null,
                    null));
    client.deployFlow(new DeploymentPlan("pipeline-frost-obs-it", snapshot, Map.of()));

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-frost-obs")) {
      // the observation must land in the resolved Datastream
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(OBS_TOPIC, OBS_ENVELOPE);
                return countObservations(datastreamId) >= 1;
              });
    } catch (ConditionTimeoutException e) {
      throw new AssertionError(
          "observation did not land in Datastream "
              + datastreamId
              + ". NiFi bulletins:\n"
              + bulletins(),
          e);
    }
  }

  @Test
  void observationWithNoMatchingDatastreamIsNotPosted() throws Exception {
    // The observation leg looks up the Datastream by reference+name; when none exists it must route
    // the record to the error sink — it must NOT create the Datastream (the pipeline never creates
    // one) and therefore never POST the observation. A shared FROST instance means the global
    // Observation count is churned by the sibling test, so the isolation-safe proof is that no
    // Datastream ever appears for the missing reference.
    assertEquals(
        0,
        countDatastreamsByFilter(MISSING_DS_REFERENCE, MISSING_DS_NAME),
        "precondition: the referenced Datastream must not exist");

    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-frost-nods-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", NO_DS_TOPIC),
                    SinkType.FROST,
                    Map.of(
                        FrostSinkStage.FROST_BASE_URL,
                        "http://frost:8080" + FROST_PATH,
                        FrostSinkStage.FROST_PROJECT_ID,
                        String.valueOf(projectId)),
                    List.of(),
                    Map.of(),
                    null,
                    null));
    client.deployFlow(new DeploymentPlan("pipeline-frost-nods-it", snapshot, Map.of()));

    // Publish the unmatched observation repeatedly while the flow starts, giving it ample time to
    // consume and route through the lookup. The Datastream must never appear: the pipeline does not
    // create one, so an unmatched reference is dropped to the error sink, not written.
    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-frost-nods")) {
      // Deliberate dwell: this proves an absence, so there is no condition that can complete early
      // and shortening the window would only narrow the chance to observe the Datastream appearing.
      for (int i = 0; i < 10; i++) {
        publisher.publish(NO_DS_TOPIC, OBS_ENVELOPE_UNKNOWN_DS);
        Thread.sleep(3000);
        assertEquals(
            0,
            countDatastreamsByFilter(MISSING_DS_REFERENCE, MISSING_DS_NAME),
            "the pipeline must not create a Datastream for an unmatched reference");
      }
    }
  }

  /** Dumps the NiFi bulletin board (errors/warnings from the deployed flow) for diagnostics. */
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

  private String frostUrl(String path) {
    return "http://" + dockerHost + ":" + frost.getMappedPort(8080) + FROST_PATH + path;
  }

  /**
   * Counts Things with the given {@code properties/reference} inside the IT project — the scoped
   * flow must create them there, not at the server root.
   */
  private int countThings(String reference) throws Exception {
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
    return mapper.readTree(response.body()).path("value").size();
  }

  /** Returns the name of the single Thing identified by reference inside the IT project. */
  private String thingName(String reference) throws Exception {
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
    return mapper.readTree(response.body()).path("value").path(0).path("name").asText();
  }

  /** Counts FROST Datastreams matching the flow's lookup filter (properties/reference + name). */
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

  /** Counts the Observations of a Datastream. */
  private int countObservations(long datastreamId) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Datastreams(" + datastreamId + ")/Observations")))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value").size();
  }

  /**
   * Provisions a Datastream inside the IT project and returns its {@code @iot.id}. Two steps: the
   * Thing is created under {@code /Projects(n)/Things} (the scoped flow's Datastream lookup filters
   * on {@code Thing/Projects/id}, so the Thing must be project-linked), then the Datastream (with
   * Sensor+ObservedProperty) is nested under that Thing.
   */
  private long createDatastream() throws Exception {
    String thingBody =
        "{\"name\":\"T-IT\",\"description\":\"obs-leg IT thing\","
            + "\"properties\":{\"reference\":\"T-REF-IT\"},"
            // a Location lets FROST auto-generate the Observation's FeatureOfInterest
            + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
            + "\"encodingType\":\"application/geo+json\","
            + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}]}";
    long thingId =
        idFromLocation(
            http.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(frostUrl("/Projects(" + projectId + ")/Things")))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(thingBody))
                    .build(),
                HttpResponse.BodyHandlers.ofString()),
            "Things");

    String dsBody =
        "{\"name\":\""
            + DS_NAME
            + "\",\"description\":\"obs-leg IT\","
            + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
            + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\",\"definition\":\"ucum:Cel\"},"
            + "\"properties\":{\"reference\":\""
            + DS_REFERENCE
            + "\"},"
            + "\"Sensor\":{\"name\":\"Sensor-IT\",\"description\":\"s\","
            + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
            + "\"ObservedProperty\":{\"name\":\"Temperature\","
            + "\"definition\":\"http://example.org/temp\",\"description\":\"t\"}}";
    return idFromLocation(
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Things(" + thingId + ")/Datastreams")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(dsBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()),
        "Datastreams");
  }

  /** Extracts the entity id from a FROST create response's {@code Location} header. */
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

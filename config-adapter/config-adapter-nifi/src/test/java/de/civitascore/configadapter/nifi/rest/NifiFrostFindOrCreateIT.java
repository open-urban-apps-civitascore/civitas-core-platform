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
import de.civitascore.configadapter.nifi.mapping.SinkPort;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Request;
import okhttp3.Response;
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
 * DATA-CORRECTNESS integration test for the deployed FROST sink against a <b>real FROST-Server</b>:
 * deploys the MQTT→FROST flow built by {@link NifiFlowBuilder} onto an actual NiFi 2.9.0 that
 * carries the {@code PutFrostRecord} NAR, publishes records, and asserts the entities FROST ends up
 * with.
 *
 * <p>This is the whole path — the configuration a Dataset carries, the flow the adapter builds, the
 * processor, the batch extension and the server. The processor's own integration test proves the
 * write logic of each port; what is proven here is that a deployed Pipeline reaches it.
 *
 * <p>The {@code ThingTree} port is the one the existing Pipelines move to, so its test asserts the
 * entity set the generated graph produced for the same source data: one Thing, one Location, one
 * Datastream, and an appended measurement.
 *
 * <p>Topology (one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → FROST (alias {@code
 * frost}) → PostGIS (alias {@code database}). Skipped when Docker is unavailable or when the NAR
 * has not been built.
 */
class NifiFrostFindOrCreateIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18447;
  private static final String FROST_PATH = "/FROST-Server/v1.1";

  private static final String THINGS_TOPIC = "civitas/it/frost-things";
  private static final String TREE_TOPIC = "civitas/it/frost-tree";
  private static final String OBS_TOPIC = "civitas/it/frost-obs";
  private static final String NO_DS_TOPIC = "civitas/it/frost-obs-nods";

  private static final String THING_REFERENCE = "STATION-IT-1";
  private static final String TREE_REFERENCE = "STATION-IT-TREE";
  private static final String DS_REFERENCE = "DS-REF-IT";
  private static final String DS_THING_REFERENCE = "T-REF-IT";
  private static final String MISSING_DS_REFERENCE = "DS-REF-MISSING";

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
    Optional<Path> nar = frostNar();
    assumeTrue(
        nar.isPresent(),
        "the PutFrostRecord NAR is not built — run 'mvn -f nifi-extensions/pom.xml package' or set"
            + " -Dfrost.nar=<path>");

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

    // NiFi loads the NAR at startup. Without it the processor does not exist and the deployment
    // fails — hence the explicit skip above rather than a failure that reads like an adapter
    // defect.
    startNifi(HOST_PORT, network, container -> installFrostNar(container, nar.get()));
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
  void thingsPort_createsTheThingOnceAndUpdatesItOnARepeatedReference() throws Exception {
    deploy("pipeline-frost-things-it", THINGS_TOPIC, SinkPort.THINGS);

    try (MqttPublisher publisher = publisher("civitas-it-frost-things")) {
      // The first delivery creates the Thing.
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(THINGS_TOPIC, records(thing(THING_REFERENCE, "Station IT 1")));
                return countThings(THING_REFERENCE) >= 1;
              });

      // A changed record with the same reference must patch the existing Thing, never create a
      // duplicate. Deliberate pacing dwell: the no-duplicate assertion is negative, so it only
      // means something once every delivery has been processed. The sleep spaces the five
      // deliveries out to guarantee that; an await here would return on the first success.
      for (int i = 0; i < 5; i++) {
        publisher.publish(THINGS_TOPIC, records(thing(THING_REFERENCE, "Station IT 1 updated")));
        Thread.sleep(Duration.ofSeconds(2).toMillis());
      }
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(2))
          .untilAsserted(
              () -> {
                assertEquals(
                    1, countThings(THING_REFERENCE), "the upsert must not duplicate the Thing");
                assertEquals(
                    "Station IT 1 updated",
                    thingName(THING_REFERENCE),
                    "the existing Thing must be updated by reference");
              });
    }
  }

  @Test
  void thingTreePort_writesTheEntitySetTheGeneratedGraphWrote() throws Exception {
    deploy("pipeline-frost-tree-it", TREE_TOPIC, SinkPort.THING_TREE);

    try (MqttPublisher publisher = publisher("civitas-it-frost-tree")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TREE_TOPIC, records(tree(TREE_REFERENCE, 21.5)));
                return countDatastreams(TREE_REFERENCE + "-DS", TREE_REFERENCE) >= 1;
              });

      // The retained message is re-delivered on every reconnect and the await above publishes
      // repeatedly, so the Thing, the Location and the Datastream are upserted many times. Exactly
      // one of each must exist — that is the property the generated graph had and the port keeps.
      await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(2))
          .untilAsserted(
              () -> {
                assertEquals(1, countThings(TREE_REFERENCE), "one Thing for the reference");
                assertEquals(
                    1,
                    countLocations(TREE_REFERENCE),
                    "one Location, carrying the Thing's reference");
                assertEquals(
                    1,
                    countDatastreams(TREE_REFERENCE + "-DS", TREE_REFERENCE),
                    "one Datastream for the reference");
              });
    }
  }

  @Test
  void observationsPort_appendsToTheDatastreamTheRecordNames() throws Exception {
    // Pre-provision a Datastream the record refers to; the port must resolve it by its reference
    // block and post the Observation into it.
    long datastreamId = createDatastream();
    assertEquals(
        1,
        countDatastreams(DS_REFERENCE, DS_THING_REFERENCE),
        "FROST must resolve the Datastream by its reference block");

    deploy("pipeline-frost-obs-it", OBS_TOPIC, SinkPort.OBSERVATIONS);

    try (MqttPublisher publisher = publisher("civitas-it-frost-obs")) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(
                    OBS_TOPIC, records(observation(DS_THING_REFERENCE, DS_REFERENCE, 21.5)));
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
  void observationsPort_withoutAMatchingDatastream_writesNothing() throws Exception {
    // The port writes its own entity only. With no Datastream behind the reference the record goes
    // to the error sink; it must never provoke a Datastream nobody modelled. A shared FROST means
    // the global Observation count is churned by the sibling tests, so the isolation-safe proof is
    // that no Datastream ever appears for the missing reference.
    assertEquals(
        0,
        countDatastreams(MISSING_DS_REFERENCE, DS_THING_REFERENCE),
        "precondition: the referenced Datastream must not exist");

    deploy("pipeline-frost-nods-it", NO_DS_TOPIC, SinkPort.OBSERVATIONS);

    try (MqttPublisher publisher = publisher("civitas-it-frost-nods")) {
      // Deliberate dwell: this proves an absence, so there is no condition that can complete early
      // and shortening the window would only narrow the chance to observe the Datastream appearing.
      for (int i = 0; i < 10; i++) {
        publisher.publish(
            NO_DS_TOPIC, records(observation(DS_THING_REFERENCE, MISSING_DS_REFERENCE, 9.9)));
        Thread.sleep(3000);
        assertEquals(
            0,
            countDatastreams(MISSING_DS_REFERENCE, DS_THING_REFERENCE),
            "the Pipeline must not create a Datastream for an unmatched reference");
      }
    }
  }

  /** Deploys an MQTT→FROST Pipeline on the given topic, writing through the given port. */
  private void deploy(String pipeline, String topic, SinkPort port) throws Exception {
    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    pipeline,
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", topic),
                    SinkType.FROST,
                    Map.of(
                        FrostSinkStage.FROST_BASE_URL,
                        "http://frost:8080" + FROST_PATH,
                        FrostSinkStage.FROST_PROJECT_ID,
                        String.valueOf(projectId),
                        FrostSinkStage.FROST_PORT,
                        port.label()),
                    List.of(),
                    Map.of(),
                    null,
                    null));
    client.deployFlow(new DeploymentPlan(pipeline, snapshot, Map.of()));
  }

  private MqttPublisher publisher(String clientId) throws Exception {
    return new MqttPublisher("tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883), clientId);
  }

  /**
   * The message a source delivers: the sink splits a record array into one record per atomicity
   * group, so a message carries an array even when it holds one record.
   */
  private static String records(String record) {
    return "[" + record + "]";
  }

  private static String thing(String reference, String name) {
    return "{\"name\":\""
        + name
        + "\",\"description\":\"find-or-create IT\","
        + "\"properties\":{\"reference\":\""
        + reference
        + "\"}}";
  }

  /** The Thing/Location/Datastream/measurement chain the generated graph wrote. */
  private static String tree(String reference, double result) {
    return "{\"name\":\"Tree station\",\"description\":\"thing-tree IT\","
        + "\"properties\":{\"reference\":\""
        + reference
        + "\"},"
        + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
        + "\"encodingType\":\"application/geo+json\","
        + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}],"
        + "\"Datastreams\":[{\"name\":\"Temperature\",\"description\":\"ds\","
        + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
        + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\","
        + "\"definition\":\"ucum:Cel\"},"
        + "\"properties\":{\"reference\":\""
        + reference
        + "-DS\"},"
        + "\"Sensor\":{\"name\":\"Sensor-IT\",\"description\":\"s\","
        + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
        + "\"ObservedProperty\":{\"name\":\"Temperature\","
        + "\"definition\":\"http://example.org/temp\",\"description\":\"t\"},"
        + "\"Observations\":[{\"result\":"
        + result
        + ",\"phenomenonTime\":\"2026-01-01T00:00:00Z\"}]}]}";
  }

  private static String observation(
      String thingReference, String datastreamReference, double result) {
    return "{\"result\":"
        + result
        + ",\"phenomenonTime\":\"2026-01-01T00:00:00Z\","
        + "\"parameters\":{\"thingReference\":\""
        + thingReference
        + "\",\"datastreamReference\":\""
        + datastreamReference
        + "\"}}";
  }

  /** Dumps the NiFi bulletin board (errors/warnings from the deployed flow) for diagnostics. */
  private String bulletins() throws Exception {
    String token = client.authenticate();
    Request request =
        new Request.Builder()
            .url("https://" + dockerHost + ":" + HOST_PORT + "/nifi-api/flow/bulletin-board")
            .header("Authorization", "Bearer " + token)
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      return response.body().string();
    }
  }

  private String frostUrl(String path) {
    return "http://" + dockerHost + ":" + frost.getMappedPort(8080) + FROST_PATH + path;
  }

  private static String filter(String term) {
    return "?$filter=" + URLEncoder.encode(term, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private int count(String path) throws Exception {
    return entities(path).size();
  }

  private com.fasterxml.jackson.databind.JsonNode entities(String path) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder().uri(URI.create(frostUrl(path))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value");
  }

  /**
   * Counts Things with the given reference inside the IT project — the scoped Pipeline must create
   * them there, not at the server root.
   */
  private int countThings(String reference) throws Exception {
    return count(
        "/Projects("
            + projectId
            + ")/Things"
            + filter("properties/reference eq '" + reference + "'"));
  }

  /** Returns the name of the single Thing identified by reference inside the IT project. */
  private String thingName(String reference) throws Exception {
    return entities(
            "/Projects("
                + projectId
                + ")/Things"
                + filter("properties/reference eq '" + reference + "'"))
        .path(0)
        .path("name")
        .asText();
  }

  /** Counts Locations carrying a reference — the tree writes the Thing's when none is given. */
  private int countLocations(String reference) throws Exception {
    return count("/Locations" + filter("properties/reference eq '" + reference + "'"));
  }

  /** Counts Datastreams matching the port's lookup: own reference, scoped to its Thing. */
  private int countDatastreams(String reference, String thingReference) throws Exception {
    return count(
        "/Datastreams"
            + filter(
                "properties/reference eq '"
                    + reference
                    + "' and properties/thingReference eq '"
                    + thingReference
                    + "'"));
  }

  /** Counts the Observations of a Datastream. */
  private int countObservations(long datastreamId) throws Exception {
    return count("/Datastreams(" + datastreamId + ")/Observations");
  }

  /**
   * Provisions a Datastream inside the IT project and returns its {@code @iot.id}. Two steps: the
   * Thing is created under {@code /Projects(n)/Things} (the port's Datastream lookup filters on
   * {@code Thing/Projects/id}, so the Thing must be project-linked), then the Datastream (with
   * Sensor+ObservedProperty) is nested under that Thing. The Datastream carries the canonical
   * reference block, because that is what the Observations port resolves it by.
   */
  private long createDatastream() throws Exception {
    String thingBody =
        "{\"name\":\"T-IT\",\"description\":\"obs-port IT thing\","
            + "\"properties\":{\"reference\":\""
            + DS_THING_REFERENCE
            + "\"},"
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
        "{\"name\":\"DS-IT\",\"description\":\"obs-port IT\","
            + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
            + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\","
            + "\"definition\":\"ucum:Cel\"},"
            + "\"properties\":{\"reference\":\""
            + DS_REFERENCE
            + "\",\"thingReference\":\""
            + DS_THING_REFERENCE
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

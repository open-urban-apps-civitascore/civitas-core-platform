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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end wiring test for the FROST <b>find-or-create</b> sub-flow built by {@link
 * NifiFlowBuilder}: deploys the real MQTT→find-or-create flow onto an actual NiFi 2.9.0, publishes
 * an STA-envelope message, and asserts against a stubbed HTTP endpoint (WireMock) that NiFi looks
 * the Thing up by reference and POSTs it only when absent. This validates the hand-authored
 * fragments, the lookup URL/Expression-Language and the POST/route wiring on real NiFi. True
 * idempotency against a real FROST-Server is covered by {@code NifiFrostFindOrCreateIT}.
 *
 * <p>Topology (one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → WireMock (alias {@code
 * sink}); the WireMock request journal captures what NiFi did. Each test uses its own base path so
 * the shared WireMock stays isolated. Skipped when Docker is unavailable.
 */
class NifiDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18444;
  // The FROST project id the deployed flows are scoped to (required by the builder).
  private static final String PROJECT_ID = "1";
  private static final String ENVELOPE =
      "{\"things\":[{\"name\":\"Sensor S1\",\"properties\":{\"reference\":\"S1\"}}]}";
  private static final String ENVELOPE_OBS =
      "{\"things\":[],\"observations\":[{\"result\":21.5,"
          + "\"phenomenonTime\":\"2026-01-01T00:00:00Z\","
          + "\"parameters\":{\"reference\":\"DS-REF-1\",\"name\":\"DS-1\"}}]}";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> sink;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void startStack() {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi data-flow IT");

    network = Network.newNetwork();

    mosquitto =
        new GenericContainer<>(DockerImageName.parse("eclipse-mosquitto:2.0"))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883)
            .withCommand("mosquitto", "-c", "/mosquitto-no-auth.conf")
            .waitingFor(Wait.forListeningPort());
    mosquitto.start();

    sink =
        new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.9.2"))
            .withNetwork(network)
            .withNetworkAliases("sink")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200).forStatusCode(404));
    sink.start();

    startNifi(HOST_PORT, network);
  }

  @AfterAll
  static void stopStack() {
    stopNifi();
    if (sink != null) {
      sink.stop();
    }
    if (mosquitto != null) {
      mosquitto.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  @Test
  void findOrCreatePostsNewThingWhenLookupIsEmpty() throws Exception {
    String basePath = "/new";
    stub(getStub(thingsPath(basePath), "{\"value\":[]}"));
    stub(postStub(thingsPath(basePath), 201));
    deployFrost("pipeline-frost-new", "civitas/it/thing-new", basePath);

    // NiFi must look the Thing up by reference and — finding none — POST the Thing.
    publishUntil(
        "civitas/it/thing-new",
        "civitas-it-new",
        () -> postedBodyContains(thingsPath(basePath), "\"reference\":\"S1\""));
  }

  @Test
  void findOrCreateSkipsPostWhenThingExists() throws Exception {
    String basePath = "/exists";
    stub(getStub(thingsPath(basePath), "{\"value\":[{\"@iot.id\":42}]}"));
    stub(postStub(thingsPath(basePath), 201));
    deployFrost("pipeline-frost-exists", "civitas/it/thing-exists", basePath);

    // The lookup resolves an existing @iot.id, so the Thing must NOT be re-created.
    publishUntil(
        "civitas/it/thing-exists", "civitas-it-exists", () -> getReceived(thingsPath(basePath)));
    Thread.sleep(Duration.ofSeconds(5).toMillis());
    assertFalse(
        postedTo(thingsPath(basePath)), "an existing Thing must not be POSTed again (idempotency)");
  }

  @Test
  void findOrCreateLinksObservationToResolvedDatastream() throws Exception {
    String basePath = "/obs";
    stub(getStub(basePath + "/Datastreams", "{\"value\":[{\"@iot.id\":99}]}"));
    stub(postStub(basePath + "/Observations", 201));
    deployFrost("pipeline-frost-obs", "civitas/it/obs", basePath);

    // NiFi resolves the Datastream by reference+name (the lookup query must carry the extracted
    // reference), merges its @iot.id into the observation and POSTs it to /Observations.
    publishUntil(
        "civitas/it/obs",
        "civitas-it-obs",
        ENVELOPE_OBS,
        () ->
            getReceivedWithQuery(basePath + "/Datastreams", "DS-REF-1")
                && postedBodyContains(basePath + "/Observations", "\"@iot.id\":99")
                && postedBodyContains(basePath + "/Observations", "\"result\":21.5"));
  }

  @Test
  void frostWriteFailureRaisesErrorBulletin() throws Exception {
    String basePath = "/fail";
    stub(getStub(thingsPath(basePath), "{\"value\":[]}"));
    stub(postStub(thingsPath(basePath), 500));
    deployFrost("pipeline-frost-fail", "civitas/it/thing-fail", basePath);

    // A failing POST (HTTP 500) must route to the LogMessage error sink (WARN bulletin), not
    // vanish.
    publishUntil("civitas/it/thing-fail", "civitas-it-fail", this::errorSinkRaisedABulletin);
  }

  @Test
  void malformedEnvelopeRaisesErrorBulletinInsteadOfVanishing() throws Exception {
    deployFrost("pipeline-frost-malformed", "civitas/it/malformed", "/malformed");

    // A malformed STA envelope cannot be split; that SplitJson 'failure' must reach the LogMessage
    // error sink (no GET/POST is ever stubbed because the message never gets that far) rather than
    // being silently dropped.
    publishUntil(
        "civitas/it/malformed",
        "civitas-it-malformed",
        "this is not valid json",
        this::errorSinkRaisedABulletin);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  private void deployFrost(String pipelineId, String topic, String basePath) throws Exception {
    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    pipelineId,
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", topic),
                    SinkType.FROST,
                    Map.of(
                        NifiFlowBuilder.FROST_BASE_URL,
                        "http://sink:8080" + basePath,
                        NifiFlowBuilder.FROST_PROJECT_ID,
                        PROJECT_ID),
                    List.of(),
                    Map.of(),
                    null));
    client.deployFlow(new DeploymentPlan(pipelineId, snapshot, Map.of()));
  }

  /** The Thing legs are project-scoped; Datastream/Observation stubs stay at the base path. */
  private static String thingsPath(String basePath) {
    return basePath + "/Projects(" + PROJECT_ID + ")/Things";
  }

  /**
   * Publishes the STA envelope repeatedly until the condition holds (NiFi consumes asynchronously).
   */
  private void publishUntil(String topic, String clientId, AwaitCondition condition)
      throws Exception {
    publishUntil(topic, clientId, ENVELOPE, condition);
  }

  private void publishUntil(String topic, String clientId, String payload, AwaitCondition condition)
      throws Exception {
    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, clientId)) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(topic, payload);
                return condition.check();
              });
    }
  }

  @FunctionalInterface
  private interface AwaitCondition {
    boolean check() throws Exception;
  }

  private static String getStub(String urlPath, String jsonBody) {
    return "{\"request\":{\"method\":\"GET\",\"urlPath\":\""
        + urlPath
        + "\"},\"response\":{\"status\":200,\"headers\":{\"Content-Type\":"
        + "\"application/json\"},\"body\":\""
        + jsonBody.replace("\"", "\\\"")
        + "\"}}";
  }

  private static String postStub(String urlPath, int status) {
    return "{\"request\":{\"method\":\"POST\",\"urlPath\":\""
        + urlPath
        + "\"},\"response\":{\"status\":"
        + status
        + ",\"body\":\"{\\\"@iot.id\\\":1}\"}}";
  }

  private void stub(String mappingJson) throws Exception {
    http.send(
        HttpRequest.newBuilder()
            .uri(URI.create(wiremock("/__admin/mappings")))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mappingJson))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private JsonNode journal() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder().uri(URI.create(wiremock("/__admin/requests"))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("requests");
  }

  private boolean postedBodyContains(String path, String substring) throws Exception {
    for (JsonNode entry : journal()) {
      JsonNode request = entry.path("request");
      if ("POST".equals(request.path("method").asText())
          && request.path("url").asText().contains(path)
          && request.path("body").asText().contains(substring)) {
        return true;
      }
    }
    return false;
  }

  private boolean postedTo(String path) throws Exception {
    for (JsonNode entry : journal()) {
      JsonNode request = entry.path("request");
      if ("POST".equals(request.path("method").asText())
          && request.path("url").asText().contains(path)) {
        return true;
      }
    }
    return false;
  }

  private boolean getReceivedWithQuery(String pathSubstring, String querySubstring)
      throws Exception {
    for (JsonNode entry : journal()) {
      JsonNode request = entry.path("request");
      if ("GET".equals(request.path("method").asText())
          && request.path("url").asText().contains(pathSubstring)
          && request.path("url").asText().contains(querySubstring)) {
        return true;
      }
    }
    return false;
  }

  private boolean getReceived(String path) throws Exception {
    for (JsonNode entry : journal()) {
      JsonNode request = entry.path("request");
      if ("GET".equals(request.path("method").asText())
          && request.path("url").asText().contains(path)) {
        return true;
      }
    }
    return false;
  }

  /** Queries the NiFi bulletin board for a WARN bulletin emitted by the LogMessage error sink. */
  private boolean errorSinkRaisedABulletin() throws Exception {
    String token = client.authenticate();
    try (Response response =
        httpClient
            .target("https://" + dockerHost + ":" + HOST_PORT + "/nifi-api/flow/bulletin-board")
            .request()
            .header("Authorization", "Bearer " + token)
            .get()) {
      JsonNode bulletins =
          mapper
              .readTree(response.readEntity(String.class))
              .path("bulletinBoard")
              .path("bulletins");
      for (JsonNode entry : bulletins) {
        JsonNode bulletin = entry.path("bulletin");
        if (bulletin.path("sourceName").asText().contains("LogMessage")
            || bulletin.path("message").asText().contains("civitas-pipeline-dlq")) {
          return true;
        }
      }
    }
    return false;
  }

  private static String wiremock(String path) {
    return "http://" + dockerHost + ":" + sink.getMappedPort(8080) + path;
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

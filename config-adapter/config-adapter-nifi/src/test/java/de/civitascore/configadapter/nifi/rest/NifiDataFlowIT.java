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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end DATA-CORRECTNESS integration test: deploys the real MQTT→mapping→HTTP(FROST) flow
 * built by {@link NifiFlowBuilder} onto an actual NiFi 2.9.0, publishes a known MQTT message, and
 * asserts that the message that reaches the sink has been correctly transformed by the RecordPath
 * mapping (field copy + concat). Unlike {@link NifiDeploymentIT} (which only checks
 * deploy/lifecycle), this verifies the deployed flow actually transforms data as specified — so a
 * broken mapping fails it.
 *
 * <p>Topology (all on one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → WireMock (alias
 * {@code sink}); the WireMock request journal captures what NiFi posted. Skipped when Docker is
 * unavailable.
 */
class NifiDataFlowIT {

  private static final int HOST_PORT = 18444;
  private static final String USER = "admin";
  private static final String PASSWORD = "ctsNiFiTestPassword123";
  private static final String TOPIC = "civitas/it/data";
  private static final String DLQ_TOPIC = "civitas/it/dlq";
  private static final String DLQ_SINK_PATH = "/dlq-observations";
  private static final String CONST_TOPIC = "civitas/it/const";
  private static final String CONST_SINK_PATH = "/const-observations";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> sink;
  private static FixedHostPortGenericContainer<?> nifi;
  private static Client httpClient;
  private static NifiRestClient client;

  /**
   * Host on which published container ports are reachable. In CI the Docker daemon is remote
   * (DinD), so this resolves to the Docker host rather than localhost.
   */
  private static String dockerHost;

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void startStack() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping NiFi data-flow IT");

    dockerHost = DockerClientFactory.instance().dockerHostIpAddress();

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

    nifi =
        new FixedHostPortGenericContainer<>("apache/nifi:2.9.0")
            .withFixedExposedPort(HOST_PORT, 8443)
            .withNetwork(network)
            .withEnv("SINGLE_USER_CREDENTIALS_USERNAME", USER)
            .withEnv("SINGLE_USER_CREDENTIALS_PASSWORD", PASSWORD)
            .withEnv("NIFI_WEB_HTTPS_PORT", "8443")
            .withEnv(
                "NIFI_WEB_PROXY_HOST", dockerHost + ":" + HOST_PORT + ",localhost:" + HOST_PORT)
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
  void deployedFlowTransformsAndDeliversTheMessage() throws Exception {
    // The mapping: copy station_id through, and build label = concat(station_id, '-', sensor).
    MappingConfigParser parser = new MappingConfigParser();
    RecordPathCompiler compiler = new RecordPathCompiler();
    List<UpdateRecordProperty> mapping =
        compiler.compile(
            parser.parse(
                mapper.readTree(
                    """
                    { "fields": {
                        "$.station_id": "$.station_id",
                        "$.label": { "op": "concat",
                          "inputs": [ "$.station_id", { "op": "const", "value": "-" }, "$.sensor" ] }
                    } }
                    """)));

    String snapshot =
        new NifiFlowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-dataflow-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", TOPIC),
                    SinkType.FROST,
                    Map.of(
                        "HTTP Method",
                        "POST",
                        "HTTP URL",
                        "http://sink:8080/observations",
                        "Request Content-Type",
                        "application/json"),
                    mapping,
                    Map.of()));

    client.deployFlow(new DeploymentPlan("pipeline-dataflow-it", snapshot, Map.of()));

    // Publish a known message and wait until NiFi posts the *transformed* record to the sink.
    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-publisher")) {
      await()
          .atMost(Duration.ofSeconds(90))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, "{\"station_id\":\"S1\",\"sensor\":\"A\"}");
                return sinkReceivedTransformedRecord();
              });
    }
  }

  @Test
  void deployedFlowAppliesConstAlongsideCopy() throws Exception {
    // A mapping that mixes a copy (station_id) and a const (unit="celsius") in ONE UpdateRecord —
    // proves the const-as-RecordPath-literal rendering actually evaluates on real NiFi.
    MappingConfigParser parser = new MappingConfigParser();
    RecordPathCompiler compiler = new RecordPathCompiler();
    List<UpdateRecordProperty> mapping =
        compiler.compile(
            parser.parse(
                mapper.readTree(
                    """
                    { "fields": {
                        "$.station_id": "$.station_id",
                        "$.unit": { "op": "const", "value": "celsius" }
                    } }
                    """)));

    String snapshot =
        new NifiFlowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-const-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", CONST_TOPIC),
                    SinkType.FROST,
                    Map.of(
                        "HTTP Method",
                        "POST",
                        "HTTP URL",
                        "http://sink:8080" + CONST_SINK_PATH,
                        "Request Content-Type",
                        "application/json"),
                    mapping,
                    Map.of()));

    client.deployFlow(new DeploymentPlan("pipeline-const-it", snapshot, Map.of()));

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-const-publisher")) {
      await()
          .atMost(Duration.ofSeconds(90))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(CONST_TOPIC, "{\"station_id\":\"S2\"}");
                return sinkReceivedConstAndCopy();
              });
    }
  }

  /** Asserts the sink got the copied station_id AND the injected const unit. */
  private boolean sinkReceivedConstAndCopy() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "http://"
                            + dockerHost
                            + ":"
                            + sink.getMappedPort(8080)
                            + "/__admin/requests"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    for (JsonNode entry : mapper.readTree(response.body()).path("requests")) {
      JsonNode request = entry.path("request");
      if (!request.path("url").asText().contains(CONST_SINK_PATH)) {
        continue;
      }
      String body = request.path("body").asText();
      assertTrue(body.contains("\"station_id\":\"S2\""), "copy missing in: " + body);
      assertTrue(body.contains("\"unit\":\"celsius\""), "const not injected in: " + body);
      return true;
    }
    return false;
  }

  @Test
  void malformedRecordIsRoutedToErrorSinkNotToTheRealSink() throws Exception {
    // A copy-only mapping; the point of this test is the failure path, not the transform.
    MappingConfigParser parser = new MappingConfigParser();
    RecordPathCompiler compiler = new RecordPathCompiler();
    List<UpdateRecordProperty> mapping =
        compiler.compile(
            parser.parse(
                mapper.readTree("{ \"fields\": { \"$.station_id\": \"$.station_id\" } }")));

    String snapshot =
        new NifiFlowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-dlq-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", DLQ_TOPIC),
                    SinkType.FROST,
                    Map.of(
                        "HTTP Method",
                        "POST",
                        "HTTP URL",
                        "http://sink:8080" + DLQ_SINK_PATH,
                        "Request Content-Type",
                        "application/json"),
                    mapping,
                    Map.of()));

    client.deployFlow(new DeploymentPlan("pipeline-dlq-it", snapshot, Map.of()));

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-dlq-publisher")) {
      // A non-JSON payload fails JSON record conversion in ConvertRecord; its 'failure'
      // relationship must route to the LogMessage error sink (which raises a WARN bulletin),
      // NOT be dropped silently.
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(DLQ_TOPIC, "this-is-not-valid-json");
                return errorSinkRaisedABulletin();
              });
    }

    // ...and the malformed record never reached the real (HTTP) sink.
    assertFalse(
        sinkReceivedRequestTo(DLQ_SINK_PATH),
        "a record that fails conversion must not be delivered to the sink");
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

  /** Whether the WireMock sink journal recorded any request to the given path. */
  private boolean sinkReceivedRequestTo(String path) throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "http://"
                            + dockerHost
                            + ":"
                            + sink.getMappedPort(8080)
                            + "/__admin/requests"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    for (JsonNode entry : mapper.readTree(response.body()).path("requests")) {
      if (entry.path("request").path("url").asText().contains(path)) {
        return true;
      }
    }
    return false;
  }

  /** Queries the WireMock request journal and asserts a posted body carries the mapped fields. */
  private boolean sinkReceivedTransformedRecord() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "http://"
                            + dockerHost
                            + ":"
                            + sink.getMappedPort(8080)
                            + "/__admin/requests"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    JsonNode requests = mapper.readTree(response.body()).path("requests");
    for (JsonNode entry : requests) {
      JsonNode request = entry.path("request");
      if (!request.path("url").asText().contains("/observations")) {
        continue;
      }
      String body = request.path("body").asText();
      // the mapping must have run: station_id copied through AND label = "S1-A" produced by concat
      assertTrue(body.contains("\"station_id\":\"S1\""), "station_id missing in: " + body);
      assertTrue(body.contains("\"label\":\"S1-A\""), "concat mapping not applied in: " + body);
      return true;
    }
    return false;
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

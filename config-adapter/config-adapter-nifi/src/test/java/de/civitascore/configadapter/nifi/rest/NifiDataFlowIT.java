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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.NifiTestFixtures;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import okhttp3.Request;
import okhttp3.Response;
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
import org.testcontainers.utility.MountableFile;

/**
 * End-to-end wiring test for the FROST sink flow built by {@link NifiFlowBuilder}: deploys the real
 * MQTT → ConvertRecord → SplitJson → PutFrostRecord flow onto an actual NiFi 2.9.0, publishes a
 * record in the structure of the Things port, and asserts against a stubbed HTTP endpoint
 * (WireMock) that the record reaches the FROST batch endpoint, and that a refused or unreadable one
 * reaches the error sink. This validates the fragments and their wiring on real NiFi. What FROST
 * does with the batch is covered against a real FROST server by {@code NifiFrostFindOrCreateIT} and
 * {@code PutFrostRecordIT}.
 *
 * <p>Topology (one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → WireMock (alias {@code
 * sink}); the WireMock request journal captures what NiFi did. Each test uses its own base path so
 * the shared WireMock stays isolated. Skipped when Docker is unavailable.
 */
class NifiDataFlowIT extends AbstractNifiIT {

  private static final int HOST_PORT = 18444;
  // The FROST project id the deployed flows are scoped to (required by the builder).
  private static final String PROJECT_ID = "1";
  private static final String MQTT_TRUSTSTORE_PASSWORD = "mqtt-test-changeit";
  // A record in the structure of the Things port: without a Mapping it must arrive in that shape.
  private static final String THING =
      "{\"name\":\"Sensor S1\",\"description\":\"A sensor\",\"properties\":{\"reference\":\"S1\"}}";

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> sink;
  private static Path mqttTruststore;

  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void startStack() {
    assumeTrue(dockerAvailable(), "Docker not available — skipping NiFi data-flow IT");

    network = Network.newNetwork();

    Path mqttCerts = generateMqttTlsMaterial();
    mqttTruststore = mqttCerts.resolve("mqtt-truststore.p12");

    mosquitto =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
            .withNetwork(network)
            .withNetworkAliases("mqtt")
            .withExposedPorts(1883, 8883)
            .withCopyToContainer(
                MountableFile.forClasspathResource("mosquitto-tls.conf"),
                "/mosquitto/config/mosquitto.conf")
            .withCopyToContainer(
                MountableFile.forHostPath(mqttCerts.resolve("ca.crt")), "/mosquitto/certs/ca.crt")
            .withCopyToContainer(
                MountableFile.forHostPath(mqttCerts.resolve("server.crt")),
                "/mosquitto/certs/server.crt")
            .withCopyToContainer(
                MountableFile.forHostPath(mqttCerts.resolve("server.key"), 0644),
                "/mosquitto/certs/server.key")
            .waitingFor(Wait.forListeningPorts(1883, 8883));
    mosquitto.start();

    sink =
        new GenericContainer<>(DockerImageName.parse(TestContainerImages.WIREMOCK))
            .withNetwork(network)
            .withNetworkAliases("sink")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200).forStatusCode(404));
    sink.start();

    startNifi(
        HOST_PORT,
        network,
        container ->
            container
                .withCopyFileToContainer(
                    MountableFile.forHostPath(mqttTruststore), "/opt/certs/mqtt-truststore.p12")
                // Mirror the apache-nifi-helm node-truststore contract used in deployment.
                .withEnv("TRUSTSTORE_PATH", "/opt/certs/mqtt-truststore.p12")
                .withEnv("TRUSTSTORE_TYPE", "PKCS12")
                .withEnv("TRUSTSTORE_PASSWORD", MQTT_TRUSTSTORE_PASSWORD));
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
  void anMqttRecordReachesTheFrostBatchEndpoint() throws Exception {
    String basePath = "/new";
    stub(batchStub(basePath, 200));
    deployFrost("pipeline-frost-new", "civitas/it/thing-new", basePath);

    // The message becomes a record, the record one batch request carrying its reference.
    publishUntil(
        "civitas/it/thing-new",
        "civitas-it-new",
        () -> postedBodyContains(batchPath(basePath), "S1"));
  }

  @Test
  void tlsMqttUsesNifiEnvironmentTruststoreAndReachesSink() throws Exception {
    String basePath = "/tls";
    String topic = "civitas/it/tls";
    stub(batchStub(basePath, 200));
    deployFrost("pipeline-frost-tls", topic, basePath, "ssl://mqtt:8883", true);

    publishUntilTls(topic, "civitas-it-tls", () -> postedBodyContains(batchPath(basePath), "S1"));
  }

  @Test
  void aBatchFrostRefusesRaisesAnErrorBulletin() throws Exception {
    String basePath = "/fail";
    stub(batchStub(basePath, 400));
    deployFrost("pipeline-frost-fail", "civitas/it/thing-fail", basePath);

    // A refused batch must route the record to the LogMessage error sink (WARN bulletin), not
    // vanish.
    publishUntil("civitas/it/thing-fail", "civitas-it-fail", this::errorSinkRaisedABulletin);
  }

  @Test
  void anUnreadableMessageRaisesAnErrorBulletinInsteadOfVanishing() throws Exception {
    deployFrost("pipeline-frost-malformed", "civitas/it/malformed", "/malformed");

    // A message that is no JSON cannot become a record; the ConvertRecord 'failure' must reach the
    // LogMessage error sink rather than being silently dropped.
    publishUntil(
        "civitas/it/malformed",
        "civitas-it-malformed",
        "this is not valid json",
        this::errorSinkRaisedABulletin);
  }

  // ─── Helpers ──────────────────────────────────────────────────────────────────

  private void deployFrost(String pipelineId, String topic, String basePath) throws Exception {
    deployFrost(pipelineId, topic, basePath, "tcp://mqtt:1883", false);
  }

  private void deployFrost(
      String pipelineId, String topic, String basePath, String brokerUri, boolean tls)
      throws Exception {
    Map<String, String> sourceProperties = new LinkedHashMap<>();
    sourceProperties.put("Broker URI", brokerUri);
    sourceProperties.put("Topic Filter", topic);
    if (tls) {
      sourceProperties.put(
          "SSL Context Service", "${CS:" + MqttSourceStage.MQTT_SSL_CONTEXT_SERVICE + "}");
    }
    String snapshot =
        NifiTestFixtures.flowBuilder()
            .build(
                new FlowBuildSpec(
                    pipelineId,
                    SourceType.MQTT,
                    sourceProperties,
                    SinkType.FROST,
                    Map.of(
                        FrostSinkStage.FROST_BASE_URL,
                        "http://sink:8080" + basePath,
                        FrostSinkStage.FROST_PROJECT_ID,
                        PROJECT_ID,
                        FrostSinkStage.FROST_PORT,
                        "Things"),
                    List.of(),
                    Map.of(),
                    null,
                    null));
    if (tls) {
      // First simulate the deployment bootstrap creating the sensitive Parameter Context. Then
      // redeploy the real, secret-free snapshot below: NiFi must reuse the existing context rather
      // than replacing it with the declaration that deliberately contains no value.
      JsonNode root = mapper.readTree(snapshot);
      boolean passwordParameterFound = false;
      for (JsonNode parameter :
          root.path("parameterContexts")
              .path(MqttTruststoreConfig.DEFAULT_PARAMETER_CONTEXT)
              .path("parameters")) {
        if (MqttTruststoreConfig.DEFAULT_PASSWORD_PARAMETER.equals(
            parameter.path("name").asText())) {
          ((ObjectNode) parameter).put("value", MQTT_TRUSTSTORE_PASSWORD);
          passwordParameterFound = true;
          break;
        }
      }
      if (!passwordParameterFound) {
        throw new IllegalStateException(
            "TLS flow does not declare " + MqttTruststoreConfig.DEFAULT_PASSWORD_PARAMETER);
      }
      client.deployFlow(new DeploymentPlan(pipelineId, mapper.writeValueAsString(root), Map.of()));
    }
    client.deployFlow(new DeploymentPlan(pipelineId, snapshot, Map.of()));
  }

  /** The one endpoint PutFrostRecord writes to: every record of a batch goes there. */
  private static String batchPath(String basePath) {
    return basePath + "/$batch";
  }

  /**
   * Publishes the Things-port record repeatedly until the condition holds (NiFi consumes
   * asynchronously).
   */
  private void publishUntil(String topic, String clientId, AwaitCondition condition)
      throws Exception {
    publishUntil(topic, clientId, THING, condition);
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

  private void publishUntilTls(String topic, String clientId, AwaitCondition condition)
      throws Exception {
    String brokerUrl = "ssl://" + dockerHost + ":" + mosquitto.getMappedPort(8883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, clientId, mqttClientSslContext())) {
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(topic, THING);
                return condition.check();
              });
    }
  }

  private static SSLContext mqttClientSslContext() throws Exception {
    KeyStore truststore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(mqttTruststore)) {
      truststore.load(input, MQTT_TRUSTSTORE_PASSWORD.toCharArray());
    }
    TrustManagerFactory trustManagers =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagers.init(truststore);
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustManagers.getTrustManagers(), null);
    return sslContext;
  }

  /** Generates the CA/server PEM files in a disposable OpenSSL container and a node truststore. */
  private static Path generateMqttTlsMaterial() {
    String publisherHostSan =
        dockerHost.matches("\\d{1,3}(\\.\\d{1,3}){3}") ? ",IP:" + dockerHost : ",DNS:" + dockerHost;
    GenericContainer<?> openssl =
        new GenericContainer<>(DockerImageName.parse("alpine/openssl:3.5.4"))
            .withCreateContainerCmdModifier(command -> command.withEntrypoint("sh"))
            .withCommand(
                "-c",
                "mkdir -p /certs && "
                    + "openssl req -x509 -newkey rsa:2048 -nodes -days 2 "
                    + "-keyout /certs/ca.key -out /certs/ca.crt -subj /CN=Civitas-MQTT-Test-CA && "
                    + "openssl req -newkey rsa:2048 -nodes -keyout /certs/server.key "
                    + "-out /certs/server.csr -subj /CN=mqtt "
                    + "-addext subjectAltName=DNS:mqtt,DNS:localhost,IP:127.0.0.1"
                    + publisherHostSan
                    + " && "
                    + "openssl x509 -req -in /certs/server.csr -CA /certs/ca.crt "
                    + "-CAkey /certs/ca.key -CAcreateserial -days 2 -out /certs/server.crt "
                    + "-copy_extensions copyall && echo PKI_READY && sleep 600")
            .waitingFor(Wait.forLogMessage(".*PKI_READY.*", 1));
    try {
      openssl.start();
      Path dir = Files.createTempDirectory("mqtt-tls-certs");
      openssl.copyFileFromContainer("/certs/ca.crt", dir.resolve("ca.crt").toString());
      openssl.copyFileFromContainer("/certs/server.crt", dir.resolve("server.crt").toString());
      openssl.copyFileFromContainer("/certs/server.key", dir.resolve("server.key").toString());
      runKeytool(
          System.getProperty("java.home") + "/bin/keytool",
          "-importcert",
          "-alias",
          "mqtt-test-ca",
          "-keystore",
          dir.resolve("mqtt-truststore.p12").toString(),
          "-storetype",
          "PKCS12",
          "-storepass",
          MQTT_TRUSTSTORE_PASSWORD,
          "-noprompt",
          "-file",
          dir.resolve("ca.crt").toString());
      return dir;
    } catch (Exception e) {
      throw new IllegalStateException("failed to generate MQTT test PKI", e);
    } finally {
      openssl.stop();
    }
  }

  @FunctionalInterface
  private interface AwaitCondition {
    boolean check() throws Exception;
  }

  /** A batch endpoint that answers with the given status and an empty batch response. */
  private static String batchStub(String basePath, int status) {
    return "{\"request\":{\"method\":\"POST\",\"urlPath\":\""
        + batchPath(basePath)
        + "\"},\"response\":{\"status\":"
        + status
        + ",\"headers\":{\"Content-Type\":\"application/json\"},"
        + "\"body\":\"{\\\"responses\\\":[]}\"}}";
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

  /** Queries the NiFi bulletin board for a WARN bulletin emitted by the LogMessage error sink. */
  private boolean errorSinkRaisedABulletin() throws Exception {
    String token = client.authenticate();
    Request request =
        new Request.Builder()
            .url("https://" + dockerHost + ":" + HOST_PORT + "/nifi-api/flow/bulletin-board")
            .header("Authorization", "Bearer " + token)
            .get()
            .build();
    try (Response response = httpClient.newCall(request).execute()) {
      JsonNode bulletins =
          mapper.readTree(response.body().string()).path("bulletinBoard").path("bulletins");
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
      this(brokerUrl, clientId, null);
    }

    MqttPublisher(String brokerUrl, String clientId, SSLContext sslContext) throws Exception {
      mqtt = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setCleanSession(true);
      if (sslContext != null) {
        options.setSocketFactory(sslContext.getSocketFactory());
        options.setHttpsHostnameVerificationEnabled(true);
      }
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

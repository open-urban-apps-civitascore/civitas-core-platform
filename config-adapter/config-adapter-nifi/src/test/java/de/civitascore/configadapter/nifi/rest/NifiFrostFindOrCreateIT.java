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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.flow.DeploymentPlan;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.SourceType;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
 * DATA-CORRECTNESS integration test for the FROST find-or-create sub-flow against a <b>real
 * FROST-Server</b>: deploys the MQTT→find-or-create flow built by {@link NifiFlowBuilder} onto an
 * actual NiFi 2.9.0, publishes an STA-envelope message repeatedly, and asserts that FROST ends up
 * with exactly ONE Thing for the reference — i.e. the lookup-by-reference really dedups
 * (idempotency) end to end, not just against a mock.
 *
 * <p>Topology (one Docker network): Mosquitto (alias {@code mqtt}) ← NiFi → FROST (alias {@code
 * frost}) → PostGIS (alias {@code database}). Skipped when Docker is unavailable.
 */
class NifiFrostFindOrCreateIT {

  private static final int HOST_PORT = 18447;
  private static final String USER = "admin";
  private static final String PASSWORD = "ctsNiFiTestPassword123";
  private static final String TOPIC = "civitas/it/frost";
  private static final String REFERENCE = "STATION-IT-1";
  private static final String ENVELOPE =
      "{\"things\":[{\"name\":\"Station IT 1\",\"description\":\"find-or-create IT\","
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

  private static Network network;
  private static GenericContainer<?> mosquitto;
  private static GenericContainer<?> postgis;
  private static GenericContainer<?> frost;
  private static FixedHostPortGenericContainer<?> nifi;
  private static Client httpClient;
  private static NifiRestClient client;
  private static String dockerHost;

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  @SuppressWarnings("resource")
  static void startStack() {
    assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker not available — skipping NiFi/FROST find-or-create IT");

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

    postgis =
        new GenericContainer<>(DockerImageName.parse("postgis/postgis:16-3.4-alpine"))
            .withNetwork(network)
            .withNetworkAliases("database")
            .withEnv("POSTGRES_DB", "sensorthings")
            .withEnv("POSTGRES_USER", "sensorthings")
            .withEnv("POSTGRES_PASSWORD", "ChangeMe")
            .waitingFor(
                Wait.forLogMessage(".*database system is ready to accept connections.*", 2));
    postgis.start();

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
  void findOrCreateCreatesThingExactlyOnceAcrossRepeatedMessages() throws Exception {
    String snapshot =
        new NifiFlowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-frost-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", TOPIC),
                    SinkType.FROST,
                    Map.of(NifiFlowBuilder.FROST_BASE_URL, "http://frost:8080" + FROST_PATH),
                    List.of(),
                    Map.of(),
                    null));
    client.deployFlow(new DeploymentPlan("pipeline-frost-it", snapshot, Map.of()));

    String brokerUrl = "tcp://" + dockerHost + ":" + mosquitto.getMappedPort(1883);
    try (MqttPublisher publisher = new MqttPublisher(brokerUrl, "civitas-it-frost")) {
      // The Thing must appear in FROST (created via the find-or-create POST).
      await()
          .atMost(Duration.ofSeconds(120))
          .pollInterval(Duration.ofSeconds(3))
          .ignoreExceptions()
          .until(
              () -> {
                publisher.publish(TOPIC, ENVELOPE);
                return countThings(REFERENCE) >= 1;
              });

      // Keep delivering the same message; find-or-create must reuse the existing Thing, never
      // creating a duplicate.
      for (int i = 0; i < 5; i++) {
        publisher.publish(TOPIC, ENVELOPE);
        Thread.sleep(Duration.ofSeconds(2).toMillis());
      }
      assertEquals(
          1,
          countThings(REFERENCE),
          "find-or-create must keep exactly one Thing for the reference (idempotency)");
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
        new NifiFlowBuilder()
            .build(
                new FlowBuildSpec(
                    "pipeline-frost-obs-it",
                    SourceType.MQTT,
                    Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", OBS_TOPIC),
                    SinkType.FROST,
                    Map.of(NifiFlowBuilder.FROST_BASE_URL, "http://frost:8080" + FROST_PATH),
                    List.of(),
                    Map.of(),
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
    } catch (org.awaitility.core.ConditionTimeoutException e) {
      throw new AssertionError(
          "observation did not land in Datastream "
              + datastreamId
              + ". NiFi bulletins:\n"
              + bulletins(),
          e);
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

  /** Counts FROST Things whose {@code properties/reference} equals the given value. */
  private int countThings(String reference) throws Exception {
    String filter =
        URLEncoder.encode("properties/reference eq '" + reference + "'", StandardCharsets.UTF_8)
            .replace("+", "%20");
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Things?$filter=" + filter)))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return mapper.readTree(response.body()).path("value").size();
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
   * Deep-inserts a Datastream (with Thing+Sensor+ObservedProperty) and returns its {@code @iot.id}.
   */
  private long createDatastream() throws Exception {
    String body =
        "{\"name\":\""
            + DS_NAME
            + "\",\"description\":\"obs-leg IT\","
            + "\"observationType\":\"http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement\","
            + "\"unitOfMeasurement\":{\"name\":\"Celsius\",\"symbol\":\"degC\",\"definition\":\"ucum:Cel\"},"
            + "\"properties\":{\"reference\":\""
            + DS_REFERENCE
            + "\"},"
            + "\"Thing\":{\"name\":\"T-IT\",\"description\":\"obs-leg IT thing\","
            + "\"properties\":{\"reference\":\"T-REF-IT\"},"
            // a Location lets FROST auto-generate the Observation's FeatureOfInterest
            + "\"Locations\":[{\"name\":\"loc\",\"description\":\"loc\","
            + "\"encodingType\":\"application/geo+json\","
            + "\"location\":{\"type\":\"Point\",\"coordinates\":[8.4,49.0]}}]},"
            + "\"Sensor\":{\"name\":\"Sensor-IT\",\"description\":\"s\","
            + "\"encodingType\":\"application/pdf\",\"metadata\":\"http://example.org/s\"},"
            + "\"ObservedProperty\":{\"name\":\"Temperature\","
            + "\"definition\":\"http://example.org/temp\",\"description\":\"t\"}}";
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create(frostUrl("/Datastreams")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    Matcher matcher =
        Pattern.compile("Datastreams\\((\\d+)\\)")
            .matcher(response.headers().firstValue("Location").orElse(""));
    if (matcher.find()) {
      return Long.parseLong(matcher.group(1));
    }
    throw new IllegalStateException(
        "could not determine created Datastream id (status "
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

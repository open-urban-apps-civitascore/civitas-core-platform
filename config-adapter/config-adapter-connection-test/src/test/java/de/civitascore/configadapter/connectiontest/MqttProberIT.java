/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import de.civitascore.configadapter.testsupport.TestContainerImages;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

/** The MQTT prober against a real Mosquitto, over MQTT 3.1.1 and MQTT 5 (HiveMQ). */
class MqttProberIT {

  private static final int ANONYMOUS = 1883;
  private static final int SECURED = 1884;
  private static final int WEBSOCKET = 9001;

  private static final String CONFIG =
      """
      per_listener_settings true
      listener 1883
      allow_anonymous true
      listener 1884
      allow_anonymous false
      password_file /tmp/passwords
      acl_file /mosquitto/config/acl
      listener 9001
      protocol websockets
      allow_anonymous true
      """;

  private static final String ACL =
      """
      user sensor
      topic read sensors/#
      """;

  /**
   * The dynamic security plugin refuses a subscription in the SUBACK; an {@code acl_file} grants it
   * and only withholds the messages.
   */
  private static final String DYNSEC_CONFIG =
      """
      listener 1883
      allow_anonymous false
      plugin /usr/lib/mosquitto_dynamic_security.so
      plugin_opt_config_file /tmp/dynsec.json
      """;

  private static final GenericContainer<?> MOSQUITTO =
      new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
          .withCopyToContainer(Transferable.of(CONFIG), "/mosquitto/config/test.conf")
          .withCopyToContainer(Transferable.of(ACL, 0644), "/mosquitto/config/acl")
          .withCommand(
              "sh",
              "-c",
              "mosquitto_passwd -b -c /tmp/passwords sensor sensor-secret"
                  + " && chmod 0700 /tmp/passwords && chown mosquitto /tmp/passwords"
                  + " && exec mosquitto -c /mosquitto/config/test.conf")
          .withExposedPorts(ANONYMOUS, SECURED, WEBSOCKET)
          .waitingFor(Wait.forListeningPorts(ANONYMOUS, SECURED, WEBSOCKET));

  /**
   * One client, {@code sensor}, that may subscribe to {@code sensors/#} only. The password hash is
   * PBKDF2-SHA512 of {@code sensor-secret}.
   */
  private static final String DYNSEC_STATE =
      """
      {"defaultACLAccess": {"publishClientSend": false, "publishClientReceive": true,
        "subscribe": false, "unsubscribe": true},
       "clients": [{"username": "sensor",
        "password": "JUJXwTHECb/BLy+ZUG8F1SQ8JouSIaBW7TObiWO8KnhA6m6uzWDFCS4fHAWYyFYBS/JfyAk1uu9UsPl8gCuJ5g==",
        "salt": "Y29ubnRlc3QtMTJi", "iterations": 101,
        "roles": [{"rolename": "reader"}]}],
       "groups": [],
       "roles": [{"rolename": "reader",
        "acls": [{"acltype": "subscribePattern", "topic": "sensors/#", "allow": true}]}]}
      """;

  private static final GenericContainer<?> DYNSEC =
      new GenericContainer<>(DockerImageName.parse(TestContainerImages.MOSQUITTO))
          .withCopyToContainer(Transferable.of(DYNSEC_CONFIG), "/mosquitto/config/dynsec.conf")
          .withCopyToContainer(Transferable.of(DYNSEC_STATE, 0666), "/tmp/dynsec.json")
          .withCommand("mosquitto", "-c", "/mosquitto/config/dynsec.conf")
          .withExposedPorts(ANONYMOUS)
          .waitingFor(Wait.forListeningPorts(ANONYMOUS));

  static {
    MOSQUITTO.start();
    DYNSEC.start();
  }

  /** Accepts every host: the container runs on the loopback interface the guard refuses. */
  private static final DestinationCheck ANY_HOST = host -> {};

  private final MqttProber prober = new MqttProber(ANY_HOST, Duration.ofSeconds(5));

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void subscribesAnonymously(String version) throws Exception {
    ProbeResult result =
        prober.probe(
            connector("tcp", ANONYMOUS, version, "sensors/+/temp"), null, new Cancellation());

    assertTrue(result.success(), result.message());
    assertTrue(
        result.message().contains("with MQTT " + version + " and subscribed to 'sensors/+/temp'"),
        result.message());
    assertNull(result.preview());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void subscribesWithUserAndPassword(String version) throws Exception {
    Map<String, Object> connector = connector("mqtt", SECURED, version, "sensors/#");
    connector.put("user", "sensor");
    connector.put("password", "sensor-secret");

    ProbeResult result = prober.probe(connector, null, new Cancellation());

    assertTrue(result.success(), result.message());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void reportsAWrongPasswordWithoutIt(String version) {
    Map<String, Object> connector = connector("tcp", SECURED, version, "sensors/#");
    connector.put("user", "sensor");
    connector.put("password", "wrong-secret");

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class, () -> prober.probe(connector, null, new Cancellation()));

    assertTrue(failure.getMessage().startsWith("The broker did not accept the connection"));
    assertFalse(failure.getMessage().contains("wrong-secret"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void reportsASubscriptionTheBrokerRefuses(String version) {
    Map<String, Object> connector = connector("tcp", ANONYMOUS, version, "forbidden/x");
    connector.put(
        "urls", List.of("tcp://" + DYNSEC.getHost() + ":" + DYNSEC.getMappedPort(ANONYMOUS)));
    connector.put("user", "sensor");
    connector.put("password", "sensor-secret");

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class, () -> prober.probe(connector, null, new Cancellation()));

    assertTrue(
        failure.getMessage().startsWith("The broker refused the subscription to 'forbidden/x'"),
        failure.getMessage());
    assertEquals(Reason.SOURCE_ERROR, failure.reason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void subscribesOverWebSocket(String version) throws Exception {
    ProbeResult result =
        prober.probe(connector("ws", WEBSOCKET, version, "sensors/#"), null, new Cancellation());

    assertTrue(result.success(), result.message());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void reportsABrokerThatIsNotThere(String version) throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    Map<String, Object> connector = connector("tcp", ANONYMOUS, version, "sensors/#");
    connector.put("urls", List.of("tcp://localhost:" + closedPort));
    long begin = System.nanoTime();

    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class, () -> prober.probe(connector, null, new Cancellation()));

    assertTrue(failure.getMessage().startsWith("The broker did not accept the connection"));
    assertTrue(Duration.ofNanos(System.nanoTime() - begin).toSeconds() < 5);
  }

  @Test
  void reportsAnInvalidTopicFilter() {
    ConnectionTestFailure failure =
        assertThrows(
            ConnectionTestFailure.class,
            () ->
                prober.probe(
                    connector("tcp", ANONYMOUS, "3", "sensors/#/temp"), null, new Cancellation()));

    assertTrue(failure.getMessage().contains("sensors/#/temp"), failure.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"3", "5"})
  void aRefusedDestinationIsNeverContacted(String version) throws Exception {
    try (ServerSocketChannel listener = ServerSocketChannel.open()) {
      listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
      // Non-blocking, so that accept() returns null when nobody connected.
      listener.configureBlocking(false);
      Map<String, Object> connector = connector("tcp", ANONYMOUS, version, "sensors/#");
      connector.put(
          "urls",
          List.of(
              "tcp://"
                  + InetAddress.getLoopbackAddress().getHostAddress()
                  + ":"
                  + ((InetSocketAddress) listener.getLocalAddress()).getPort()));
      MqttProber guarded =
          new MqttProber(
              new DestinationGuard(List.of(), List.of(), InetAddress::getAllByName),
              Duration.ofSeconds(5));

      ConnectionTestFailure failure =
          assertThrows(
              ConnectionTestFailure.class,
              () -> guarded.probe(connector, null, new Cancellation()));

      assertEquals(Reason.INVALID_CONNECTOR, failure.reason(), failure.getMessage());
      assertNull(listener.accept(), "no connection was opened");
    }
  }

  private static Map<String, Object> connector(
      String scheme, int containerPort, String version, String topic) {
    Map<String, Object> connector = new HashMap<>();
    connector.put("connectionType", "mqtt");
    connector.put(
        "urls",
        List.of(
            scheme
                + "://"
                + MOSQUITTO.getHost()
                + ":"
                + MOSQUITTO.getMappedPort(containerPort)
                + ("ws".equals(scheme) ? "/mqtt" : "")));
    connector.put("topics", List.of(topic));
    connector.put("qos", 1);
    connector.put("protocol_version", version);
    connector.put("connect_timeout", "3s");
    return connector;
  }
}

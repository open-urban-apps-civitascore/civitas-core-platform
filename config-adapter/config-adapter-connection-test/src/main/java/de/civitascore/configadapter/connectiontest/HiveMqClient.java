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

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientTransportConfig;
import com.hivemq.client.mqtt.MqttClientTransportConfigBuilder;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3SubAckException;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5ConnAckException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5SubAckException;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5RetainHandling;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Subscribes over MQTT 3.1.1 or MQTT 5 with the HiveMQ client, using the JVM's trust and host name
 * verification. Messages that arrive before the disconnect are dropped.
 */
final class HiveMqClient {

  /**
   * MQTT 5 only: a retained message may arrive right after the SUBACK. The MQTT 3 client offers no
   * limit for incoming packets.
   */
  private static final int MAX_PACKET_SIZE = 64 * 1024;

  /**
   * Connects, subscribes and disconnects.
   *
   * @return the QoS the broker granted
   * @throws ConnectionTestFailure if the broker refuses the connection or the subscription
   */
  int subscribe(MqttSubscription subscription, Cancellation cancellation)
      throws ConnectionTestFailure {
    MqttQos qos = MqttQos.fromCode(subscription.qos());
    if (qos == null) {
      throw ConnectionTestFailure.invalid("MQTT 'qos' must be 0, 1 or 2.");
    }
    return subscription.mqtt5()
        ? subscribeMqtt5(subscription, qos, cancellation)
        : subscribeMqtt3(subscription, qos, cancellation);
  }

  private static int subscribeMqtt3(
      MqttSubscription subscription, MqttQos qos, Cancellation cancellation)
      throws ConnectionTestFailure {
    Mqtt3Subscribe subscribe;
    try {
      subscribe = Mqtt3Subscribe.builder().topicFilter(subscription.topic()).qos(qos).build();
    } catch (IllegalArgumentException e) {
      throw invalidTopic(subscription, e);
    }
    Mqtt3Connect connect = connectMqtt3(subscription);
    Mqtt3AsyncClient client =
        MqttClient.builder()
            .useMqttVersion3()
            .identifier(clientId())
            .transportConfig(transport(subscription))
            .buildAsync();
    return exchange(
        subscription,
        cancellation,
        client::disconnect,
        () -> client.connect(connect),
        () ->
            client
                .subscribe(subscribe, publish -> {})
                .thenApply(subAck -> grantedMqtt3(subscription, subAck)),
        failed -> failureMqtt3(subscription, failed));
  }

  private static int subscribeMqtt5(
      MqttSubscription subscription, MqttQos qos, Cancellation cancellation)
      throws ConnectionTestFailure {
    Mqtt5Subscribe subscribe;
    try {
      // No retained messages: the test needs only the SUBACK.
      subscribe =
          Mqtt5Subscribe.builder()
              .topicFilter(subscription.topic())
              .qos(qos)
              .retainHandling(Mqtt5RetainHandling.DO_NOT_SEND)
              .build();
    } catch (IllegalArgumentException e) {
      throw invalidTopic(subscription, e);
    }
    Mqtt5Connect connect = connectMqtt5(subscription);
    Mqtt5AsyncClient client =
        MqttClient.builder()
            .useMqttVersion5()
            .identifier(clientId())
            .transportConfig(transport(subscription))
            .buildAsync();
    return exchange(
        subscription,
        cancellation,
        client::disconnect,
        () -> client.connect(connect),
        () ->
            client
                .subscribe(subscribe, publish -> {})
                .thenApply(subAck -> grantedMqtt5(subscription, subAck)),
        failed -> failureMqtt5(subscription, failed));
  }

  /**
   * Connects and subscribes within the subscription's timeout, then always disconnects. The no-op
   * message callback keeps the client from logging a warning per early message.
   */
  private static int exchange(
      MqttSubscription subscription,
      Cancellation cancellation,
      Runnable disconnect,
      Supplier<CompletableFuture<?>> connect,
      Supplier<CompletableFuture<Integer>> subscribe,
      Function<ExecutionException, ConnectionTestFailure> failure)
      throws ConnectionTestFailure {
    cancellation.register(disconnect::run);
    long deadline = System.nanoTime() + subscription.timeout().toNanos();
    AtomicBoolean ended = new AtomicBoolean();
    CompletableFuture<?> connecting = connect.get();
    // disconnect() is a no-op before the CONNACK, so a late connection is closed once it completes.
    connecting.whenComplete(
        (connAck, error) -> {
          if (error == null && ended.get()) {
            disconnect.run();
          }
        });
    try {
      await(connecting, deadline);
      return await(subscribe.get(), deadline);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ConnectionTestFailure refused) {
        throw new ConnectionTestFailure(refused.reason(), refused.getMessage(), e);
      }
      throw failure.apply(e);
    } catch (TimeoutException e) {
      throw ConnectionTestFailure.timeout("The broker did not answer in time.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw ConnectionTestFailure.timeout("The test was stopped.", e);
    } finally {
      ended.set(true);
      disconnect.run();
    }
  }

  private static Mqtt3Connect connectMqtt3(MqttSubscription subscription)
      throws ConnectionTestFailure {
    var connect =
        Mqtt3Connect.builder().cleanSession(true).keepAlive(subscription.keepAliveSeconds());
    if (subscription.user() == null) {
      if (subscription.password() != null) {
        throw ConnectionTestFailure.invalid("MQTT 3 sends a password only with a user name.");
      }
      return connect.build();
    }
    var auth = Mqtt3SimpleAuth.builder().username(subscription.user());
    return connect
        .simpleAuth(
            subscription.password() == null
                ? auth.build()
                : auth.password(password(subscription)).build())
        .build();
  }

  private static Mqtt5Connect connectMqtt5(MqttSubscription subscription) {
    Mqtt5ConnectBuilder connect =
        Mqtt5Connect.builder()
            .cleanStart(true)
            .keepAlive(subscription.keepAliveSeconds())
            .restrictions()
            .maximumPacketSize(MAX_PACKET_SIZE)
            .applyRestrictions();
    if (subscription.user() == null && subscription.password() == null) {
      return connect.build();
    }
    Mqtt5SimpleAuthBuilder.Complete auth =
        subscription.user() != null
            ? Mqtt5SimpleAuth.builder().username(subscription.user())
            : Mqtt5SimpleAuth.builder().password(password(subscription));
    if (subscription.user() != null && subscription.password() != null) {
      auth = auth.password(password(subscription));
    }
    return connect.simpleAuth(auth.build()).build();
  }

  private static byte[] password(MqttSubscription subscription) {
    return subscription.password().getBytes(StandardCharsets.UTF_8);
  }

  private static int grantedMqtt3(MqttSubscription subscription, Mqtt3SubAck subAck) {
    Mqtt3SubAckReturnCode code = subAck.getReturnCodes().get(0);
    if (code.isError()) {
      throw new CompletionException(refused(subscription, code.toString(), null));
    }
    return code.getCode();
  }

  private static int grantedMqtt5(MqttSubscription subscription, Mqtt5SubAck subAck) {
    Mqtt5SubAckReasonCode code = subAck.getReasonCodes().get(0);
    if (code.isError()) {
      throw new CompletionException(refused(subscription, code.toString(), null));
    }
    return code.getCode();
  }

  private static ConnectionTestFailure failureMqtt3(
      MqttSubscription subscription, ExecutionException failed) {
    Throwable cause = failed.getCause();
    if (cause instanceof Mqtt3ConnAckException connAck) {
      return notAccepted(String.valueOf(connAck.getMqttMessage().getReturnCode()), failed);
    }
    if (cause instanceof Mqtt3SubAckException subAck) {
      return refused(
          subscription, String.valueOf(subAck.getMqttMessage().getReturnCodes()), failed);
    }
    return notAccepted(ConnectionTestFailure.describe(cause), failed);
  }

  private static ConnectionTestFailure failureMqtt5(
      MqttSubscription subscription, ExecutionException failed) {
    Throwable cause = failed.getCause();
    if (cause instanceof Mqtt5ConnAckException connAck) {
      return notAccepted(String.valueOf(connAck.getMqttMessage().getReasonCode()), failed);
    }
    if (cause instanceof Mqtt5SubAckException subAck) {
      return refused(
          subscription, String.valueOf(subAck.getMqttMessage().getReasonCodes()), failed);
    }
    return notAccepted(ConnectionTestFailure.describe(cause), failed);
  }

  private static ConnectionTestFailure notAccepted(String reason, Throwable cause) {
    return new ConnectionTestFailure("The broker did not accept the connection: " + reason, cause);
  }

  private static ConnectionTestFailure refused(
      MqttSubscription subscription, String code, Throwable cause) {
    return new ConnectionTestFailure(
        "The broker refused the subscription to '" + subscription.topic() + "': " + code + ".",
        cause);
  }

  /** The client refuses the topic filter before it reaches the broker. */
  private static ConnectionTestFailure invalidTopic(
      MqttSubscription subscription, IllegalArgumentException cause) {
    return ConnectionTestFailure.invalid(
        "The topic filter '" + subscription.topic() + "' is not valid.", cause);
  }

  private static MqttClientTransportConfig transport(MqttSubscription subscription) {
    URI broker = subscription.broker();
    MqttClientTransportConfigBuilder transport =
        MqttClientTransportConfig.builder()
            .serverHost(broker.getHost())
            .serverPort(port(broker))
            .socketConnectTimeout(subscription.connectTimeoutSeconds(), TimeUnit.SECONDS)
            .mqttConnectTimeout(subscription.connectTimeoutSeconds(), TimeUnit.SECONDS);
    if (subscription.tls()) {
      transport = transport.sslWithDefaultConfig();
    }
    if (subscription.webSocket()) {
      String path = broker.getRawPath() == null ? "" : broker.getRawPath();
      transport =
          transport
              .webSocketConfig()
              .serverPath(path.startsWith("/") ? path.substring(1) : path)
              .applyWebSocketConfig();
    }
    return transport.build();
  }

  private static int port(URI broker) {
    if (broker.getPort() > 0) {
      return broker.getPort();
    }
    return switch (broker.getScheme()) {
      case "ssl" -> 8883;
      case "ws" -> 80;
      case "wss" -> 443;
      default -> 1883;
    };
  }

  private static <T> T await(CompletableFuture<T> future, long deadline)
      throws ExecutionException, TimeoutException, InterruptedException {
    return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
  }

  /** Random, alphanumeric and within the 23 characters MQTT 3.1.1 asks a broker to accept. */
  private static String clientId() {
    return "civitastest" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}

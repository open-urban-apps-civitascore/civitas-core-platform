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

import de.civitascore.configadapter.connector.ConnectorValueException;
import de.civitascore.configadapter.connector.MqttConnectorValues;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Connects to the first broker and subscribes to the topic. A subscription the broker grants is
 * success; no message is awaited. MQTT 3 is spoken as MQTT 3.1.1.
 */
final class MqttProber implements ConnectionProber {

  /** NiFi's default when the connector sets none. */
  private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

  private final DestinationCheck guard;
  private final Duration timeout;
  private final HiveMqClient client = new HiveMqClient();

  MqttProber(DestinationCheck guard, Duration timeout) {
    this.guard = guard;
    this.timeout = timeout;
  }

  @Override
  public String connectionType() {
    return "mqtt";
  }

  @Override
  public ProbeResult probe(
      Map<String, Object> connector, String apiCall, boolean applyWhere, Cancellation cancellation)
      throws ConnectionTestFailure {
    MqttConnectorValues values;
    try {
      values = MqttConnectorValues.read(connector);
    } catch (ConnectorValueException e) {
      throw ConnectionTestFailure.invalid(e.getMessage(), e);
    }
    URI broker;
    try {
      broker = URI.create(values.brokers().get(0));
    } catch (IllegalArgumentException e) {
      throw ConnectionTestFailure.invalid("The broker URL is not a valid URL.", e);
    }
    guard.check(broker.getHost());
    MqttSubscription subscription =
        new MqttSubscription(
            broker,
            values.topic(),
            qos(connector.get("qos")),
            "5".equals(values.protocolVersion()),
            text(connector.get("user")),
            text(connector.get("password")),
            connectTimeoutSeconds(values),
            values.keepAliveSeconds() == null
                ? DEFAULT_KEEP_ALIVE_SECONDS
                : values.keepAliveSeconds(),
            timeout);
    int granted = client.subscribe(subscription, cancellation);
    return ProbeResult.success(
        "Connected to "
            + broker.getHost()
            + " with MQTT "
            + values.protocolVersion()
            + " and subscribed to '"
            + subscription.topic()
            + "' (granted QoS "
            + granted
            + ").",
        null);
  }

  /** The connector's connect timeout, but never beyond the budget of the test. */
  private int connectTimeoutSeconds(MqttConnectorValues values) {
    int budget = (int) Math.max(1, timeout.toSeconds());
    Integer configured = values.connectTimeoutSeconds();
    return configured == null || configured <= 0 ? budget : Math.min(configured, budget);
  }

  private static int qos(Object value) throws ConnectionTestFailure {
    String text = value == null ? "0" : String.valueOf(value).trim();
    return switch (text) {
      case "0", "1", "2" -> Integer.parseInt(text);
      default -> throw ConnectionTestFailure.invalid("MQTT 'qos' must be 0, 1 or 2.");
    };
  }

  private static String text(Object value) {
    return value == null || String.valueOf(value).isEmpty() ? null : String.valueOf(value);
  }
}

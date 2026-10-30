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

import java.net.URI;
import java.time.Duration;

/**
 * What an MQTT test subscribes with.
 *
 * @param broker the broker URL, transport scheme {@code tcp}/{@code ssl}/{@code ws}/{@code wss}
 * @param topic the topic filter
 * @param qos the requested QoS
 * @param mqtt5 whether to speak MQTT 5 rather than MQTT 3.1.1
 * @param user the user name, or null
 * @param password the password in plain text, or null
 * @param connectTimeoutSeconds the connect timeout
 * @param keepAliveSeconds the keep-alive interval
 * @param timeout the time the whole subscription may take
 */
record MqttSubscription(
    URI broker,
    String topic,
    int qos,
    boolean mqtt5,
    String user,
    String password,
    int connectTimeoutSeconds,
    int keepAliveSeconds,
    Duration timeout) {

  boolean tls() {
    return "ssl".equals(broker.getScheme()) || "wss".equals(broker.getScheme());
  }

  boolean webSocket() {
    return "ws".equals(broker.getScheme()) || "wss".equals(broker.getScheme());
  }

  /** Keeps the password out of logs. */
  @Override
  public String toString() {
    return "MqttSubscription[broker=" + broker + ", topic=" + topic + "]";
  }
}

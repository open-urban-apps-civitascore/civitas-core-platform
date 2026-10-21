/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connector;

import static de.civitascore.configadapter.connector.ConnectorValues.trimmedNonBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The values of an MQTT connector, read and checked for every consumer of an MQTT source.
 * Credentials and the QoS stay in the connector document.
 *
 * @param brokers the broker URLs, with the {@code mqtt}/{@code mqtts} aliases mapped to {@code
 *     tcp}/{@code ssl}; all on one transport
 * @param topic the single topic filter
 * @param tls whether TLS is enabled
 * @param protocolVersion the MQTT major version, {@code "3"} or {@code "5"}
 * @param connectTimeoutSeconds the connect timeout, or null if not configured
 * @param keepAliveSeconds the keep-alive interval, or null if not configured
 */
public record MqttConnectorValues(
    List<String> brokers,
    String topic,
    boolean tls,
    String protocolVersion,
    Integer connectTimeoutSeconds,
    Integer keepAliveSeconds) {

  private static final Set<String> PLAINTEXT_SCHEMES = Set.of("tcp", "ws", "mqtt");
  private static final Set<String> TLS_SCHEMES = Set.of("ssl", "mqtts", "wss");

  /**
   * Paho's TCP and SSL transports reject a broker URI with a path, unlike WebSocket. NiFi does not
   * check paths, so such a URI would deploy and fail only when the processor connects.
   */
  private static final Set<String> PATHLESS_SCHEMES = Set.of("tcp", "ssl", "mqtt", "mqtts");

  /**
   * Maps the {@code mqtt}/{@code mqtts} schemes most brokers advertise to ones Eclipse Paho
   * accepts. Paho has no network module for them
   * (https://github.com/eclipse-paho/paho.mqtt.java/issues/464).
   */
  private static final Map<String, String> SCHEME_ALIASES = Map.of("mqtt", "tcp", "mqtts", "ssl");

  private static final Set<String> PROTOCOL_VERSIONS = Set.of("3", "5");

  private static final Pattern BROKER_SCHEME =
      Pattern.compile("^([a-z][a-z0-9+.-]*)://", Pattern.CASE_INSENSITIVE);

  /** The path of a scheme-stripped broker URL: after host[:port], before any query/fragment. */
  private static final Pattern BROKER_PATH = Pattern.compile("^[^/?#]*([^?#]*)");

  /** The port of a scheme-stripped broker URL, after any userinfo and an IPv6 literal. */
  private static final Pattern BROKER_PORT =
      Pattern.compile("^(?:[^@/?#]*@)?(?:\\[[^\\]]*\\]|[^:/?#]*)(?::(\\d*))?");

  /** MQTT carries the keep-alive, and TCP the port, as an unsigned 16-bit value. */
  private static final int MAX_UNSIGNED_SHORT = 65535;

  /** A plain seconds value, optionally with a seconds unit suffix (e.g. {@code 5}, {@code 5s}). */
  private static final Pattern SECONDS =
      Pattern.compile("(\\d+)\\s*(?:s|sec|secs|second|seconds)?", Pattern.CASE_INSENSITIVE);

  public MqttConnectorValues {
    brokers = List.copyOf(brokers);
  }

  /**
   * Reads the MQTT values of a connector document. Broker URL and topic are required, so that no
   * consumer falls back to a default broker or topic.
   *
   * @throws ConnectorValueException if a value cannot be used
   */
  public static MqttConnectorValues read(Map<String, Object> connector)
      throws ConnectorValueException {
    List<String> brokers = trimmedNonBlank(connector.get("urls"));
    List<String> topics = trimmedNonBlank(connector.get("topics"));
    if (brokers.isEmpty() || topics.isEmpty()) {
      throw new ConnectorValueException("MQTT source requires non-empty 'urls' and 'topics'");
    }
    boolean tls = tlsEnabled(connector.get("tls"));
    brokers = validateAndNormalizeBrokers(brokers, tls);
    // A broker URI list is fine, but one subscription is a single topic filter.
    if (topics.size() > 1) {
      throw new ConnectorValueException(
          "multiple MQTT topics are not supported (one ConsumeMQTT subscribes to a single topic"
              + " filter): "
              + topics);
    }
    Integer keepAlive = seconds("Keep Alive", connector.get("keepalive"));
    if (keepAlive != null && keepAlive > MAX_UNSIGNED_SHORT) {
      throw new ConnectorValueException(
          "MQTT 'Keep Alive' must not exceed " + MAX_UNSIGNED_SHORT + " seconds: " + keepAlive);
    }
    return new MqttConnectorValues(
        brokers,
        topics.get(0),
        tls,
        protocolVersion(connector.get("protocol_version")),
        seconds("Connection Timeout", connector.get("connect_timeout")),
        keepAlive);
  }

  private static boolean tlsEnabled(Object tls) {
    return tls instanceof Map<?, ?> map
        && "true".equalsIgnoreCase(String.valueOf(map.get("enabled")));
  }

  /**
   * A connector without a version speaks MQTT 3.
   *
   * @throws ConnectorValueException if the version is neither 3 nor 5
   */
  private static String protocolVersion(Object value) throws ConnectorValueException {
    String configured = value == null ? "3" : String.valueOf(value).trim();
    if (!PROTOCOL_VERSIONS.contains(configured)) {
      throw new ConnectorValueException("MQTT protocol version must be 3 or 5");
    }
    return configured;
  }

  /**
   * Checks every broker against the TLS switch and maps the scheme aliases. All brokers must end up
   * on one transport, because NiFi's {@code customValidate} fails a processor with mixed schemes.
   */
  private static List<String> validateAndNormalizeBrokers(List<String> brokers, boolean tlsEnabled)
      throws ConnectorValueException {
    List<String> normalized = new ArrayList<>(brokers.size());
    Set<String> expected = tlsEnabled ? TLS_SCHEMES : PLAINTEXT_SCHEMES;
    for (String broker : brokers) {
      Matcher matcher = BROKER_SCHEME.matcher(broker);
      String scheme = matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : "";
      if (!PLAINTEXT_SCHEMES.contains(scheme) && !TLS_SCHEMES.contains(scheme)) {
        throw new ConnectorValueException("unsupported MQTT broker scheme in URL: " + broker);
      }
      if (!expected.contains(scheme)) {
        throw new ConnectorValueException(
            "MQTT broker scheme '" + scheme + "' is inconsistent with tls.enabled=" + tlsEnabled);
      }
      String remainder = broker.substring(matcher.end());
      if (PATHLESS_SCHEMES.contains(scheme) && hasPath(remainder)) {
        throw new ConnectorValueException(
            "MQTT broker scheme '"
                + scheme
                + "' does not accept a path, use a 'ws'/'wss' URL for a path-addressed broker: "
                + broker);
      }
      if (!validPort(remainder)) {
        throw new ConnectorValueException(
            "MQTT broker port must be between 1 and " + MAX_UNSIGNED_SHORT + ": " + broker);
      }
      normalized.add(SCHEME_ALIASES.getOrDefault(scheme, scheme) + "://" + remainder);
    }
    Set<String> transports =
        normalized.stream()
            .map(BROKER_SCHEME::matcher)
            .filter(Matcher::find)
            .map(matcher -> matcher.group(1))
            .collect(Collectors.toUnmodifiableSet());
    if (transports.size() > 1) {
      throw new ConnectorValueException(
          "all MQTT brokers must use the same transport, got " + transports + ": " + normalized);
    }
    return List.copyOf(normalized);
  }

  /** Whether the URL names no port, or one between 1 and 65535. */
  private static boolean validPort(String authorityAndPath) {
    Matcher matcher = BROKER_PORT.matcher(authorityAndPath);
    if (!matcher.find() || matcher.group(1) == null) {
      return true;
    }
    try {
      int port = Integer.parseInt(matcher.group(1));
      return port >= 1 && port <= MAX_UNSIGNED_SHORT;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** A bare {@code /} is what Paho itself produces for an empty path, so it does not count. */
  private static boolean hasPath(String authorityAndPath) {
    Matcher matcher = BROKER_PATH.matcher(authorityAndPath);
    if (!matcher.find()) {
      return false;
    }
    String path = matcher.group(1);
    return !path.isEmpty() && !"/".equals(path);
  }

  /**
   * A portal duration such as {@code "5s"} as whole seconds, or null if blank. Any other value is
   * rejected rather than silently dropped.
   */
  private static Integer seconds(String name, Object value) throws ConnectorValueException {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty()) {
      return null;
    }
    Matcher matcher = SECONDS.matcher(text);
    if (matcher.matches()) {
      try {
        return Integer.valueOf(matcher.group(1));
      } catch (NumberFormatException tooLarge) {
        // falls through to the rejection below
      }
    }
    throw new ConnectorValueException(
        "MQTT '" + name + "' is not a valid seconds duration: " + text);
  }
}

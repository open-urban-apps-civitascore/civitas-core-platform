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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MqttConnectorValuesTest {

  private static Map<String, Object> connector(String url) {
    Map<String, Object> map = new HashMap<>();
    map.put("urls", List.of(url));
    map.put("topics", List.of("sensors/#"));
    return map;
  }

  private static Map<String, Object> tlsConnector(String url) {
    Map<String, Object> map = connector(url);
    map.put("tls", Map.of("enabled", true));
    return map;
  }

  private static ConnectorValueException rejected(Map<String, Object> connector) {
    return assertThrows(ConnectorValueException.class, () -> MqttConnectorValues.read(connector));
  }

  @Test
  void readBuildsValuesWithDefaults() throws ConnectorValueException {
    MqttConnectorValues values = MqttConnectorValues.read(connector("tcp://broker:1883"));

    assertEquals(List.of("tcp://broker:1883"), values.brokers());
    assertEquals("sensors/#", values.topic());
    assertFalse(values.tls());
    assertEquals("3", values.protocolVersion());
    assertNull(values.connectTimeoutSeconds());
    assertNull(values.keepAliveSeconds());
  }

  @Test
  void readRequiresUrlsAndTopics() {
    Map<String, Object> noUrls = connector("tcp://b:1883");
    noUrls.remove("urls");
    Map<String, Object> noTopics = connector("tcp://b:1883");
    noTopics.put("topics", List.of(" "));

    assertTrue(rejected(noUrls).getMessage().contains("'urls' and 'topics'"));
    assertTrue(rejected(noTopics).getMessage().contains("'urls' and 'topics'"));
  }

  @Test
  void mqttSchemeIsAliasedToTcp() throws ConnectorValueException {
    assertEquals(
        List.of("tcp://b:1883"), MqttConnectorValues.read(connector("mqtt://b:1883")).brokers());
  }

  @Test
  void mqttsSchemeIsAliasedToSsl() throws ConnectorValueException {
    MqttConnectorValues values = MqttConnectorValues.read(tlsConnector("mqtts://b:8883"));

    assertEquals(List.of("ssl://b:8883"), values.brokers());
    assertTrue(values.tls());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ssl://b:8883", "mqtts://b:8883", "wss://b:443/mqtt"})
  void tlsSchemesAreRejectedWithoutTlsEnabled(String url) {
    ConnectorValueException e = rejected(connector(url));

    assertTrue(e.getMessage().contains("tls.enabled=false"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"tcp://b:1883", "mqtt://b:1883", "ws://b:80/mqtt"})
  void plaintextSchemesAreRejectedWithTlsEnabled(String url) {
    ConnectorValueException e = rejected(tlsConnector(url));

    assertTrue(e.getMessage().contains("tls.enabled=true"), e.getMessage());
  }

  @Test
  void unsupportedSchemeIsRejected() {
    assertTrue(rejected(connector("http://b")).getMessage().contains("unsupported"));
    assertTrue(rejected(connector("b:1883")).getMessage().contains("unsupported"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tcp://b:1883/mqtt", "mqtt://b:1883/mqtt"})
  void pathIsRejectedOnPlaintextTcpSchemes(String url) {
    ConnectorValueException e = rejected(connector(url));

    assertTrue(e.getMessage().contains("does not accept a path"), e.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ssl://b:8883/mqtt", "mqtts://b:8883/mqtt"})
  void pathIsRejectedOnTlsTcpSchemes(String url) {
    ConnectorValueException e = rejected(tlsConnector(url));

    assertTrue(e.getMessage().contains("does not accept a path"), e.getMessage());
  }

  @Test
  void pathIsAllowedOnWebSocketSchemes() throws ConnectorValueException {
    assertEquals(
        List.of("ws://b:80/mqtt"), MqttConnectorValues.read(connector("ws://b:80/mqtt")).brokers());
    assertEquals(
        List.of("wss://b:443/mqtt"),
        MqttConnectorValues.read(tlsConnector("wss://b:443/mqtt")).brokers());
  }

  @Test
  void bareSlashPathIsAllowed() throws ConnectorValueException {
    assertEquals(
        List.of("tcp://b:1883/"), MqttConnectorValues.read(connector("tcp://b:1883/")).brokers());
  }

  @Test
  void mixedTransportsAreRejected() {
    Map<String, Object> connector = connector("tcp://a:1883");
    connector.put("urls", List.of("tcp://a:1883", "ws://b:80/mqtt"));

    ConnectorValueException e = rejected(connector);

    assertTrue(e.getMessage().contains("same transport"), e.getMessage());
  }

  @Test
  void aliasAndTransportSchemeCountAsOneTransport() throws ConnectorValueException {
    Map<String, Object> connector = connector("tcp://a:1883");
    connector.put("urls", List.of("tcp://a:1883", "mqtt://b:1883"));

    assertEquals(
        List.of("tcp://a:1883", "tcp://b:1883"), MqttConnectorValues.read(connector).brokers());
  }

  @Test
  void moreThanOneTopicIsRejected() {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("topics", List.of("a", "b"));

    assertTrue(rejected(connector).getMessage().contains("multiple MQTT topics"));
  }

  @Test
  void protocolVersionFiveIsAccepted() throws ConnectorValueException {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("protocol_version", "5");

    assertEquals("5", MqttConnectorValues.read(connector).protocolVersion());
  }

  @Test
  void protocolVersionFourIsRejected() {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("protocol_version", "4");

    assertTrue(rejected(connector).getMessage().contains("3 or 5"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"5", "5s", "30 seconds", " 7 SEC "})
  void durationsAreParsedToSeconds(String text) throws ConnectorValueException {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("connect_timeout", text);
    connector.put("keepalive", text);

    MqttConnectorValues values = MqttConnectorValues.read(connector);

    int expected = Integer.parseInt(text.replaceAll("\\D", ""));
    assertEquals(expected, values.connectTimeoutSeconds());
    assertEquals(expected, values.keepAliveSeconds());
  }

  @ParameterizedTest
  @ValueSource(strings = {"5m", "99999999999", "abc", "-5"})
  void invalidDurationsAreRejected(String text) {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("connect_timeout", text);

    ConnectorValueException e = rejected(connector);

    assertTrue(e.getMessage().contains("Connection Timeout"), e.getMessage());
    assertTrue(e.getMessage().contains(text), e.getMessage());
  }

  @Test
  void invalidKeepAliveNamesKeepAlive() {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("keepalive", "5m");

    assertTrue(rejected(connector).getMessage().contains("Keep Alive"));
  }

  @Test
  void blankDurationIsAbsent() throws ConnectorValueException {
    Map<String, Object> connector = connector("tcp://b:1883");
    connector.put("keepalive", "  ");

    assertNull(MqttConnectorValues.read(connector).keepAliveSeconds());
  }
}

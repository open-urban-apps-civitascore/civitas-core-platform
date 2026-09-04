/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MqttTruststoreConfigTest {

  @Test
  void defaultsDescribeTheJdkTruststoreOpenedWithItsConventionalPassword() {
    MqttTruststoreConfig config = MqttTruststoreConfig.jdkTruststore();

    assertEquals("${JAVA_HOME}/lib/security/cacerts", config.path());
    assertEquals("PKCS12", config.type());
    assertEquals("changeit", config.password());
    assertEquals("", config.passwordParameter());
    assertEquals("", config.parameterContext());
    assertTrue(config.hasPassword());
    assertFalse(config.hasPasswordParameter(), "the JDK store needs no Parameter Context");
  }

  @Test
  void aLiteralPasswordIsPushedAfterUploadRatherThanWrittenIntoTheSnapshot() {
    MqttTruststoreConfig config = MqttTruststoreConfig.jdkTruststore();

    assertFalse(
        config.sslContextProperties().containsKey("Truststore Password"),
        "a sensitive value must not travel in the flow definition");
    assertEquals(Map.of("Truststore Password", "changeit"), config.sensitiveProperties());
  }

  @Test
  void theNodeTruststoreStillDescribesTheDeploymentParameterContext() {
    MqttTruststoreConfig config = MqttTruststoreConfig.nodeTruststore();

    assertEquals("${TRUSTSTORE_PATH}", config.path());
    assertEquals("PKCS12", config.type());
    assertEquals("TRUSTSTORE_PASSWORD", config.passwordParameter());
    assertEquals("NiFi Node Truststore", config.parameterContext());
    assertTrue(config.hasPasswordParameter());
    assertEquals(
        "#{TRUSTSTORE_PASSWORD}", config.sslContextProperties().get("Truststore Password"));
    assertEquals(Map.of(), config.sensitiveProperties());
  }

  @Test
  void aPasswordParameterWinsOverALiteral() {
    // Otherwise a deployment that names a parameter would also ship the default literal.
    MqttTruststoreConfig config =
        new MqttTruststoreConfig("/opt/t.p12", "PKCS12", "changeit", "MQTT_PW", "MQTT CAs");

    assertEquals("", config.password());
    assertFalse(config.hasPassword());
    assertEquals(Map.of(), config.sensitiveProperties());
    assertEquals("#{MQTT_PW}", config.sslContextProperties().get("Truststore Password"));
  }

  @Test
  void theNoPasswordSentinelDropsThePasswordParameter() {
    MqttTruststoreConfig config =
        new MqttTruststoreConfig("/opt/mqtt-tls/truststore.p12", "PKCS12", "", "NONE", "");

    assertFalse(config.hasPasswordParameter(), "the sentinel is case-insensitive");
    assertEquals("", config.passwordParameter());
    assertFalse(config.sslContextProperties().containsKey("Truststore Password"));
    assertEquals(Map.of(), config.sensitiveProperties());
  }

  @Test
  void blanksAndNullsNormalizeToEmpty() {
    MqttTruststoreConfig config = new MqttTruststoreConfig(null, "  ", null, "  ", null);

    assertEquals("", config.path());
    assertEquals("", config.type());
    assertEquals("", config.password());
    assertEquals("", config.parameterContext());
    assertFalse(config.hasPasswordParameter());
    assertFalse(config.hasPassword());
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    MqttTruststoreConfig config =
        new MqttTruststoreConfig("  /opt/t.p12 ", " PKCS12 ", "", " MQTT_PW ", " MQTT CAs ");

    assertEquals("/opt/t.p12", config.path());
    assertEquals("PKCS12", config.type());
    assertEquals("MQTT_PW", config.passwordParameter());
    assertEquals("MQTT CAs", config.parameterContext());
  }

  @Test
  void toStringMasksTheLiteralPassword() {
    assertFalse(MqttTruststoreConfig.jdkTruststore().toString().contains("changeit"));
  }
}

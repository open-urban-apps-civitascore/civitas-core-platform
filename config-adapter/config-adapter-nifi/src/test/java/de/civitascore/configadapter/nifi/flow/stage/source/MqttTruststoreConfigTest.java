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

import org.junit.jupiter.api.Test;

class MqttTruststoreConfigTest {

  @Test
  void defaultsDescribeTheNodeTruststoreBehindTheDeploymentParameterContext() {
    MqttTruststoreConfig config = MqttTruststoreConfig.nodeTruststore();

    assertEquals("${TRUSTSTORE_PATH}", config.path());
    assertEquals("PKCS12", config.type());
    assertEquals("TRUSTSTORE_PASSWORD", config.passwordParameter());
    assertEquals("NiFi Node Truststore", config.parameterContext());
    assertTrue(config.hasPasswordParameter());
  }

  @Test
  void theNoPasswordSentinelDropsThePasswordParameter() {
    MqttTruststoreConfig config =
        new MqttTruststoreConfig("/opt/mqtt-tls/truststore.p12", "PKCS12", "NONE", "");

    assertFalse(config.hasPasswordParameter(), "the sentinel is case-insensitive");
    assertEquals("", config.passwordParameter());
  }

  @Test
  void blanksAndNullsNormalizeToEmpty() {
    MqttTruststoreConfig config = new MqttTruststoreConfig(null, "  ", "  ", null);

    assertEquals("", config.path());
    assertEquals("", config.type());
    assertEquals("", config.parameterContext());
    assertFalse(config.hasPasswordParameter());
  }

  @Test
  void surroundingWhitespaceIsStripped() {
    MqttTruststoreConfig config =
        new MqttTruststoreConfig("  /opt/t.p12 ", " PKCS12 ", " MQTT_PW ", " MQTT CAs ");

    assertEquals("/opt/t.p12", config.path());
    assertEquals("PKCS12", config.type());
    assertEquals("MQTT_PW", config.passwordParameter());
    assertEquals("MQTT CAs", config.parameterContext());
  }
}

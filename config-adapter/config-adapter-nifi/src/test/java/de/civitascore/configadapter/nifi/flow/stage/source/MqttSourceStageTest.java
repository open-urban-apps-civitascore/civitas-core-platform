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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MqttSourceStageTest {

  private static final String SOURCE_NODE_KEY = "4f0c2a8e-6b1d-4e7a-9c3f-2d5b8e1a7c90:n-source";

  private final MqttSourceStage stage =
      new MqttSourceStage(
          new CredentialResolver(new byte[0]), MqttTruststoreConfig.nodeTruststore());

  // Without a pinned value, a change to the derivation moves every deployed flow to a new broker
  // session on upgrade.
  @Test
  @DisplayName("gives the same client id on every deploy of a source node")
  void clientIdIsStable() throws Exception {
    assertEquals("civitascore78hgpj3wk2", clientId(SOURCE_NODE_KEY));
  }

  @Test
  @DisplayName("gives distinct client ids to distinct source nodes")
  void clientIdDiffersPerSourceNode() throws Exception {
    assertNotEquals(
        clientId(SOURCE_NODE_KEY), clientId("9a7e3c1b-2d4f-4b6a-8e0c-5f1d3b7a9c2e:n-source"));
  }

  @Test
  @DisplayName("gives a client id every broker must accept")
  void clientIdIsBrokerSafe() throws Exception {
    String clientId = clientId(SOURCE_NODE_KEY);

    assertTrue(clientId.matches("[0-9a-zA-Z]{1,23}"), clientId);
  }

  private String clientId(String sourceNodeKey) throws FatalAdapterException {
    PlanContext out = new PlanContext();
    stage.bind(mqttSource(), sourceNodeKey, out);
    return out.sourceProperties().get("Client ID");
  }

  private static Datasource mqttSource() {
    Datasource source = new Datasource();
    source.setId("a1");
    source.setType("MQTT");
    source.handleUnknownProperty("urls", List.of("tcp://mosquitto:1883"));
    source.handleUnknownProperty("topics", List.of("sensors/+/temp"));
    return source;
  }
}

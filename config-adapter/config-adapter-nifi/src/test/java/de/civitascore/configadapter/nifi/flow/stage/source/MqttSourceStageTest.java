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
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MqttSourceStageTest {

  private static final String PIPELINE_ID = "4f0c2a8e-6b1d-4e7a-9c3f-2d5b8e1a7c90";

  private final MqttSourceStage stage =
      new MqttSourceStage(
          new CredentialResolver(new byte[0]), MqttTruststoreConfig.nodeTruststore());

  // Without a pinned value, a change to the derivation moves every deployed flow to a new broker
  // session on upgrade.
  @Test
  @DisplayName("gives the same client id on every deploy of a source node")
  void clientIdIsStable() throws Exception {
    assertEquals("civitascore78hgpj3wk2", clientId(PIPELINE_ID, node("n-source")));
  }

  @Test
  @DisplayName("gives distinct client ids to equal node ids in different pipelines")
  void clientIdDiffersPerPipeline() throws Exception {
    assertNotEquals(
        clientId(PIPELINE_ID, node("n-source")),
        clientId("9a7e3c1b-2d4f-4b6a-8e0c-5f1d3b7a9c2e", node("n-source")));
  }

  @Test
  @DisplayName("gives a client id every broker must accept")
  void clientIdIsBrokerSafe() throws Exception {
    String clientId = clientId(PIPELINE_ID, node("n-source"));

    assertTrue(clientId.matches("[0-9a-zA-Z]{1,23}"), clientId);
  }

  @Test
  @DisplayName("ignores a client_id left on the datasource")
  void ignoresDatasourceClientId() throws Exception {
    Datasource source = mqttSource();
    source.handleUnknownProperty("client_id", "civitas-nifi-consumer");
    PlanContext out = new PlanContext();

    stage.bind(source, PIPELINE_ID, node("n-source"), out);

    assertEquals(clientId(PIPELINE_ID, node("n-source")), out.sourceProperties().get("Client ID"));
  }

  private String clientId(String pipelineId, GraphNode sourceNode) throws FatalAdapterException {
    PlanContext out = new PlanContext();
    stage.bind(mqttSource(), pipelineId, sourceNode, out);
    return out.sourceProperties().get("Client ID");
  }

  private static GraphNode node(String id) {
    return new GraphNode(id, "source", null, null, null, null);
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

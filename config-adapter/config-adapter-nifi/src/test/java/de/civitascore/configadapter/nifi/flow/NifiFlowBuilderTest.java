/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NifiFlowBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final NifiFlowBuilder builder = new NifiFlowBuilder();

  private List<UpdateRecordProperty> mapping() {
    return List.of(
        new UpdateRecordProperty(
            "/title", "/name", RecordPathCompiler.ReplacementStrategy.RECORD_PATH_VALUE));
  }

  private JsonNode build(FlowBuildSpec spec) throws Exception {
    return mapper.readTree(builder.build(spec));
  }

  private FlowBuildSpec mqttToPostgis(List<UpdateRecordProperty> mapping) {
    return new FlowBuildSpec(
        "pipeline-abc",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.POSTGIS,
        Map.of("Table Name", "sensor_observations"),
        mapping,
        Map.of(
            "PostGISConnectionPool",
            Map.of(
                "Database Connection URL", "jdbc:postgresql://db:5432/x", "Database User", "u")));
  }

  private JsonNode component(JsonNode flow, String array, String typeSuffix) {
    for (JsonNode c : flow.get("flowContents").get(array)) {
      if (c.path("type").asText().endsWith(typeSuffix)) {
        return c;
      }
    }
    throw new AssertionError("component not found: " + typeSuffix);
  }

  @Test
  void buildsValidFlowWithFullChain() throws Exception {
    JsonNode flow = build(mqttToPostgis(mapping()));

    assertEquals("pipeline-abc", flow.get("flowContents").get("name").asText());
    // 4 processors: ConsumeMQTT -> ConvertRecord -> UpdateRecord(mapping) -> PutDatabaseRecord
    assertEquals(4, flow.get("flowContents").get("processors").size());
    // 3 controller services: reader, writer, dbcp
    assertEquals(3, flow.get("flowContents").get("controllerServices").size());
    // 3 connections chaining the 4 processors
    assertEquals(3, flow.get("flowContents").get("connections").size());
  }

  @Test
  void resolvesControllerServiceReferencesToRealIds() throws Exception {
    String json = builder.build(mqttToPostgis(mapping()));
    // no unresolved ${CS:...} tokens remain anywhere
    assertFalse(json.contains("${CS:"));

    JsonNode flow = mapper.readTree(json);
    JsonNode putDb = component(flow, "processors", "PutDatabaseRecord");
    String dbcpRef = putDb.get("properties").get("Database Connection Pooling Service").asText();
    // the reference must be an actual controller-service id present in the flow
    boolean found = false;
    for (JsonNode cs : flow.get("flowContents").get("controllerServices")) {
      if (cs.get("identifier").asText().equals(dbcpRef)) {
        found = true;
      }
    }
    assertTrue(found, "DBCP reference must point to a real controller service");
  }

  @Test
  void bindsSourceSinkAndMappingProperties() throws Exception {
    JsonNode flow = build(mqttToPostgis(mapping()));

    assertEquals(
        "tcp://mosquitto:1883",
        component(flow, "processors", "ConsumeMQTT").get("properties").get("Broker URI").asText());
    assertEquals(
        "sensor_observations",
        component(flow, "processors", "PutDatabaseRecord")
            .get("properties")
            .get("Table Name")
            .asText());

    JsonNode update = component(flow, "processors", "UpdateRecord").get("properties");
    assertEquals("/name", update.get("/title").asText());
    assertEquals("record-path-value", update.get("Replacement Value Strategy").asText());
  }

  @Test
  void connectionsUseSourceOutputRelationships() throws Exception {
    JsonNode flow = build(mqttToPostgis(mapping()));
    boolean hasMessageRel = false;
    boolean hasSuccessRel = false;
    for (JsonNode conn : flow.get("flowContents").get("connections")) {
      String rel = conn.get("selectedRelationships").get(0).asText();
      if ("Message".equals(rel)) {
        hasMessageRel = true;
      }
      if ("success".equals(rel)) {
        hasSuccessRel = true;
      }
    }
    assertTrue(hasMessageRel, "ConsumeMQTT->ConvertRecord uses 'Message'");
    assertTrue(hasSuccessRel, "record processors chain on 'success'");
  }

  @Test
  void omitsMappingProcessorWhenNoMapping() throws Exception {
    JsonNode flow = build(mqttToPostgis(List.of()));
    // 3 processors: ConsumeMQTT -> ConvertRecord -> PutDatabaseRecord (no UpdateRecord)
    assertEquals(3, flow.get("flowContents").get("processors").size());
  }

  @Test
  void identifiersAreStableAcrossBuilds() throws Exception {
    assertEquals(builder.build(mqttToPostgis(mapping())), builder.build(mqttToPostgis(mapping())));
  }

  @Test
  void writerAlwaysInheritsRecordSchema() throws Exception {
    // the adapter never pins a schema on the writer — typing is the sink's job (PutDatabaseRecord
    // against the DB columns, owned by the PostGIS adapter)
    JsonNode flow = build(mqttToPostgis(mapping()));
    JsonNode writer = component(flow, "controllerServices", "JsonRecordSetWriter");
    assertEquals(
        "inherit-record-schema", writer.get("properties").get("Schema Access Strategy").asText());
  }

  @Test
  void buildsFrostSinkChainWithoutDbcp() throws Exception {
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-frost",
            SourceType.MQTT,
            Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
            SinkType.FROST,
            Map.of(
                "HTTP Method",
                "POST",
                "HTTP URL",
                "http://frost:8080/FROST-Server/v1.1/Observations"),
            mapping(),
            Map.of());

    JsonNode flow = build(spec);
    // ConsumeMQTT -> ConvertRecord -> UpdateRecord -> InvokeHTTP
    assertEquals(4, flow.get("flowContents").get("processors").size());
    // only reader + writer (no DBCP for a FROST/HTTP sink)
    assertEquals(2, flow.get("flowContents").get("controllerServices").size());

    JsonNode invoke = component(flow, "processors", "InvokeHTTP").get("properties");
    assertEquals(
        "http://frost:8080/FROST-Server/v1.1/Observations", invoke.get("HTTP URL").asText());
    assertEquals("POST", invoke.get("HTTP Method").asText());
  }
}

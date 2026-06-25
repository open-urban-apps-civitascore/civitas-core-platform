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

  private FlowBuildSpec frostSink() {
    return new FlowBuildSpec(
        "pipeline-frost",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.FROST,
        Map.of(
            "HTTP Method", "POST",
            "HTTP URL", "http://frost:8080/FROST-Server/v1.1/Observations"),
        mapping(),
        Map.of());
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
    // 5 processors: ConsumeMQTT -> ConvertRecord -> UpdateRecord(mapping) -> PutDatabaseRecord,
    // plus the LogMessage error sink that the convert/mapping failure relationships feed
    assertEquals(5, flow.get("flowContents").get("processors").size());
    // 3 controller services: reader, writer, dbcp
    assertEquals(3, flow.get("flowContents").get("controllerServices").size());
    // 7 connections: 3 chaining the happy path + 2 transform-failure edges (ConvertRecord,
    // UpdateRecord) + 2 sink write-failure edges (PutDatabaseRecord 'failure' + 'retry')
    assertEquals(7, flow.get("flowContents").get("connections").size());
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
  void routesConversionAndMappingFailuresToLogSink() throws Exception {
    JsonNode flow = build(mqttToPostgis(mapping()));

    JsonNode log = component(flow, "processors", "LogMessage");
    String logId = log.get("identifier").asText();
    // the log sink terminates its own 'success' — it is the end of the error path
    assertTrue(autoTerminates(log, "success"), "LogMessage terminates its own success");

    JsonNode convert = component(flow, "processors", "ConvertRecord");
    JsonNode update = component(flow, "processors", "UpdateRecord");
    // records that fail JSON conversion or RecordPath mapping are no longer dropped silently...
    assertFalse(
        autoTerminates(convert, "failure"), "ConvertRecord must not auto-terminate failure");
    assertFalse(autoTerminates(update, "failure"), "UpdateRecord must not auto-terminate failure");
    // ...they are routed to the LogMessage sink instead
    assertTrue(
        hasConnection(flow, convert.get("identifier").asText(), logId, "failure"),
        "ConvertRecord failure must be routed to the LogMessage sink");
    assertTrue(
        hasConnection(flow, update.get("identifier").asText(), logId, "failure"),
        "UpdateRecord failure must be routed to the LogMessage sink");
  }

  @Test
  void routesPostgisSinkWriteFailuresToLogSink() throws Exception {
    // A failed write to PostGIS must not be auto-terminated (silent loss): PutDatabaseRecord's
    // 'failure' and 'retry' relationships route to the LogMessage sink.
    JsonNode flow = build(mqttToPostgis(mapping()));

    JsonNode sink = component(flow, "processors", "PutDatabaseRecord");
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    String sinkId = sink.get("identifier").asText();

    assertFalse(
        autoTerminates(sink, "failure"), "PutDatabaseRecord must not auto-terminate failure");
    assertFalse(autoTerminates(sink, "retry"), "PutDatabaseRecord must not auto-terminate retry");
    assertTrue(autoTerminates(sink, "success"), "a successful write is still terminated");
    assertTrue(
        hasConnection(flow, sinkId, logId, "failure"), "sink failure must route to the log sink");
    assertTrue(
        hasConnection(flow, sinkId, logId, "retry"), "sink retry must route to the log sink");
  }

  @Test
  void routesFrostSinkWriteFailuresToLogSink() throws Exception {
    JsonNode flow = build(frostSink());

    JsonNode sink = component(flow, "processors", "InvokeHTTP");
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    String sinkId = sink.get("identifier").asText();

    for (String relationship : List.of("Failure", "Retry", "No Retry")) {
      assertFalse(
          autoTerminates(sink, relationship), "InvokeHTTP must not auto-terminate " + relationship);
      assertTrue(
          hasConnection(flow, sinkId, logId, relationship),
          "InvokeHTTP " + relationship + " must route to the log sink");
    }
    // the HTTP response itself is still discarded — only write failures are routed
    assertTrue(autoTerminates(sink, "Response"), "InvokeHTTP Response stays terminated");
    assertTrue(autoTerminates(sink, "Original"), "InvokeHTTP Original stays terminated");
  }

  private boolean autoTerminates(JsonNode processor, String relationship) {
    for (JsonNode rel : processor.path("autoTerminatedRelationships")) {
      if (relationship.equals(rel.asText())) {
        return true;
      }
    }
    return false;
  }

  private boolean hasConnection(
      JsonNode flow, String sourceId, String destId, String relationship) {
    for (JsonNode conn : flow.get("flowContents").get("connections")) {
      boolean matchesRelationship = false;
      for (JsonNode rel : conn.get("selectedRelationships")) {
        if (relationship.equals(rel.asText())) {
          matchesRelationship = true;
        }
      }
      if (matchesRelationship
          && sourceId.equals(conn.path("source").path("id").asText())
          && destId.equals(conn.path("destination").path("id").asText())) {
        return true;
      }
    }
    return false;
  }

  @Test
  void omitsMappingProcessorWhenNoMapping() throws Exception {
    JsonNode flow = build(mqttToPostgis(List.of()));
    // 4 processors: ConsumeMQTT -> ConvertRecord -> PutDatabaseRecord (no UpdateRecord),
    // plus the LogMessage error sink for ConvertRecord's failure relationship
    assertEquals(4, flow.get("flowContents").get("processors").size());
  }

  @Test
  void wiresErrorSinkEvenWithoutMappingProcessor() throws Exception {
    // even with no mapping (no UpdateRecord), ConvertRecord's failure must still route to the
    // LogMessage sink rather than being dropped
    JsonNode flow = build(mqttToPostgis(List.of()));

    JsonNode log = component(flow, "processors", "LogMessage");
    String logId = log.get("identifier").asText();
    JsonNode convert = component(flow, "processors", "ConvertRecord");
    assertFalse(
        autoTerminates(convert, "failure"), "ConvertRecord must not auto-terminate failure");
    assertTrue(
        hasConnection(flow, convert.get("identifier").asText(), logId, "failure"),
        "ConvertRecord failure must route to the LogMessage sink even without a mapping processor");
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
    JsonNode flow = build(frostSink());
    // ConsumeMQTT -> ConvertRecord -> UpdateRecord -> InvokeHTTP, plus the LogMessage error sink
    assertEquals(5, flow.get("flowContents").get("processors").size());
    // only reader + writer (no DBCP for a FROST/HTTP sink)
    assertEquals(2, flow.get("flowContents").get("controllerServices").size());

    JsonNode invoke = component(flow, "processors", "InvokeHTTP").get("properties");
    assertEquals(
        "http://frost:8080/FROST-Server/v1.1/Observations", invoke.get("HTTP URL").asText());
    assertEquals("POST", invoke.get("HTTP Method").asText());
  }
}

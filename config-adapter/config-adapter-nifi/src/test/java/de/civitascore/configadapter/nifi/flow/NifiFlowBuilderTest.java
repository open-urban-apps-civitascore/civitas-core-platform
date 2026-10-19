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

import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.compiled;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.frostSink;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mapping;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.mqttToPostgis;
import static de.civitascore.configadapter.nifi.flow.NifiTestFixtures.sqlToPostgis;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.ForkPlan;
import de.civitascore.configadapter.nifi.mapping.FrostPortPlan;
import de.civitascore.configadapter.nifi.mapping.SinkPort;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class NifiFlowBuilderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final NifiFlowBuilder builder = NifiTestFixtures.flowBuilder();

  private JsonNode build(FlowBuildSpec spec) throws Exception {
    return mapper.readTree(builder.build(spec));
  }

  /** Whether any processor of the given type has a property whose value contains the substring. */
  private boolean hasProcessor(
      JsonNode flow, String typeSuffix, String property, String substring) {
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith(typeSuffix)
          && c.path("properties").path(property).asText().contains(substring)) {
        return true;
      }
    }
    return false;
  }

  /** First processor of the given type whose property equals the value, or null. */
  private JsonNode componentByProperty(
      JsonNode flow, String typeSuffix, String property, String value) {
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith(typeSuffix)
          && value.equals(c.path("properties").path(property).asText())) {
        return c;
      }
    }
    return null;
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

  /**
   * A TLS MQTT spec: the processor references the SSL Context Service, whose trust anchor the
   * builder's MQTT stage supplies.
   */
  private static FlowBuildSpec mqttTlsSpec() {
    FlowBuildSpec plain = mqttToPostgis(mapping());
    Map<String, String> tlsProperties = new LinkedHashMap<>(plain.sourceProperties());
    tlsProperties.put("Broker URI", "ssl://mqtt:8883");
    tlsProperties.put(
        "SSL Context Service", "${CS:" + MqttSourceStage.MQTT_SSL_CONTEXT_SERVICE + "}");
    return new FlowBuildSpec(
        plain.processGroupName(),
        plain.sourceType(),
        tlsProperties,
        plain.sinkType(),
        plain.sinkProperties(),
        plain.transforms(),
        plain.controllerServiceProperties(),
        plain.sourceCron(),
        plain.sinkPreRegion());
  }

  @Test
  void mqttTlsAddsOneJvmTruststoreSslContextServiceAndReferencesIt() throws Exception {
    FlowBuildSpec tls = mqttTlsSpec();

    JsonNode flow = build(tls);
    JsonNode services = flow.path("flowContents").path("controllerServices");
    assertEquals(4, services.size());
    JsonNode sslContext = component(flow, "controllerServices", "StandardSSLContextService");
    assertEquals("TLS", sslContext.path("properties").path("TLS Protocol").asText());
    assertEquals(
        "${TRUSTSTORE_PATH}", sslContext.path("properties").path("Truststore Filename").asText());
    assertEquals(
        "#{TRUSTSTORE_PASSWORD}",
        sslContext.path("properties").path("Truststore Password").asText());
    assertEquals("PKCS12", sslContext.path("properties").path("Truststore Type").asText());
    assertEquals(
        MqttTruststoreConfig.DEFAULT_PARAMETER_CONTEXT,
        flow.path("flowContents").path("parameterContextName").asText());
    JsonNode truststorePassword =
        flow.path("parameterContexts")
            .path(MqttTruststoreConfig.DEFAULT_PARAMETER_CONTEXT)
            .path("parameters")
            .path(0);
    assertEquals(
        MqttTruststoreConfig.DEFAULT_PASSWORD_PARAMETER, truststorePassword.path("name").asText());
    assertTrue(truststorePassword.path("sensitive").asBoolean());
    assertTrue(truststorePassword.path("value").isMissingNode());
    assertTrue(sslContext.path("properties").path("Keystore Filename").isNull());
    assertEquals(
        sslContext.path("identifier").asText(),
        component(flow, "processors", "ConsumeMQTT")
            .path("properties")
            .path("SSL Context Service")
            .asText());
    assertEquals(builder.build(tls), builder.build(tls), "TLS snapshot must remain deterministic");
  }

  @Test
  void passwordlessMqttTruststoreDeclaresNoParameterContext() throws Exception {
    MqttTruststoreConfig truststore =
        new MqttTruststoreConfig(
            "/opt/mqtt-tls/truststore.p12", "PKCS12", "", MqttTruststoreConfig.NO_PASSWORD, "");
    NifiFlowBuilder passwordless = NifiTestFixtures.flowBuilder(truststore);
    FlowBuildSpec tls = mqttTlsSpec();

    JsonNode flow = mapper.readTree(passwordless.build(tls));

    JsonNode sslContext = component(flow, "controllerServices", "StandardSSLContextService");
    assertEquals(
        "/opt/mqtt-tls/truststore.p12",
        sslContext.path("properties").path("Truststore Filename").asText());
    assertTrue(
        sslContext.path("properties").path("Truststore Password").isNull(),
        "a truststore that opens without a password must not reference a parameter");
    assertTrue(
        flow.path("parameterContexts").isEmpty(),
        "no sensitive parameter means no parameter context to provision");
    assertTrue(
        flow.path("flowContents").path("parameterContextName").isMissingNode(),
        "the process group must not bind to a parameter context it does not use");
  }

  @Test
  void mqttWithoutTlsDoesNotAddOrReferenceSslContextService() throws Exception {
    JsonNode flow = build(mqttToPostgis(mapping()));
    assertEquals(3, flow.path("flowContents").path("controllerServices").size());
    assertTrue(
        component(flow, "processors", "ConsumeMQTT")
            .path("properties")
            .path("SSL Context Service")
            .isNull());
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
  void updateRecordCarriesOnlyTheMappedRecordPaths() throws Exception {
    // A fragment re-exported with sample record paths would add those fields to every record.
    JsonNode update =
        component(build(mqttToPostgis(mapping())), "processors", "UpdateRecord").get("properties");
    Set<String> names = new HashSet<>();
    update.fieldNames().forEachRemaining(names::add);
    assertEquals(
        Set.of("Record Reader", "Record Writer", "Replacement Value Strategy", "/title"), names);
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
  void frostFanoutPutsTheForkAheadOfTheMappingAndFeedsTheRecordSplit() throws Exception {
    // The fan-out has to happen before the entity bodies are rendered: those are static EL
    // templates over sta_* attributes and cannot multiply themselves. So ForkRecord must sit at the
    // head of the mapping unit, leave on its own 'fork' relationship, and the N records it writes
    // must reach the pre-region's SplitJson — that is what turns N records into N FlowFiles and
    // therefore N observations.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithFanoutMapping());

    JsonNode fork = component(flow, "processors", "ForkRecord");
    assertEquals("/measurements[*]/measuredValues", fork.get("properties").get("fan-out").asText());
    assertEquals("extract", fork.get("properties").get("Mode").asText());
    assertEquals("true", fork.get("properties").get("Include Parent Fields").asText());

    // 'original' must be auto-terminated, otherwise the unforked input queues up forever
    assertTrue(
        StreamSupport.stream(fork.get("autoTerminatedRelationships").spliterator(), false)
            .anyMatch(r -> "original".equals(r.asText())),
        "the unforked original must be auto-terminated");

    // ForkRecord's third relationship. Left neither connected nor auto-terminated, the processor is
    // INVALID and NiFi silently never runs it, so every deploy of a fan-out flow fails.
    assertFalse(autoTerminates(fork, "failure"), "the fork must not auto-terminate failure");
    assertEquals(
        component(flow, "processors", "LogMessage").get("identifier").asText(),
        destinationOf(flow, fork.get("identifier").asText(), "failure"),
        "an unreadable FlowFile must reach the error sink");

    // Selected by strategy, not by first-match: the fixture mixes a const rule with copies, so the
    // flow holds a literal-value AND a record-path-value UpdateRecord and the fork feeds the
    // latter.
    JsonNode update =
        componentByProperty(
            flow, "UpdateRecord", "Replacement Value Strategy", "record-path-value");
    String forkId = fork.get("identifier").asText();

    // The guard sits between the two: a fan-out that extracted nothing must not travel the chain as
    // an empty, successful FlowFile.
    JsonNode guard =
        componentByProperty(flow, "RouteOnAttribute", "failure", "${record.count:equals('0')}");
    String guardId = guard.get("identifier").asText();
    assertEquals(
        guardId,
        destinationOf(flow, forkId, "fork"),
        "the fork must feed the guard over its 'fork' relationship");
    assertEquals(
        update.get("identifier").asText(),
        destinationOf(flow, guardId, "unmatched"),
        "records that survived the guard must feed the mapping");
    assertEquals(
        component(flow, "processors", "LogMessage").get("identifier").asText(),
        destinationOf(flow, guardId, "failure"),
        "an empty fan-out must reach the error sink");

    // The forked records must reach the pre-region's SplitJson — that is what turns N records into
    // N FlowFiles and therefore N observations. Without it the fan-out would produce one FlowFile
    // carrying N records, and the static entity bodies would render only the first.
    String splitId =
        componentByProperty(flow, "SplitJson", "JsonPath Expression", "$[*]")
            .get("identifier")
            .asText();
    assertTrue(
        reaches(flow, forkId, splitId), "the forked records must reach the pre-region's SplitJson");
  }

  /** Whether any chain of connections leads from {@code sourceId} to {@code targetId}. */
  private boolean reaches(JsonNode flow, String sourceId, String targetId) {
    Set<String> seen = new HashSet<>();
    Deque<String> pending = new ArrayDeque<>(List.of(sourceId));
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (!seen.add(current)) {
        continue;
      }
      if (current.equals(targetId)) {
        return true;
      }
      for (JsonNode connection : flow.get("flowContents").get("connections")) {
        if (current.equals(connection.path("source").path("id").asText())) {
          pending.push(connection.path("destination").path("id").asText());
        }
      }
    }
    return false;
  }

  /** The destination component id of the connection leaving {@code sourceId} on {@code rel}. */
  private String destinationOf(JsonNode flow, String sourceId, String rel) {
    for (JsonNode connection : flow.get("flowContents").get("connections")) {
      boolean matches =
          sourceId.equals(connection.path("source").path("id").asText())
              && StreamSupport.stream(connection.get("selectedRelationships").spliterator(), false)
                  .anyMatch(r -> rel.equals(r.asText()));
      if (matches) {
        return connection.path("destination").path("id").asText();
      }
    }
    return null;
  }

  @Test
  void frostProjectIdReachesTheProcessor() throws Exception {
    // The project scopes every lookup and every write, so that a reference collision with another
    // Dataset cannot resolve across Datasets. The processor holds it as a property now; before the
    // rebuild it was interpolated into forty URLs.
    JsonNode flow = build(frostSink());

    assertTrue(hasProcessor(flow, "PutFrostRecord", "FROST Project Id", "7"));
  }

  @Test
  void frostSinkWithoutProjectIdIsRejected() {
    // Every FROST flow must be project-scoped; a missing id fails the build rather than silently
    // producing a server-root flow that the dataset's named API cannot see.
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-frost-noproject",
            SourceType.MQTT,
            Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", "t"),
            SinkType.FROST,
            Map.of(FrostSinkStage.FROST_BASE_URL, "http://frost:8080/FROST-Server/v1.1"),
            List.of(),
            Map.of(),
            null,
            null);
    assertThrows(FatalAdapterException.class, () -> builder.build(spec));
  }

  @Test
  void frostSinkWithoutBaseUrlIsRejected() {
    // The build is reachable without the bind half; a missing base URL would deploy relative
    // InvokeHTTP URLs that post observations into the void.
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-frost-nobase",
            SourceType.MQTT,
            Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", "t"),
            SinkType.FROST,
            Map.of(FrostSinkStage.FROST_PROJECT_ID, "7"),
            List.of(),
            Map.of(),
            null,
            null);
    assertThrows(FatalAdapterException.class, () -> builder.build(spec));
  }

  @Test
  void routesFrostSinkWriteFailuresToLogSink() throws Exception {
    // No silent data loss at the sink: the record that FROST rejected and the record whose request
    // did not complete both reach the error sink. An unrouted relationship would drop them.
    JsonNode flow = build(frostSink());

    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    JsonNode put = component(flow, "processors", "PutFrostRecord");
    String putId = put.get("identifier").asText();
    for (String relationship : List.of("failure", "retry")) {
      assertFalse(autoTerminates(put, relationship), "must not auto-terminate " + relationship);
      assertTrue(
          hasConnection(flow, putId, logId, relationship),
          relationship + " must reach the error sink");
    }
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
  void chainedMappingsMaterializePerNodeWithStableFirstIds() throws Exception {
    FlowBuildSpec single = mqttToPostgis(mapping());
    FlowBuildSpec chained =
        new FlowBuildSpec(
            single.processGroupName(),
            single.sourceType(),
            single.sourceProperties(),
            single.sinkType(),
            single.sinkProperties(),
            List.of(
                new CompiledMapping(mapping(), ForkPlan.NONE),
                new CompiledMapping(mapping(), ForkPlan.NONE)),
            single.controllerServiceProperties(),
            null,
            null);

    List<String> singleIds = updateRecordIds(build(single));
    List<String> chainedIds = updateRecordIds(build(chained));

    assertEquals(1, singleIds.size());
    assertEquals(2, chainedIds.size());
    // The first chain position omits the index, so single-mapping flows already deployed to a
    // live NiFi map back to the same component on redeploy (redeploy idempotency).
    assertEquals(singleIds.get(0), chainedIds.get(0));
    assertFalse(chainedIds.get(0).equals(chainedIds.get(1)));
  }

  @Test
  void addingAFanOutLeavesTheUpdateRecordIdsUntouched() throws Exception {
    // The fork's id seed is deliberately independent of the strategy discriminators, so a mapping
    // that gains a fan-out keeps its UpdateRecord ids and a redeploy still matches them to the live
    // NiFi components. If the fork joined that seed instead, every existing UpdateRecord would get
    // a
    // new id and the redeploy would orphan the deployed ones — invisible in every other assertion.
    FlowBuildSpec withoutFork = mqttToPostgis(mapping());
    FlowBuildSpec withFork =
        new FlowBuildSpec(
            withoutFork.processGroupName(),
            withoutFork.sourceType(),
            withoutFork.sourceProperties(),
            withoutFork.sinkType(),
            withoutFork.sinkProperties(),
            List.of(new CompiledMapping(mapping(), new ForkPlan("/items"))),
            withoutFork.controllerServiceProperties(),
            null,
            null);

    assertEquals(updateRecordIds(build(withoutFork)), updateRecordIds(build(withFork)));
  }

  private List<String> updateRecordIds(JsonNode flow) {
    List<String> ids = new ArrayList<>();
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("UpdateRecord")) {
        ids.add(c.get("identifier").asText());
      }
    }
    return ids;
  }

  @Test
  void frostSinkWithMappingButNoEnvelopePlanIsRejected() {
    // The builder is reachable directly (not only through the planner): a compiled mapping without
    // a port plan
    // would deploy a flow that sends the mapped record instead of the port structure — the build
    // must fail instead of writing the wrong document.
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-frost-map",
            SourceType.MQTT,
            Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", "t"),
            SinkType.FROST,
            Map.of(
                FrostSinkStage.FROST_BASE_URL,
                "http://frost:8080/x",
                FrostSinkStage.FROST_PROJECT_ID,
                "7",
                FrostSinkStage.FROST_PORT,
                SinkPort.THING_TREE.label()),
            compiled(mapping()),
            Map.of(),
            null,
            null);
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(
        ex.getMessage()
            .contains("a record mapping was compiled for the FROST sink but no port plan"));
  }

  @Test
  void mappedFrostFlow_buildsOneProcessorInsteadOfTheGraph() throws Exception {
    // The generated find-or-create graph wrote each entity in its own request, which is where the
    // twenty to forty processors came from. The processor writes the record in one request.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());

    assertEquals(1, countProcessors(flow, "PutFrostRecord"), "one processor writes the record");
    assertEquals(0, countProcessors(flow, "InvokeHTTP"), "no request processor is left");
  }

  @Test
  void mappedFrostFlow_carriesThePortAndTheScopeOnTheProcessor() throws Exception {
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());

    assertTrue(hasProcessor(flow, "PutFrostRecord", "Port", "ThingTree"), "the port is a property");
    assertTrue(
        hasProcessor(flow, "PutFrostRecord", "FROST Project Id", "7"),
        "the project scopes every lookup and write");
  }

  @Test
  void mappedFrostFlow_rendersThePortBodyBeforeTheProcessor() throws Exception {
    // The processor reads the record as the port's structure, so the flow hands it that document
    // and not the flat mapped record.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());

    assertTrue(
        hasProcessor(flow, "ReplaceText", "Replacement Value", "{\"name\":\"${sta_0_name"),
        "the port body is rendered from the captured attributes");
  }

  /** How many processors of a type the flow holds. */
  private static int countProcessors(JsonNode flow, String type) {
    int found = 0;
    for (JsonNode processor : flow.findValue("processors")) {
      if (processor.path("type").asText().endsWith("." + type)) {
        found++;
      }
    }
    return found;
  }

  @Test
  void postgisSinkRejectsAPreRegionPlan() {
    // The pre-region slot is sink-neutral; a sink that cannot consume the variant must fail the
    // build instead of silently dropping the compiled transform output.
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-postgis-preregion",
            SourceType.MQTT,
            Map.of("Broker URI", "tcp://mqtt:1883", "Topic Filter", "t"),
            SinkType.POSTGIS,
            Map.of("Table Name", "t"),
            compiled(mapping()),
            Map.of(
                "PostGISConnectionPool",
                Map.of("Database Connection URL", "jdbc:postgresql://db:5432/x")),
            null,
            new FrostPortPlan(List.of("sta_0_reference"), "{\"properties\":{}}"));
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(ex.getMessage().contains("POSTGIS sink cannot consume a pre-region plan"));
  }

  @Test
  void routesFrostIntermediateFailuresToLogSink() throws Exception {
    // No silent data loss in front of the sink either: the split, the capture and the render route
    // their 'failure' to the error sink rather than auto-terminating it.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();

    for (String type : List.of("SplitJson", "EvaluateJsonPath", "ReplaceText")) {
      boolean seen = false;
      for (JsonNode c : flow.get("flowContents").get("processors")) {
        if (!c.path("type").asText().endsWith(type)) {
          continue;
        }
        seen = true;
        assertFalse(autoTerminates(c, "failure"), type + " must not auto-terminate failure");
        assertTrue(
            hasConnection(flow, c.get("identifier").asText(), logId, "failure"),
            type + " failure must reach the error sink");
      }
      assertTrue(seen, "expected at least one " + type + " in the FROST sub-flow");
    }
  }

  @Test
  void frostSinkWithoutPortIsRejected() {
    // A SQL source is accepted without a mapping (its records must have the port structure), but a
    // FROST sink without a port cannot be built.
    FlowBuildSpec spec =
        new FlowBuildSpec(
            "pipeline-frost-sql",
            SourceType.SQL,
            Map.of("Table Name", "events"),
            SinkType.FROST,
            Map.of(
                FrostSinkStage.FROST_BASE_URL,
                "http://frost:8080",
                FrostSinkStage.FROST_PROJECT_ID,
                "7"),
            List.of(),
            Map.of(
                "SourceConnectionPool",
                Map.of("Database Connection URL", "jdbc:postgresql://s/in")),
            null,
            null);
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(ex.getMessage().contains("requires the 'Port' property"), ex.getMessage());
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
  void buildsSqlSourceChainCronScheduled() throws Exception {
    // a SQL source flow reads records from the configured table on a cron schedule
    JsonNode flow = build(sqlToPostgis());

    // the entry processor reads records from the configured table via a DB query — not ConsumeMQTT
    JsonNode source = component(flow, "processors", "QueryDatabaseTableRecord");
    assertEquals(
        "events",
        source.get("properties").get("Table Name").asText(),
        "SQL source must query the configured table");

    // a SQL source is pull-based: it must run on a schedule, never free-running at TIMER_DRIVEN
    // 0 sec (which would hammer the database as fast as the engine can trigger it)
    assertEquals(
        "CRON_DRIVEN",
        source.path("schedulingStrategy").asText(),
        "a pull-based SQL source must be cron-scheduled, not timer-driven");
  }

  @Test
  void sqlSourceChainOmitsConvertAndMqtt() throws Exception {
    JsonNode flow = build(sqlToPostgis());

    // SQL records come straight from the source — no ConsumeMQTT, no ConvertRecord step
    JsonNode procs = flow.get("flowContents").get("processors");
    for (JsonNode p : procs) {
      String type = p.path("type").asText();
      assertFalse(type.endsWith("ConsumeMQTT"), "no MQTT source in a SQL flow");
      assertFalse(type.endsWith("ConvertRecord"), "SQL source already emits records");
    }
    // QueryDatabaseTableRecord -> UpdateRecord(mapping) -> PutDatabaseRecord + LogMessage error
    // sink
    assertEquals(4, procs.size());
    // reader, writer, source DBCP, sink DBCP
    assertEquals(4, flow.get("flowContents").get("controllerServices").size());
  }

  @Test
  void sqlSourceResolvesItsOwnConnectionPool() throws Exception {
    String json = builder.build(sqlToPostgis());
    assertFalse(json.contains("${CS:"), "no unresolved controller-service tokens");

    JsonNode flow = mapper.readTree(json);
    JsonNode source = component(flow, "processors", "QueryDatabaseTableRecord");
    String poolRef = source.get("properties").get("Database Connection Pooling Service").asText();
    boolean found = false;
    for (JsonNode cs : flow.get("flowContents").get("controllerServices")) {
      if (cs.get("identifier").asText().equals(poolRef)
          && "SourceConnectionPool".equals(cs.get("name").asText())) {
        found = true;
      }
    }
    assertTrue(found, "source DBCP reference must point to the SourceConnectionPool service");
  }

  @Test
  void cronOverridesSourceSchedule() throws Exception {
    JsonNode flow = build(sqlToPostgis("0 15 10 * * ?"));
    JsonNode source = component(flow, "processors", "QueryDatabaseTableRecord");
    assertEquals("CRON_DRIVEN", source.get("schedulingStrategy").asText());
    assertEquals(
        "0 15 10 * * ?",
        source.get("schedulingPeriod").asText(),
        "explicit cron node overrides the fragment's default schedule");
  }

  @Test
  void routesFrostEnvelopeRegionFailuresToLogSink() throws Exception {
    // No silent drop in the rebuild region: staRecordSplit/staCapture/staEnvelope route 'failure'
    // to the error sink.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    JsonNode split = componentByProperty(flow, "SplitJson", "JsonPath Expression", "$[*]");
    JsonNode capture = componentByProperty(flow, "EvaluateJsonPath", "sta_0_name", "$.sta_0_name");
    for (JsonNode processor : List.of(split, capture)) {
      assertFalse(autoTerminates(processor, "failure"), "must not auto-terminate failure");
      assertTrue(
          hasConnection(flow, processor.get("identifier").asText(), logId, "failure"),
          "envelope-region failure must route to the log sink");
    }
  }

  @Test
  void buildsFrostSubFlowWithoutDbcp() throws Exception {
    // ConsumeMQTT + ConvertRecord + SplitJson + PutFrostRecord + LogMessage. The generated
    // find-or-create graph needed twenty-one processors for the same Pipeline.
    JsonNode flow = build(frostSink());

    assertEquals(5, flow.get("flowContents").get("processors").size());
    assertEquals(1, countProcessors(flow, "ConvertRecord"), "raw MQTT JSON becomes records");
    // The web client carries the transport; a FROST sink needs no database connection pool.
    for (JsonNode service : flow.get("flowContents").get("controllerServices")) {
      assertFalse(service.path("type").asText().contains("DBCP"), "no connection pool for FROST");
    }
  }
}

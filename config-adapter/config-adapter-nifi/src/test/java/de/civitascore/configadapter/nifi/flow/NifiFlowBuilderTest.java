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
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  void buildsFrostFindOrCreateSubFlow() throws Exception {
    // FROST is no longer a single POST: per Thing in the STA envelope, look up by reference and
    // POST
    // only if absent. Assert the find-or-create stages and their wiring exist.
    JsonNode flow = build(frostSink());

    JsonNode split = component(flow, "processors", "SplitJson");
    assertEquals(
        "$.things", split.get("properties").get("JsonPath Expression").asText(), "splits things");

    JsonNode get = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "GET");
    assertTrue(
        get.get("properties")
            .get("HTTP URL")
            .asText()
            .contains("/Things?$filter=properties/reference"),
        "looks up the Thing by reference");

    JsonNode route = component(flow, "processors", "RouteOnAttribute");
    assertEquals(
        "${frost.id:isEmpty()}",
        route.get("properties").get("new").asText(),
        "routes to 'new' only when no @iot.id resolved");

    JsonNode post = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "POST");
    assertTrue(
        post.get("properties").get("HTTP URL").asText().endsWith("/Things"), "POSTs a new Thing");
    // restore-then-POST: the captured entity body is written back before the POST
    JsonNode restore = component(flow, "processors", "ReplaceText");
    assertEquals("${frost.body}", restore.get("properties").get("Replacement Value").asText());
  }

  @Test
  void buildsFrostObservationLeg() throws Exception {
    // Second leg: resolve the observation's Datastream by reference+name and POST the observation
    // with the resolved @iot.id merged in.
    JsonNode flow = build(frostSink());

    assertTrue(
        hasProcessor(flow, "SplitJson", "JsonPath Expression", "$.observations"),
        "splits observations");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Datastreams?$filter="),
        "looks up the Datastream by reference+name");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Observations"), "POSTs the observation");
    assertTrue(
        hasProcessor(flow, "ReplaceText", "Replacement Value", "\"Datastream\""),
        "merges the resolved Datastream id into the observation");
  }

  @Test
  void frostProjectIdScopesTheFindOrCreateFlow() throws Exception {
    // With the saga's project id, Things live under /Projects(n) (visible through the dataset's
    // project-scoped named API) and the Datastream lookup is filtered by Thing/Projects/id — the
    // projects plugin has no /Projects(n)/Datastreams collection, and an unfiltered lookup could
    // match another dataset's Datastream on a reference collision.
    FlowBuildSpec unscoped = frostSink();
    JsonNode flow =
        build(
            new FlowBuildSpec(
                unscoped.processGroupName(),
                unscoped.sourceType(),
                unscoped.sourceProperties(),
                SinkType.FROST,
                Map.of(
                    FrostSinkStage.FROST_BASE_URL,
                    "http://frost:8080/FROST-Server/v1.1",
                    FrostSinkStage.FROST_PROJECT_ID,
                    "7"),
                List.of(),
                Map.of(),
                null,
                null));

    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Projects(7)/Things?$filter="),
        "Thing lookup is project-scoped");
    boolean thingPostScoped = false;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("POST")
          && c.path("properties").path("HTTP URL").asText().endsWith("/Projects(7)/Things")) {
        thingPostScoped = true;
      }
    }
    assertTrue(thingPostScoped, "Thing POST is project-scoped");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "Thing/Projects/id%20eq%207"),
        "Datastream lookup filters on the project");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/FROST-Server/v1.1/Observations"),
        "Observation POST stays at the root (scope flows via the Datastream)");
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
    JsonNode flow = build(frostSink());

    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    // the terminal write is the POST; its failure-side relationships must route to the log sink
    JsonNode post = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "POST");
    String postId = post.get("identifier").asText();

    for (String relationship : List.of("Failure", "Retry", "No Retry")) {
      assertFalse(
          autoTerminates(post, relationship), "POST must not auto-terminate " + relationship);
      assertTrue(
          hasConnection(flow, postId, logId, relationship),
          "POST " + relationship + " must route to the log sink");
    }
    // the HTTP response itself is still discarded — only write failures are routed
    assertTrue(autoTerminates(post, "Response"), "POST Response stays terminated");
    assertTrue(autoTerminates(post, "Original"), "POST Original stays terminated");
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
            List.of(new CompiledMapping(mapping()), new CompiledMapping(mapping())),
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
    // The builder is reachable directly (not only through the planner): a compiled mapping heading
    // into the raw-JSON find-or-create without an envelope rebuild would silently vanish inside
    // the envelope — the build must fail instead of dropping the transformation.
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
                "7"),
            compiled(mapping()),
            Map.of(),
            null,
            null);
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(
        ex.getMessage()
            .contains("a record mapping was compiled for a raw-JSON sink but no entity plan"));
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
            new FrostEntityPlan(
                List.of("sta_0_reference"),
                List.of(new FilterTerm("properties/reference", "sta_0_reference")),
                null,
                List.of(),
                null,
                null));
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(ex.getMessage().contains("POSTGIS sink cannot consume a pre-region plan"));
  }

  @Test
  void routesFrostIntermediateFailuresToLogSink() throws Exception {
    // No silent data loss inside the find-or-create sub-flow: every Split/Extract/Replace stage's
    // 'failure' must route to the LogMessage error sink, not stay auto-terminated.
    JsonNode flow = build(frostSink());
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
            type + " failure must route to the log sink");
      }
      assertTrue(seen, "expected at least one " + type + " in the FROST sub-flow");
    }
  }

  @Test
  void frostSinkWithSqlSourceIsRejected() {
    // FROST consumes the STA envelope an MQTT source delivers; a SQL source emits plain records
    // that
    // SplitJson would never match, so the combination is rejected rather than silently empty.
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
    assertThrows(FatalAdapterException.class, () -> builder.build(spec));
  }

  @Test
  void frostFilterEscapesSingleQuotesForOData() throws Exception {
    // A reference/name containing a single quote must not break the OData $filter: the value is
    // quote-doubled (OData escape) before urlEncode, so O'Brien stays a valid literal.
    JsonNode flow = build(frostSink());
    boolean thing = false;
    boolean datastream = false;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      String url = c.path("properties").path("HTTP URL").asText("");
      if (url.contains("/Things?$filter=")) {
        thing = true;
        assertTrue(
            url.contains("frost.ref:replaceAll(\"'\",\"''\")"),
            "Things filter must OData-escape single quotes in the reference");
      }
      if (url.contains("/Datastreams?$filter=")) {
        datastream = true;
        assertTrue(
            url.contains("frost.ref:replaceAll(\"'\",\"''\")"), "Datastream ref must escape");
        assertTrue(
            url.contains("frost.name:replaceAll(\"'\",\"''\")"), "Datastream name must escape");
      }
    }
    assertTrue(thing, "expected a Things lookup GET");
    assertTrue(datastream, "expected a Datastreams lookup GET");
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
  void buildsFrostFindOrCreateChainForMappedFlow() throws Exception {
    // A mapped FROST flow runs ONE linear find-or-create chain per record: record chain
    // (Convert + UpdateRecord), split $[*] → capture (flat keys) → Thing stage → Datastream stage
    // → Observation POST. Stages are sequential — each create needs its parent's @iot.id.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());

    // throws if absent: the MQTT payload must be converted to records for the mapping
    component(flow, "processors", "ConvertRecord");
    JsonNode update = component(flow, "processors", "UpdateRecord");
    assertEquals(
        "/station",
        update.get("properties").get("/sta_0_name").asText(),
        "mapping writes the flat intermediate fields");

    JsonNode split = componentByProperty(flow, "SplitJson", "JsonPath Expression", "$[*]");
    assertTrue(split != null, "record-writer array must be split into single records");
    JsonNode capture = componentByProperty(flow, "EvaluateJsonPath", "sta_0_name", "$.sta_0_name");
    assertTrue(capture != null, "flat fields must be captured into attributes");

    // Thing stage: lookup by the structure's match key, create on miss (create set is mapped)
    JsonNode thingGet =
        componentByProperty(
            flow,
            "InvokeHTTP",
            "HTTP URL",
            "http://frost:8080/FROST-Server/v1.1/Projects(7)/Things?$filter=properties/reference"
                + "%20eq%20'${sta_2_reference:replaceAll(\"'\",\"''\"):urlEncode()}'");
    assertTrue(thingGet != null, "Thing lookup must filter on the match key inside the project");
    assertTrue(
        hasProcessor(flow, "ReplaceText", "Replacement Value", "{\"name\":\"${sta_0_name"),
        "the Thing create body must be rendered from the captured attributes");

    // Datastream stage: lookup-only (create set unmapped) — a miss must route to the error sink
    JsonNode dsGet =
        componentByProperty(
            flow,
            "InvokeHTTP",
            "HTTP URL",
            "http://frost:8080/FROST-Server/v1.1/Datastreams?$filter=properties/reference"
                + "%20eq%20'${sta_5_reference:replaceAll(\"'\",\"''\"):urlEncode()}'"
                + "%20and%20Thing/Projects/id%20eq%207");
    assertTrue(dsGet != null, "Datastream lookup must be project-filtered");
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();
    JsonNode dsRoute = null;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("RouteOnAttribute")
          && c.path("properties").path("new").asText().contains("frost.ds.id")) {
        dsRoute = c;
      }
    }
    assertTrue(dsRoute != null, "Datastream stage must route on the extracted id");
    assertTrue(
        hasConnection(flow, dsRoute.get("identifier").asText(), logId, "new"),
        "a missing datastream on a lookup-only stage must route to the error sink");

    // Observation POST carries the resolved Datastream id
    assertTrue(
        hasProcessor(
            flow,
            "ReplaceText",
            "Replacement Value",
            "\"Datastream\":{\"@iot.id\":${frost.ds.id}}"),
        "the Observation body must link the resolved Datastream");
    JsonNode obsPost =
        componentByProperty(
            flow, "InvokeHTTP", "HTTP URL", "http://frost:8080/FROST-Server/v1.1/Observations");
    assertTrue(obsPost != null, "observations must be posted to /Observations");
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
  void buildsFrostFindOrCreateChainWithoutDbcp() throws Exception {
    JsonNode flow = build(frostSink());
    // ConsumeMQTT + LogMessage + Thing leg (split, body, ref, GET, id, route, restore, POST = 8)
    // + Observation leg (split, body, ref, GET, id, route, restore, inject, POST = 9) = 19
    assertEquals(19, flow.get("flowContents").get("processors").size());
    // only reader + writer (no DBCP for a FROST/HTTP sink)
    assertEquals(2, flow.get("flowContents").get("controllerServices").size());

    // the base URL + project id drive the per-stage URLs, not a single /Observations POST
    JsonNode get = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "GET");
    assertTrue(
        get.get("properties")
            .get("HTTP URL")
            .asText()
            .startsWith("http://frost:8080/FROST-Server/v1.1/Projects(7)/Things"));
  }
}

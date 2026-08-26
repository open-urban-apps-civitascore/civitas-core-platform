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
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
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
   * A TLS MQTT spec: the processor references the SSL Context Service, and the trust anchor arrives
   * as controller-service properties.
   */
  private static FlowBuildSpec mqttTlsSpec(MqttTruststoreConfig truststore) {
    return mqttTlsSpec(truststore.sslContextProperties());
  }

  private static FlowBuildSpec mqttTlsSpec(Map<String, String> truststoreProperties) {
    FlowBuildSpec plain = mqttToPostgis(mapping());
    Map<String, String> tlsProperties = new LinkedHashMap<>(plain.sourceProperties());
    tlsProperties.put("Broker URI", "ssl://mqtt:8883");
    tlsProperties.put(
        "SSL Context Service", "${CS:" + MqttSourceStage.MQTT_SSL_CONTEXT_SERVICE + "}");
    Map<String, Map<String, String>> serviceProperties =
        new LinkedHashMap<>(plain.controllerServiceProperties());
    serviceProperties.put(MqttSourceStage.MQTT_SSL_CONTEXT_SERVICE, truststoreProperties);
    return new FlowBuildSpec(
        plain.processGroupName(),
        plain.sourceType(),
        tlsProperties,
        plain.sinkType(),
        plain.sinkProperties(),
        plain.transforms(),
        serviceProperties,
        plain.sourceCron(),
        plain.sinkPreRegion());
  }

  @Test
  void mqttTlsAddsOneJvmTruststoreSslContextServiceAndReferencesIt() throws Exception {
    FlowBuildSpec tls = mqttTlsSpec(MqttTruststoreConfig.nodeTruststore());

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
            "/opt/mqtt-tls/truststore.p12", "PKCS12", MqttTruststoreConfig.NO_PASSWORD, "");
    NifiFlowBuilder passwordless = NifiTestFixtures.flowBuilder(truststore);
    FlowBuildSpec tls = mqttTlsSpec(truststore);

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
  void buildsFrostUpsertSubFlow() throws Exception {
    // Per Thing in the STA envelope: look up by reference, PATCH the resolved entity on a hit and
    // POST only on a miss.
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
    JsonNode patch = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "PATCH");
    assertTrue(
        patch.get("properties").get("HTTP URL").asText().endsWith("/Things(${frost.id})"),
        "PATCHes the Thing resolved by reference");
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
    boolean thingPatchScoped = false;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("POST")
          && c.path("properties").path("HTTP URL").asText().endsWith("/Projects(7)/Things")) {
        thingPostScoped = true;
      }
      if (c.path("type").asText().endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("PATCH")
          && c.path("properties")
              .path("HTTP URL")
              .asText()
              .endsWith("/Projects(7)/Things(${frost.id})")) {
        thingPatchScoped = true;
      }
    }
    assertTrue(thingPostScoped, "Thing POST is project-scoped");
    assertTrue(thingPatchScoped, "Thing PATCH is project-scoped");
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
    // Both write outcomes must route their failure-side relationships to the log sink.
    for (String method : List.of("POST", "PATCH")) {
      JsonNode write = componentByProperty(flow, "InvokeHTTP", "HTTP Method", method);
      String writeId = write.get("identifier").asText();
      for (String relationship : List.of("Failure", "Retry", "No Retry")) {
        assertFalse(
            autoTerminates(write, relationship),
            method + " must not auto-terminate " + relationship);
        assertTrue(
            hasConnection(flow, writeId, logId, relationship),
            method + " " + relationship + " must route to the log sink");
      }
    }
    // the HTTP response itself is still discarded — only write failures are routed
    JsonNode post = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "POST");
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
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null));
    FatalAdapterException ex = assertThrows(FatalAdapterException.class, () -> builder.build(spec));
    assertTrue(ex.getMessage().contains("POSTGIS sink cannot consume a pre-region plan"));
  }

  @Test
  void mappedFrostUpsertsLocationAndPatchesDatastreamNavigationEntities() throws Exception {
    JsonNode flow = build(NifiTestFixtures.frostSinkWithRelatedEntityMapping());

    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Things(${frost.thing.id})/Locations?$top=1"),
        "resolves the Thing's current Location");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Things(${frost.thing.id})/Locations"),
        "creates and links a missing Location through the navigation collection");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Locations(${frost.location.id})"),
        "patches an existing Location by id");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Datastreams(${frost.ds.id})/Sensor"),
        "resolves the Datastream's Sensor navigation entity");
    assertTrue(
        hasProcessor(
            flow, "InvokeHTTP", "HTTP URL", "/Datastreams(${frost.ds.id})/ObservedProperty"),
        "resolves the Datastream's ObservedProperty navigation entity");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Sensors(${frost.sensor.id})"),
        "patches the resolved Sensor by id");
    assertTrue(
        hasProcessor(
            flow, "InvokeHTTP", "HTTP URL", "/ObservedProperties(${frost.observedProperty.id})"),
        "patches the resolved ObservedProperty by id");
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
        "the Thing upsert body must be rendered from the captured attributes");
    assertTrue(
        hasProcessor(flow, "InvokeHTTP", "HTTP URL", "/Projects(7)/Things(${frost.thing.id})"),
        "an existing mapped Thing must be PATCHed by its resolved id");

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
  void guardsEmptyMatchKeysIntoTheErrorSink() throws Exception {
    // A record with an empty match-key value must never reach the lookup: the miss route would
    // CREATE an entity with an empty key that every later bad record silently converges on.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());
    String logId = component(flow, "processors", "LogMessage").get("identifier").asText();

    JsonNode guard = null;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("RouteOnAttribute")
          && c.path("properties").has("missing")) {
        guard = c;
      }
    }
    assertTrue(guard != null, "the mapped chain must contain the match-key guard");
    assertEquals(
        "${sta_2_reference:isEmpty():or(${sta_5_reference:isEmpty()})}",
        guard.get("properties").get("missing").asText(),
        "the guard must cover every match-key attribute of the plan");
    assertTrue(
        hasConnection(flow, guard.get("identifier").asText(), logId, "missing"),
        "an empty match key must route to the error sink");
    assertFalse(autoTerminates(guard, "unmatched"), "valid records must continue into the chain");
  }

  @Test
  void thingOnlyMappedChainTerminatesWithAutoTerminatedTails() throws Exception {
    // A metadata-only pipeline ends after the Thing stage. Its terminal relationships must be
    // auto-terminated: NiFi treats a processor with an unconnected relationship as invalid and
    // silently skips it on start — the queue in front would stall forever.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithThingOnlyMapping());

    // The chain ends at the create-confirm route (created-and-confirmed path); its 'unmatched'
    // must be auto-terminated. Neither id extractor is terminal now — the re-GET feeds the confirm
    // route.
    int extractors = 0;
    JsonNode route = null;
    JsonNode confirm = null;
    JsonNode patch = null;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      if (c.path("type").asText().endsWith("EvaluateJsonPath")
          && c.path("properties").has("frost.thing.id")) {
        extractors++;
      }
      if (c.path("type").asText().endsWith("RouteOnAttribute")
          && c.path("properties").path("new").asText().contains("frost.thing.id")) {
        route = c;
      }
      if (c.path("type").asText().endsWith("RouteOnAttribute")
          && c.path("properties").path("unconfirmed").asText().contains("frost.thing.id")) {
        confirm = c;
      }
      if (c.path("type").asText().endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("PATCH")) {
        patch = c;
      }
    }
    assertEquals(2, extractors, "lookup and re-GET extractors must exist");
    assertTrue(route != null, "the Thing route must exist");
    assertTrue(confirm != null, "the create-confirm route must exist");
    assertTrue(patch != null, "the Thing PATCH must exist");
    assertTrue(
        autoTerminates(confirm, "unmatched"),
        "the created-and-confirmed path must be auto-terminated at the chain end");
    assertFalse(autoTerminates(route, "unmatched"), "the found route must feed the Thing PATCH");
    assertTrue(
        autoTerminates(patch, "Original"),
        "the successful update path must be auto-terminated at the chain end");
    // no observation stage in this flow
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      assertFalse(
          c.path("properties").path("HTTP URL").asText().endsWith("/Observations"),
          "a thing-only mapping must not post observations");
    }
  }

  @Test
  void bothThingOutcomesFeedTheNextStage() throws Exception {
    // Regression guard against silent steady-state data loss: both the PATCHed-existing and the
    // created-and-confirmed Thing must feed the Datastream GET.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithMapping());

    String dsGetId = null;
    String confirmId = null;
    String patchId = null;
    for (JsonNode c : flow.get("flowContents").get("processors")) {
      String type = c.path("type").asText();
      String props = c.path("properties").toString();
      if (type.endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("GET")
          && c.path("properties").path("HTTP URL").asText().contains("/Datastreams")) {
        dsGetId = c.path("identifier").asText();
      }
      if (type.endsWith("RouteOnAttribute") && props.contains("unconfirmed")) {
        confirmId = c.path("identifier").asText();
      }
      if (type.endsWith("InvokeHTTP")
          && c.path("properties").path("HTTP Method").asText().equals("PATCH")
          && c.path("properties").path("HTTP URL").asText().contains("/Things(")) {
        patchId = c.path("identifier").asText();
      }
    }
    assertTrue(dsGetId != null, "the Datastream GET must exist");
    assertTrue(confirmId != null, "the Thing create-confirm route must exist");
    assertTrue(patchId != null, "the Thing update processor must exist");
    assertTrue(
        hasConnection(flow, confirmId, dsGetId, "unmatched"),
        "the created-and-confirmed Thing must feed the Datastream GET");
    assertTrue(
        hasConnection(flow, patchId, dsGetId, "Original"),
        "the PATCHed existing Thing must feed the Datastream GET");
  }

  @Test
  void aCreatedEntityContinuesOnOriginalBecauseTheResponseIsCapturedIntoAnAttribute()
      throws Exception {
    // Chained onto 'Response', a first delivery would write no observation at all while a
    // redelivery
    // hid it by taking the lookup-hit path — no error, no failure route, no bulletin.
    JsonNode flow = build(NifiTestFixtures.frostSinkWithThingOnlyMapping());

    JsonNode post = componentByProperty(flow, "InvokeHTTP", "HTTP Method", "POST");
    assertFalse(
        post.path("properties").path("Response Body Attribute Name").asText().isEmpty(),
        "the entity POST must capture the response body, so a 4xx can be logged with its cause");
    assertFalse(
        autoTerminates(post, "Original"),
        "the created entity leaves on 'Original' and must not be discarded there");
    assertTrue(
        autoTerminates(post, "Response"),
        "'Response' never fires while the body is captured, so it must stay terminated");

    String postId = post.get("identifier").asText();
    assertTrue(
        destinationOf(flow, postId, "Original") != null,
        "the created entity must be chained onwards from 'Original'");
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
  void buildsFrostUpsertChainWithoutDbcp() throws Exception {
    JsonNode flow = build(frostSink());
    // ConsumeMQTT + LogMessage + Thing leg (split, body, ref, GET, id, route, two restores,
    // POST, PATCH = 10)
    // + Observation leg (split, body, ref, GET, id, route, restore, inject, POST = 9) = 21
    assertEquals(21, flow.get("flowContents").get("processors").size());
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

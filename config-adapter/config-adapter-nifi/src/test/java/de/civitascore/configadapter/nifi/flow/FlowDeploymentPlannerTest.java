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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CredentialEncryptor;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowDeploymentPlannerTest {

  private static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  private static final String SECRET = "sup3r-s3cret-mqtt-pw";

  private final ObjectMapper mapper = new ObjectMapper();

  private byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  private FlowDeploymentPlanner planner(CredentialResolver resolver) {
    return new FlowDeploymentPlanner(
        new GraphParser(),
        new MappingConfigParser(),
        new RecordPathCompiler(),
        new NifiFlowBuilder(),
        resolver,
        new FlowDeploymentPlanner.PlatformSinkConfig(
            "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
        "http://frost:8080/FROST-Server/v1.1");
  }

  private Map<String, Object> map(String json) throws Exception {
    return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> graphWithMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.station_id": "$.station_id",
                  "$.temperature": "$.temperature",
                  "$.observed_at": { "op": "toDate", "input": "$.ts", "pattern": "yyyy-MM-dd" }
                } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-map" },
            { "id": "e2", "source": "n-map", "target": "n-end" }
          ]
        }
        """);
  }

  private Map<String, Object> graphWithGeoPoint() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.location": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" }
                } } } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-map" },
            { "id": "e2", "source": "n-map", "target": "n-end" }
          ]
        }
        """);
  }

  private Datasource mqttSource(String encryptedPassword) {
    Datasource source = new Datasource();
    source.setId("a1");
    source.setType("MQTT");
    // the portal connector shape: urls/topics are lists, user/client_id/qos scalars
    source.handleUnknownProperty("urls", List.of("tcp://mosquitto:1883"));
    source.handleUnknownProperty("topics", List.of("sensors/+/temp"));
    source.handleUnknownProperty("user", "mqttuser");
    source.handleUnknownProperty("client_id", "civitas-it");
    source.handleUnknownProperty("qos", 1);
    source.handleUnknownProperty("password", encryptedPassword);
    return source;
  }

  private SinkSpec postgisSink() {
    return new SinkSpec(SinkType.POSTGIS, "sensor_observations");
  }

  @Test
  void buildsDeployablePlanWithoutLeakingSecrets() throws Exception {
    byte[] key = stretchedKey();
    String enc =
        "ENC("
            + CredentialEncryptor.encrypt(
                SECRET, key, CredentialEncryptor.DATASOURCE_CREDENTIAL_CONTEXT)
            + ")";

    try (CredentialResolver resolver = new CredentialResolver(key)) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "b222", graphWithMapping(), mqttSource(enc), postgisSink()));

      // process-group name is the idempotency key
      assertEquals("pipeline-b222", plan.processGroupName());

      String snapshot = plan.snapshotJson();
      // non-sensitive slots are bound
      assertTrue(snapshot.contains("tcp://mosquitto:1883"));
      assertTrue(snapshot.contains("sensors/+/temp"));
      assertTrue(snapshot.contains("sensor_observations"));
      // compiled RecordPath mapping is present (copied field + a toDate conversion)
      assertTrue(snapshot.contains("station_id"));
      assertTrue(snapshot.contains("toDate(/ts, 'yyyy-MM-dd')"));

      // SECURITY INVARIANT: the plaintext secret never appears in the uploaded snapshot
      assertFalse(snapshot.contains(SECRET));
      // ...but is available for the post-upload sensitive-property push
      assertEquals(SECRET, plan.sensitivePropsByComponent().get("ConsumeMQTT").get("Password"));
    }
  }

  @Test
  void postgisSinkWithoutPlatformConnectionIsRejected() throws Exception {
    FlowDeploymentPlanner noDbPlanner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(stretchedKey()),
            null,
            "http://frost:8080/FROST-Server/v1.1");
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noDbPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nodb", graphWithMapping(), mqttSource(null), postgisSink())));
    assertEquals(
        de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  @Test
  void typedConversionPassesThroughWhenNoSinkSchema() throws Exception {
    // toInt/toFloat are transparent in the adapter — coercion happens at the sink
    // (PutDatabaseRecord
    // against the DB columns). A FROST sink without a data structure is therefore NOT rejected.
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-start", "type": "start", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.count": { "op": "toInt", "input": "$.n" } } } } },
                { "id": "n-end", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-start", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-end" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-typed", graph, mqttSource(null), new SinkSpec(SinkType.FROST, null)));
      // the conversion is rendered as a transparent record-path copy, no schema involved
      assertTrue(plan.snapshotJson().contains("/n"));
    }
  }

  @Test
  void frostSinkWithoutBaseUrlIsRejected() throws Exception {
    // A null FROST base URL must fail fast — otherwise the flow would deploy and silently POST
    // observations to a bogus/empty URL.
    FlowDeploymentPlanner noFrostPlanner =
        new FlowDeploymentPlanner(
            new GraphParser(),
            new MappingConfigParser(),
            new RecordPathCompiler(),
            new NifiFlowBuilder(),
            new CredentialResolver(stretchedKey()),
            new FlowDeploymentPlanner.PlatformSinkConfig(
                "jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
            null);
    FatalAdapterException ex =
        assertThrows(
            FatalAdapterException.class,
            () ->
                noFrostPlanner.plan(
                    new PipelineDeploymentRequest(
                        "p-nofrost",
                        graphWithMapping(),
                        mqttSource(null),
                        new SinkSpec(SinkType.FROST, null))));
    assertEquals(
        de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR, ex.getErrorCode());
  }

  // Note: rejection of a POSTGIS sink without a table name now happens at SinkSpec construction
  // (the type rejects the invalid state) — see PipelineDeploymentRequestTest.

  @Test
  void mixedConstAndCopyMappingIsAccepted() throws Exception {
    // A const is rendered as a RecordPath literal, so it shares one UpdateRecord with a copy — the
    // combination is valid (previously rejected as a "mixed strategy").
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-start", "type": "start", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": {
                      "$.station_id": "$.station_id",
                      "$.unit": { "op": "const", "value": "celsius" }
                    } } } },
                { "id": "n-end", "type": "end", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-start", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-end" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-mixed", graph, mqttSource(null), new SinkSpec(SinkType.FROST, null)));
      String snapshot = plan.snapshotJson();
      // both fields are bound: the copy as a record-path, the const as a literal-value — across the
      // two strategy-grouped UpdateRecord processors
      assertTrue(snapshot.contains("/station_id"));
      assertTrue(snapshot.contains("celsius"));
    }
  }

  @Test
  void toleratesRealFrontendNodeTypes() throws Exception {
    // the editor emits source/sink nodes (dataSource, frost, geoPersistence) alongside the mapping
    // —
    // the adapter must tolerate them and still build, not reject the graph
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-geo", "type": "geoPersistence", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-frost", "type": "frost", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-frost" },
                { "id": "e3", "source": "n-frost", "target": "n-geo" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      DeploymentPlan plan =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-real", graph, mqttSource(null), new SinkSpec(SinkType.FROST, null)));
      assertTrue(plan.snapshotJson().contains("/a")); // the mapping was still found and compiled
    }
  }

  @Test
  void cronTriggerIsRejected() throws Exception {
    // a cron-scheduled pipeline must not deploy as an unscheduled (timer-driven) flow
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-cron", "type": "cron", "data": { "cronExpression": "0 0 * * *" } },
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-frost", "type": "frost", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-frost" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-cron",
                              graph,
                              mqttSource(null),
                              new SinkSpec(SinkType.FROST, null))));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void multipleMappingNodesAreRejected() throws Exception {
    // the adapter builds a single transform; two mapping nodes are ambiguous and must fail loudly
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.a": "$.b" } } } },
                { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {
                    "fields": { "$.c": "$.d" } } } } ],
              "edges": [] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-2map", graph, mqttSource(null), postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void multipleMqttTopicsAreRejected() throws Exception {
    // one ConsumeMQTT subscribes to a single topic filter; a list of distinct topics must fail
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("topics", List.of("a/+", "b/+"));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-2topic", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void mqttTlsEnabledIsRejected() throws Exception {
    // TLS needs a NiFi SSL Context Service the adapter does not provision — reject, don't silently
    // deploy a plaintext connection
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("tls", Map.of("enabled", true));
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-tls", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void connectTimeoutAndKeepaliveAreBoundAsSeconds() throws Exception {
    // portal sends durations like "5s"/"30s"; NiFi's Connection Timeout / Keep Alive want plain
    // integer seconds, so they must be normalized and actually set (not silently ignored)
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("connect_timeout", "5s");
    source.handleUnknownProperty("keepalive", "30s");
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest("p-to", graphWithMapping(), source, postgisSink()))
              .snapshotJson();
      assertTrue(snapshot.contains("\"Connection Timeout\":\"5\""));
      assertTrue(snapshot.contains("\"Keep Alive\":\"30\""));
    }
  }

  @Test
  void blankTopicElementIsRejected() throws Exception {
    // after trimming, a blank-only topic list is empty → treated as missing, not a blank filter
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("topics", List.of("  "));
    assertPlanRejected(source, "p-blanktopic");
  }

  @Test
  void invalidConnectTimeoutIsRejected() throws Exception {
    // a non-blank but non-parseable duration must fail loudly, not silently fall back to NiFi's
    // default
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("connect_timeout", "soon");
    assertPlanRejected(source, "p-badto");
  }

  @Test
  void tlsBrokerSchemeIsRejected() throws Exception {
    // an ssl:// broker would need a NiFi SSL Context Service we do not provision — reject even when
    // the tls flag is absent
    Datasource source = mqttSource(null);
    source.handleUnknownProperty("urls", List.of("ssl://broker:8883"));
    assertPlanRejected(source, "p-ssl");
  }

  @Test
  void mappingNodeWithoutConfigIsRejected() throws Exception {
    // a wired mapping node with no mappingConfig is a corrupted payload — it must not deploy
    // untransformed
    Map<String, Object> graph =
        map(
            """
            { "nodes": [
                { "id": "n-src", "type": "dataSource", "data": {} },
                { "id": "n-map", "type": "mapping", "data": {} },
                { "id": "n-frost", "type": "frost", "data": {} } ],
              "edges": [
                { "id": "e1", "source": "n-src", "target": "n-map" },
                { "id": "e2", "source": "n-map", "target": "n-frost" } ] }
            """);
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-nocfg", graph, mqttSource(null), postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  private void assertPlanRejected(Datasource source, String pipelineId) throws Exception {
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              pipelineId, graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void geoPointMappingOnPostgisSinkCompilesToWkt() throws Exception {
    // Positive path: geometryEncoding(POSTGIS)=WKT propagates through plan(); the deployed snapshot
    // carries the WKT concat that the geometry column parses on insert.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      String snapshot =
          planner(resolver)
              .plan(
                  new PipelineDeploymentRequest(
                      "p-pg-geo", graphWithGeoPoint(), mqttSource(null), postgisSink()))
              .snapshotJson();
      assertTrue(snapshot.contains("concat('POINT(', /lon, ' ', /lat, ')')"));
    }
  }

  @Test
  void geoPointMappingOnFrostSinkIsRejected() throws Exception {
    // geometryEncoding(FROST)=GEOJSON, and geoPoint cannot be rendered as a GeoJSON object via
    // RecordPath, so compile() — and therefore plan() — must reject before deploying. The standard
    // planner has a FROST URL, proving the rejection is the encoding, not a missing URL.
    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p-frost-geo",
                              graphWithGeoPoint(),
                              mqttSource(null),
                              new SinkSpec(SinkType.FROST, null))));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_MAPPING_ERROR,
          ex.getErrorCode());
    }
  }

  @Test
  void withStringtypeUnspecifiedCoversAllBranches() {
    // no query string → append with '?'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?stringtype=unspecified",
        FlowDeploymentPlanner.withStringtypeUnspecified("jdbc:postgresql://db:5432/civitas"));
    // existing query string → append with '&'
    assertEquals(
        "jdbc:postgresql://db:5432/civitas?ssl=true&stringtype=unspecified",
        FlowDeploymentPlanner.withStringtypeUnspecified(
            "jdbc:postgresql://db:5432/civitas?ssl=true"));
    // already present → unchanged (idempotent, no double-append)
    String already = "jdbc:postgresql://db:5432/civitas?stringtype=unspecified";
    assertEquals(already, FlowDeploymentPlanner.withStringtypeUnspecified(already));
    // null / blank pass through untouched
    assertNull(FlowDeploymentPlanner.withStringtypeUnspecified(null));
    assertEquals("", FlowDeploymentPlanner.withStringtypeUnspecified(""));
  }

  @Test
  void unsupportedSourceSinkCombinationIsRejected() throws Exception {
    Datasource source = new Datasource();
    source.setId("x");
    source.setType("kafka"); // no curated template

    try (CredentialResolver resolver = new CredentialResolver(stretchedKey())) {
      FatalAdapterException ex =
          assertThrows(
              FatalAdapterException.class,
              () ->
                  planner(resolver)
                      .plan(
                          new PipelineDeploymentRequest(
                              "p1", graphWithMapping(), source, postgisSink())));
      assertEquals(
          de.civitascore.configadapter.model.AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          ex.getErrorCode());
    }
  }
}

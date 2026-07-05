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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.crypto.CryptoKeyLoader;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.credentials.CredentialResolver;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.stage.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.MappingNodeType;
import de.civitascore.configadapter.nifi.flow.stage.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.PostgisSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.SqlSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.ConversionOp;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.StaEnvelopeCompiler;
import de.civitascore.configadapter.nifi.mapping.ValueNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared fixture factories for planner/builder tests and the golden-snapshot characterization
 * tests. The inputs built here feed byte-level snapshot comparisons, so their values are part of
 * the committed golden files — change them only together with a golden regeneration.
 */
public final class NifiTestFixtures {

  static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private NifiTestFixtures() {}

  static byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  public static StageRegistry stageRegistry(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    return new StageRegistry(
        List.of(new MqttSourceStage(resolver), new SqlSourceStage(resolver, probe)),
        List.of(new PostgisSinkStage(platformSink), new FrostSinkStage(frostBaseUrl)),
        List.of(new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler())));
  }

  /** A builder over stages whose bind halves are never exercised (build-level tests). */
  public static NifiFlowBuilder flowBuilder() {
    return new NifiFlowBuilder(stageRegistry(null, SqlSourceProbe.NO_OP, null, null));
  }

  static FlowDeploymentPlanner planner(CredentialResolver resolver) {
    return planner(resolver, SqlSourceProbe.NO_OP);
  }

  static FlowDeploymentPlanner planner(CredentialResolver resolver, SqlSourceProbe probe) {
    return planner(
        resolver,
        probe,
        new PlatformSinkConfig("jdbc:postgresql://db:5432/civitas", "nifi", "db-secret"),
        "http://frost:8080/FROST-Server/v1.1");
  }

  public static FlowDeploymentPlanner planner(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    StageRegistry registry = stageRegistry(resolver, probe, platformSink, frostBaseUrl);
    return new FlowDeploymentPlanner(new GraphParser(), new NifiFlowBuilder(registry), registry);
  }

  static Map<String, Object> map(String json) throws Exception {
    return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
  }

  static Map<String, Object> graphWithMapping() throws Exception {
    return graphWithMapping("geoPersistence");
  }

  static Map<String, Object> graphWithMapping(String sinkType) throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.station_id": "$.station_id",
                  "$.temperature": "$.temperature",
                  "$.observed_at": { "op": "toDate", "input": "$.ts", "pattern": "yyyy-MM-dd" }
                } } } },
            { "id": "n-sink", "type": "%s", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(sinkType));
  }

  static Map<String, Object> graphWithGeoPoint() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.location": { "op": "geoPoint", "lon": "$.lon", "lat": "$.lat" }
                } } } },
            { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """);
  }

  /**
   * A mapping node targeting the STA envelope catalog paths (things + observations, mixed
   * strategies via the const).
   */
  static Map<String, Object> graphWithFrostMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.things[].name": "$.station",
                  "$.things[].description": { "op": "const", "value": "imported station" },
                  "$.things[].properties.reference": "$.ref",
                  "$.observations[].result": { "op": "toFloat", "input": "$.temp" },
                  "$.observations[].phenomenonTime": "$.ts",
                  "$.observations[].parameters.reference": "$.ref",
                  "$.observations[].parameters.name": "$.dsName"
                } } } },
            { "id": "n-sink", "type": "frost", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """);
  }

  /** A mapping node targeting only {@code $.things[].name} — misses the required lookup key. */
  static Map<String, Object> graphWithIncompleteFrostMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.things[].name": "$.station" } } } },
            { "id": "n-sink", "type": "frost", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """);
  }

  /** A graph with no mapping node — the source feeds the sink directly. */
  static Map<String, Object> graphWithoutMapping() throws Exception {
    return graphWithoutMapping("geoPersistence");
  }

  static Map<String, Object> graphWithoutMapping(String sinkType) throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-sink", "type": "%s", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-sink" },
            { "id": "e3", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(sinkType));
  }

  /** Two chained mapping nodes in front of a PostGIS sink (rename, then derive). */
  static Map<String, Object> graphWithChainedMappings() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.station_id": "$.id", "$.temperature": "$.temp" } } } },
            { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.unit": { "op": "const", "value": "celsius" } } } } },
            { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sink-1" } }
          ],
          "edges": [
            { "id": "e1", "source": "n-src", "target": "n-map1" },
            { "id": "e2", "source": "n-map1", "target": "n-map2" },
            { "id": "e3", "source": "n-map2", "target": "n-sink" }
          ]
        }
        """);
  }

  /**
   * Two chained mapping nodes in front of a FROST sink: the first is a plain record transform, the
   * LAST carries the STA envelope target paths.
   */
  static Map<String, Object> graphWithChainedFrostMappings() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map1", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.station": "$.station_raw", "$.ref": "$.ref_raw" } } } },
            { "id": "n-map2", "type": "mapping", "data": { "mappingConfig": {
                "fields": {
                  "$.things[].name": "$.station",
                  "$.things[].description": { "op": "const", "value": "imported station" },
                  "$.things[].properties.reference": "$.ref"
                } } } },
            { "id": "n-sink", "type": "frost", "data": { "entityId": "sink-1" } }
          ],
          "edges": [
            { "id": "e1", "source": "n-src", "target": "n-map1" },
            { "id": "e2", "source": "n-map1", "target": "n-map2" },
            { "id": "e3", "source": "n-map2", "target": "n-sink" }
          ]
        }
        """);
  }

  /** A full chain with the cron trigger wired in front of the source. */
  static Map<String, Object> graphWithCron(String cronExpression) throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "type": "start", "data": {} },
            { "id": "n-cron", "type": "cron", "data": { "cronExpression": "%s" } },
            { "id": "n-src", "type": "dataSource", "data": { "entityId": "src-1" } },
            { "id": "n-map", "type": "mapping", "data": { "mappingConfig": {
                "fields": { "$.a": "$.b" } } } },
            { "id": "n-sink", "type": "geoPersistence", "data": { "entityId": "sink-1" } },
            { "id": "n-end", "type": "end", "data": {} }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-cron" },
            { "id": "e2", "source": "n-cron", "target": "n-src" },
            { "id": "e3", "source": "n-src", "target": "n-map" },
            { "id": "e4", "source": "n-map", "target": "n-sink" },
            { "id": "e5", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(cronExpression));
  }

  static Datasource mqttSource(String encryptedPassword) {
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

  static Datasource sqlSource(String encryptedPassword) {
    Datasource source = new Datasource();
    source.setId("s1");
    source.setType("SQL");
    // the portal SQL connector shape (see datasources/contract)
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    source.handleUnknownProperty("table", "events");
    source.handleUnknownProperty("columns", List.of("*"));
    source.handleUnknownProperty("password", encryptedPassword);
    return source;
  }

  /** A SQL datasource with explicit connector fields (any may be null to omit it). */
  static Datasource sqlSourceWith(String driver, String table, Object columns, String where) {
    Datasource source = new Datasource();
    source.setId("s2");
    source.setType("SQL");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    if (driver != null) {
      source.handleUnknownProperty("driver", driver);
    }
    if (table != null) {
      source.handleUnknownProperty("table", table);
    }
    if (columns != null) {
      source.handleUnknownProperty("columns", columns);
    }
    if (where != null) {
      source.handleUnknownProperty("where", where);
    }
    return source;
  }

  /** A basic, valid SQL datasource (postgres) for negative/WHERE tests. */
  static Datasource sqlSourceBasic() {
    Datasource source = new Datasource();
    source.setId("s3");
    source.setType("SQL");
    source.handleUnknownProperty("driver", "postgres");
    source.handleUnknownProperty("dsn", "postgres://reader@srcdb:5432/in");
    source.handleUnknownProperty("user", "reader");
    source.handleUnknownProperty("table", "events");
    source.handleUnknownProperty("columns", List.of("*"));
    return source;
  }

  static SinkSpec postgisSink() {
    return new PostgisSinkSpec("sensor_observations");
  }

  /** A PostGIS sink with a primary key — required for a cron-scheduled (re-reading) SQL source. */
  static SinkSpec postgisSinkWithPk() {
    return new PostgisSinkSpec("sensor_observations", List.of("id"));
  }

  /** Wraps one node's compiled properties as the spec's mapping-unit list (empty stays empty). */
  static List<CompiledTransform> compiled(List<UpdateRecordProperty> properties) {
    return properties.isEmpty() ? List.of() : List.of(new CompiledMapping(properties));
  }

  static List<UpdateRecordProperty> mapping() {
    return List.of(
        new UpdateRecordProperty(
            "/title", "/name", RecordPathCompiler.ReplacementStrategy.RECORD_PATH_VALUE));
  }

  /**
   * A mapping mixing both replacement strategies — pins the two-UpdateRecord chain and its
   * first-seen strategy grouping order in snapshots.
   */
  static List<UpdateRecordProperty> mixedMapping() {
    return List.of(
        new UpdateRecordProperty(
            "/title", "/name", RecordPathCompiler.ReplacementStrategy.RECORD_PATH_VALUE),
        new UpdateRecordProperty(
            "/source", "imported", RecordPathCompiler.ReplacementStrategy.LITERAL_VALUE),
        new UpdateRecordProperty(
            "/temp", "/payload/temp", RecordPathCompiler.ReplacementStrategy.RECORD_PATH_VALUE));
  }

  static FlowBuildSpec mqttToPostgis(List<UpdateRecordProperty> mapping) {
    return new FlowBuildSpec(
        "pipeline-abc",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.POSTGIS,
        Map.of("Table Name", "sensor_observations"),
        compiled(mapping),
        Map.of(
            "PostGISConnectionPool",
            Map.of("Database Connection URL", "jdbc:postgresql://db:5432/x", "Database User", "u")),
        null,
        null);
  }

  /**
   * A SQL-source → PostGIS-sink flow. Mirrors the {@code datasources/contract} SQL config shape
   * ({@code table}/{@code columns}/{@code where}): the source pulls records from a table over a
   * dedicated source-side connection pool, the sink writes them to PostGIS over its own pool.
   */
  static FlowBuildSpec sqlToPostgis() {
    return sqlToPostgis(null);
  }

  static FlowBuildSpec sqlToPostgis(String sourceCron) {
    return new FlowBuildSpec(
        "pipeline-sql",
        SourceType.SQL,
        Map.of("Table Name", "events", "Columns to Return", "*"),
        SinkType.POSTGIS,
        Map.of("Table Name", "sensor_observations"),
        compiled(mapping()),
        Map.of(
            "SourceConnectionPool",
            Map.of(
                "Database Connection URL", "jdbc:postgresql://src:5432/in",
                "Database User", "reader"),
            "PostGISConnectionPool",
            Map.of("Database Connection URL", "jdbc:postgresql://db:5432/x", "Database User", "u")),
        sourceCron,
        null);
  }

  static FlowBuildSpec frostSink() {
    return new FlowBuildSpec(
        "pipeline-frost",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.FROST,
        Map.of(
            FrostSinkStage.FROST_BASE_URL,
            "http://frost:8080/FROST-Server/v1.1",
            FrostSinkStage.FROST_PROJECT_ID,
            "7"),
        // FROST: the source delivers the STA envelope; no record mapping (find-or-create works on
        // the raw JSON).
        List.of(),
        Map.of(),
        null,
        null);
  }

  /**
   * A mapped MQTT→FROST flow: the compiled flat mapping (mixed strategies via the const) plus the
   * envelope rebuild plan, both produced by the real {@link StaEnvelopeCompiler} so the golden pins
   * the actual compiler output.
   */
  static FlowBuildSpec frostSinkWithMapping() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.things[].name", new ValueNode.CopyNode("$.station"));
    fields.put("$.things[].description", new ValueNode.ConstNode("imported station", null));
    fields.put("$.things[].properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put(
        "$.observations[].result",
        new ValueNode.ConvertNode(ConversionOp.TO_FLOAT, new ValueNode.CopyNode("$.temp"), null));
    fields.put("$.observations[].phenomenonTime", new ValueNode.CopyNode("$.ts"));
    fields.put("$.observations[].parameters.reference", new ValueNode.CopyNode("$.ref"));
    fields.put("$.observations[].parameters.name", new ValueNode.CopyNode("$.dsName"));
    StaEnvelopeCompiler.EnvelopeCompilation compilation =
        new StaEnvelopeCompiler(new RecordPathCompiler())
            .compile(new MappingConfig(null, null, fields));
    return new FlowBuildSpec(
        "pipeline-frost-mapping",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.FROST,
        Map.of(
            FrostSinkStage.FROST_BASE_URL,
            "http://frost:8080/FROST-Server/v1.1",
            FrostSinkStage.FROST_PROJECT_ID,
            "7"),
        compiled(compilation.flatProperties()),
        Map.of(),
        null,
        compilation.plan());
  }
}

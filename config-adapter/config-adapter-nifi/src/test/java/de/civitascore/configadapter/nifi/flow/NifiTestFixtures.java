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
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkAuth;
import de.civitascore.configadapter.nifi.flow.stage.sink.FrostSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.sink.PostgisSinkStage;
import de.civitascore.configadapter.nifi.flow.stage.sink.SinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.source.MqttTruststoreConfig;
import de.civitascore.configadapter.nifi.flow.stage.source.SqlSourceStage;
import de.civitascore.configadapter.nifi.flow.stage.transform.MappingNodeType;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.mapping.CompiledMapping;
import de.civitascore.configadapter.nifi.mapping.CompiledTransform;
import de.civitascore.configadapter.nifi.mapping.ConversionOp;
import de.civitascore.configadapter.nifi.mapping.ForkPlan;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.ValueNode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared fixture factories for the planner/builder tests. */
public final class NifiTestFixtures {

  static final String MASTER_KEY_HEX =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  // Configuration CORE URNs the graph fixtures' source/sink nodes reference. The planner resolves
  // source/sink from the request, not the graph, so their exact values are inert here — they exist
  // only to make the graphs shaped like a real clean CORE Pipeline document.
  static final String SRC_REF = "urn:core:dataset:acme:datasource:sensors:Src:0000000001:1.0.0";
  static final String SINK_REF = "urn:core:dataset:acme:datasink:sensors:Sink:0000000002:1.0.0";

  // Mapping CORE URNs the graph fixtures' mapping nodes reference; resolved against MAPPINGS.
  static final String MAP_BASIC = "urn:core:dataset:acme:mapping:sensors:Basic:0000000010:1.0.0";
  static final String MAP_GEO = "urn:core:dataset:acme:mapping:sensors:Geo:0000000011:1.0.0";
  static final String MAP_FROST = "urn:core:dataset:acme:mapping:sensors:Frost:0000000012:1.0.0";
  static final String MAP_FROST_INCOMPLETE =
      "urn:core:dataset:acme:mapping:sensors:FrostInc:0000000013:1.0.0";
  static final String MAP_CRON = "urn:core:dataset:acme:mapping:sensors:Cron:0000000014:1.0.0";
  static final String MAP_TOINT = "urn:core:dataset:acme:mapping:sensors:ToInt:0000000015:1.0.0";
  static final String MAP_MIXED = "urn:core:dataset:acme:mapping:sensors:Mixed:0000000016:1.0.0";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final PlatformSinkConfig DEFAULT_PLATFORM_SINK =
      new PlatformSinkConfig("jdbc:postgresql://db:5432/civitas", "nifi", "db-secret");

  private static final String DEFAULT_FROST_BASE_URL = "http://frost:8080/FROST-Server/v1.1";

  /**
   * The shipped mappings catalog every planner fixture request carries — the callback-free
   * adapter's source of a {@code mappingRef}'s {@code fields}. Keyed by the Mapping CORE URNs the
   * graph fixtures reference; field order is preserved (LinkedHashMap) because the FROST compiler's
   * flat {@code sta_N} indices depend on it.
   */
  static final Map<String, Object> MAPPINGS;

  static {
    try {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put(
          MAP_BASIC,
          map(
              "{\"fields\": {\"$.station_id\":\"$.station_id\",\"$.temperature\":\"$.temperature\","
                  + "\"$.observed_at\":{\"op\":\"toDateTime\",\"input\":\"$.ts\",\"pattern\":\"yyyy-MM-dd\"}}}"));
      m.put(
          MAP_GEO,
          map(
              "{\"fields\": {\"$.location\":{\"op\":\"geoPoint\",\"lon\":\"$.lon\",\"lat\":\"$.lat\"}}}"));
      m.put(
          MAP_FROST,
          map(
              "{\"fields\": {\"$.name\":\"$.station\",\"$.description\":{\"op\":\"const\",\"value\":\"imported station\"},"
                  + "\"$.properties.reference\":\"$.ref\",\"$.Datastreams[].properties.reference\":\"$.ref\","
                  + "\"$.Datastreams[].Observations[].result\":{\"op\":\"toFloat\",\"input\":\"$.temp\"},"
                  + "\"$.Datastreams[].Observations[].phenomenonTime\":\"$.ts\"}}"));
      m.put(MAP_FROST_INCOMPLETE, map("{\"fields\": {\"$.name\":\"$.station\"}}"));
      m.put(MAP_CRON, map("{\"fields\": {\"$.a\":\"$.b\"}}"));
      m.put(MAP_TOINT, map("{\"fields\": {\"$.count\":{\"op\":\"toInt\",\"input\":\"$.n\"}}}"));
      m.put(
          MAP_MIXED,
          map(
              "{\"fields\": {\"$.station_id\":\"$.station_id\",\"$.unit\":{\"op\":\"const\",\"value\":\"celsius\"}}}"));
      MAPPINGS = Collections.unmodifiableMap(m);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private NifiTestFixtures() {}

  /** A deployment request carrying the shared {@link #MAPPINGS} catalog. */
  static PipelineDeploymentRequest req(
      String pipelineId, Map<String, Object> graph, Datasource source, SinkSpec sink) {
    return new PipelineDeploymentRequest(pipelineId, graph, source, sink, MAPPINGS);
  }

  static byte[] stretchedKey() throws Exception {
    return CryptoKeyLoader.stretchMasterKey(CryptoKeyLoader.hexStringToBytes(MASTER_KEY_HEX));
  }

  public static StageRegistry stageRegistry(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    return stageRegistry(
        resolver, probe, platformSink, frostBaseUrl, MqttTruststoreConfig.nodeTruststore());
  }

  public static StageRegistry stageRegistry(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl,
      MqttTruststoreConfig mqttTruststore) {
    return new StageRegistry(
        List.of(new MqttSourceStage(resolver, mqttTruststore), new SqlSourceStage(resolver, probe)),
        List.of(
            new PostgisSinkStage(platformSink),
            new FrostSinkStage(frostBaseUrl, FrostSinkAuth.basicAuth("frost", "secret"))),
        List.of(new MappingNodeType(new MappingConfigParser(), new RecordPathCompiler())));
  }

  /** A builder over stages whose bind halves are never exercised (build-level tests). */
  public static NifiFlowBuilder flowBuilder() {
    return flowBuilder(MqttTruststoreConfig.nodeTruststore());
  }

  public static NifiFlowBuilder flowBuilder(MqttTruststoreConfig mqttTruststore) {
    return new NifiFlowBuilder(
        stageRegistry(null, SqlSourceProbe.NO_OP, null, null, mqttTruststore));
  }

  static FlowDeploymentPlanner planner(CredentialResolver resolver) {
    return planner(resolver, SqlSourceProbe.NO_OP);
  }

  static FlowDeploymentPlanner planner(CredentialResolver resolver, SqlSourceProbe probe) {
    return planner(resolver, probe, DEFAULT_PLATFORM_SINK, DEFAULT_FROST_BASE_URL);
  }

  static FlowDeploymentPlanner planner(
      CredentialResolver resolver, MqttTruststoreConfig mqttTruststore) {
    return planner(
        resolver,
        SqlSourceProbe.NO_OP,
        DEFAULT_PLATFORM_SINK,
        DEFAULT_FROST_BASE_URL,
        mqttTruststore);
  }

  public static FlowDeploymentPlanner planner(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    return planner(
        resolver, probe, platformSink, frostBaseUrl, MqttTruststoreConfig.nodeTruststore());
  }

  public static FlowDeploymentPlanner planner(
      CredentialResolver resolver,
      SqlSourceProbe probe,
      PlatformSinkConfig platformSink,
      String frostBaseUrl,
      MqttTruststoreConfig mqttTruststore) {
    StageRegistry registry =
        stageRegistry(resolver, probe, platformSink, frostBaseUrl, mqttTruststore);
    return new FlowDeploymentPlanner(new GraphParser(), new NifiFlowBuilder(registry), registry);
  }

  static Map<String, Object> map(String json) throws Exception {
    return MAPPER.readValue(json, new TypeReference<>() {});
  }

  static Map<String, Object> graphWithMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-map", "kind": "mapping", "mappingRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(SRC_REF, MAP_BASIC, SINK_REF));
  }

  static Map<String, Object> graphWithGeoPoint() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-map", "kind": "mapping", "mappingRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(SRC_REF, MAP_GEO, SINK_REF));
  }

  /**
   * A mapping node targeting the record-anchored FROST catalog paths (creatable Thing, lookup-only
   * Datastream, observations, mixed strategies via the const) — see {@link #MAP_FROST}.
   */
  static Map<String, Object> graphWithFrostMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-map", "kind": "mapping", "mappingRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(SRC_REF, MAP_FROST, SINK_REF));
  }

  /**
   * A mapping node targeting only {@code $.name} — misses the Thing match key ({@link
   * #MAP_FROST_INCOMPLETE}).
   */
  static Map<String, Object> graphWithIncompleteFrostMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-map", "kind": "mapping", "mappingRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-map" },
            { "id": "e3", "source": "n-map", "target": "n-sink" },
            { "id": "e4", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(SRC_REF, MAP_FROST_INCOMPLETE, SINK_REF));
  }

  /** A graph with no mapping node — the source feeds the sink directly. */
  static Map<String, Object> graphWithoutMapping() throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
          ],
          "edges": [
            { "id": "e1", "source": "n-start", "target": "n-src" },
            { "id": "e2", "source": "n-src", "target": "n-sink" },
            { "id": "e3", "source": "n-sink", "target": "n-end" }
          ]
        }
        """
            .formatted(SRC_REF, SINK_REF));
  }

  /** A full chain with the cron trigger wired in front of the source. */
  static Map<String, Object> graphWithCron(String cronExpression) throws Exception {
    return map(
        """
        {
          "nodes": [
            { "id": "n-start", "kind": "start" },
            { "id": "n-cron", "kind": "cron", "cronExpression": "%s" },
            { "id": "n-src", "kind": "source", "sourceRef": "%s" },
            { "id": "n-map", "kind": "mapping", "mappingRef": "%s" },
            { "id": "n-sink", "kind": "sink", "sinkRef": "%s" },
            { "id": "n-end", "kind": "end" }
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
            .formatted(cronExpression, SRC_REF, MAP_CRON, SINK_REF));
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
    return properties.isEmpty()
        ? List.of()
        : List.of(new CompiledMapping(properties, ForkPlan.NONE));
  }

  /**
   * Wraps a FROST compilation, keeping its fan-out. Taking the compilation rather than its
   * properties is what keeps a fixture from silently losing the fork and asserting on a flow that
   * does not fan out.
   */
  static List<CompiledTransform> compiled(FrostMappingCompiler.FrostCompilation compilation) {
    return List.of(compilation.mapping());
  }

  static List<UpdateRecordProperty> mapping() {
    return List.of(
        new UpdateRecordProperty(
            "/title", "/name", RecordPathCompiler.ReplacementStrategy.RECORD_PATH_VALUE));
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

  /** The Thing/Datastream match keys of the mapped-FROST fixtures. */
  static final StaProperties STA_KEYS =
      StaProperties.ofKeys(List.of("reference"), List.of("reference"));

  /**
   * A metadata-only mapped MQTT→FROST flow: a creatable Thing and nothing else — the chain must
   * terminate cleanly after the Thing stage (no observation POST follows).
   */
  static FlowBuildSpec frostSinkWithThingOnlyMapping() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put("$.name", new ValueNode.CopyNode("$.station"));
    fields.put("$.description", new ValueNode.ConstNode("registered station", null));
    FrostMappingCompiler.FrostCompilation compilation =
        new FrostMappingCompiler(new RecordPathCompiler())
            .compile(new MappingConfig(null, null, fields), STA_KEYS);
    return new FlowBuildSpec(
        "pipeline-frost-thing-only",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/meta"),
        SinkType.FROST,
        Map.of(
            FrostSinkStage.FROST_BASE_URL,
            "http://frost:8080/FROST-Server/v1.1",
            FrostSinkStage.FROST_PROJECT_ID,
            "7"),
        compiled(compilation),
        Map.of(),
        null,
        compilation.plan());
  }

  /**
   * A mapped MQTT→FROST flow: the compiled flat mapping (mixed strategies via the const) plus the
   * entity plan, both produced by the real {@link FrostMappingCompiler} so the tests pin the actual
   * compiler output. Lookup-only datastream (no create set) with an observation — the
   * pre-existing-datastream shape.
   */
  static FlowBuildSpec frostSinkWithMapping() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.name", new ValueNode.CopyNode("$.station"));
    fields.put("$.description", new ValueNode.ConstNode("imported station", null));
    fields.put("$.properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put(
        "$.Datastreams[].Observations[].result",
        new ValueNode.ConvertNode(ConversionOp.TO_FLOAT, new ValueNode.CopyNode("$.temp"), null));
    fields.put("$.Datastreams[].Observations[].phenomenonTime", new ValueNode.CopyNode("$.ts"));
    fields.put("$.Datastreams[].properties.reference", new ValueNode.CopyNode("$.ref"));
    FrostMappingCompiler.FrostCompilation compilation =
        new FrostMappingCompiler(new RecordPathCompiler())
            .compile(new MappingConfig(null, null, fields), STA_KEYS);
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
        compiled(compilation),
        Map.of(),
        null,
        compilation.plan());
  }

  /**
   * A mapped MQTT→FROST flow whose sources read a nested array, so the compiler derives a fan-out.
   * Mirrors the reported structure: readings two array levels deep under a gateway.
   */
  static FlowBuildSpec frostSinkWithFanoutMapping() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.name", new ValueNode.CopyNode("$.station"));
    fields.put("$.description", new ValueNode.ConstNode("gateway", null));
    fields.put("$.properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put("$.Datastreams[].properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put(
        "$.Datastreams[].Observations[].result",
        new ValueNode.ConvertNode(
            ConversionOp.TO_FLOAT,
            new ValueNode.CopyNode("$.measurements[].measuredValues[].value"),
            null));
    fields.put(
        "$.Datastreams[].Observations[].phenomenonTime",
        new ValueNode.CopyNode("$.measurements[].measuredValues[].ts"));
    FrostMappingCompiler.FrostCompilation compilation =
        new FrostMappingCompiler(new RecordPathCompiler())
            .compile(new MappingConfig(null, null, fields), STA_KEYS);
    return new FlowBuildSpec(
        "pipeline-frost-fanout",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/temp"),
        SinkType.FROST,
        Map.of(
            FrostSinkStage.FROST_BASE_URL,
            "http://frost:8080/FROST-Server/v1.1",
            FrostSinkStage.FROST_PROJECT_ID,
            "7"),
        compiled(compilation),
        Map.of(),
        null,
        compilation.plan());
  }

  /** A fully creatable mapped chain including every related entity update stage. */
  static FlowBuildSpec frostSinkWithRelatedEntityMapping() throws Exception {
    Map<String, ValueNode> fields = new LinkedHashMap<>();
    fields.put("$.name", new ValueNode.CopyNode("$.station"));
    fields.put("$.description", new ValueNode.ConstNode("station", null));
    fields.put("$.properties.reference", new ValueNode.CopyNode("$.ref"));
    fields.put("$.Locations[].name", new ValueNode.ConstNode("location", null));
    fields.put("$.Locations[].description", new ValueNode.ConstNode("location", null));
    fields.put("$.Locations[].encodingType", new ValueNode.ConstNode("application/geo+json", null));
    fields.put(
        "$.Locations[].location",
        new ValueNode.GeoPointNode(
            new ValueNode.CopyNode("$.lon"), new ValueNode.CopyNode("$.lat")));
    fields.put("$.Datastreams[].name", new ValueNode.CopyNode("$.stream"));
    fields.put("$.Datastreams[].description", new ValueNode.ConstNode("stream", null));
    fields.put("$.Datastreams[].observationType", new ValueNode.ConstNode("measurement", null));
    fields.put("$.Datastreams[].unitOfMeasurement.name", new ValueNode.ConstNode("Celsius", null));
    fields.put("$.Datastreams[].unitOfMeasurement.symbol", new ValueNode.ConstNode("C", null));
    fields.put(
        "$.Datastreams[].unitOfMeasurement.definition", new ValueNode.ConstNode("ucum:Cel", null));
    fields.put("$.Datastreams[].Sensor.name", new ValueNode.ConstNode("sensor", null));
    fields.put("$.Datastreams[].Sensor.description", new ValueNode.ConstNode("sensor", null));
    fields.put(
        "$.Datastreams[].Sensor.encodingType", new ValueNode.ConstNode("application/pdf", null));
    fields.put("$.Datastreams[].Sensor.metadata", new ValueNode.ConstNode("metadata", null));
    fields.put(
        "$.Datastreams[].ObservedProperty.name", new ValueNode.ConstNode("temperature", null));
    fields.put(
        "$.Datastreams[].ObservedProperty.definition",
        new ValueNode.ConstNode("https://example.org/temperature", null));
    fields.put(
        "$.Datastreams[].ObservedProperty.description",
        new ValueNode.ConstNode("temperature", null));
    fields.put("$.Datastreams[].properties.reference", new ValueNode.CopyNode("$.ref"));
    FrostMappingCompiler.FrostCompilation compilation =
        new FrostMappingCompiler(new RecordPathCompiler())
            .compile(new MappingConfig(null, null, fields), STA_KEYS);
    return new FlowBuildSpec(
        "pipeline-frost-related-updates",
        SourceType.MQTT,
        Map.of("Broker URI", "tcp://mosquitto:1883", "Topic Filter", "sensors/+/meta"),
        SinkType.FROST,
        Map.of(
            FrostSinkStage.FROST_BASE_URL,
            "http://frost:8080/FROST-Server/v1.1",
            FrostSinkStage.FROST_PROJECT_ID,
            "7"),
        compiled(compilation),
        Map.of(),
        null,
        compilation.plan());
  }
}

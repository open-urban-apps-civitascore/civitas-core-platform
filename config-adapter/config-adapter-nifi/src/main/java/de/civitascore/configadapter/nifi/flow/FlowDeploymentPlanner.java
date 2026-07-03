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

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.Datasource;
import de.civitascore.configadapter.nifi.flow.NifiFlowBuilder.FlowBuildSpec;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest.SinkSpec;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.graph.GraphParser;
import de.civitascore.configadapter.nifi.graph.PipelineGraph;
import de.civitascore.configadapter.nifi.graph.PipelineGraph.GraphNode;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import de.civitascore.configadapter.nifi.mapping.MappingConfig;
import de.civitascore.configadapter.nifi.mapping.MappingConfigParser;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns one resolved pipeline (graph + source + sink) into a {@link DeploymentPlan}: it compiles
 * the mapping to RecordPath, resolves the source/sink processor properties, decrypts secrets
 * (collected separately for a post-upload REST push), and delegates the NiFi flow assembly to
 * {@link NifiFlowBuilder} (programmatic composition of building blocks per the graph).
 */
public class FlowDeploymentPlanner {

  private static final String DBCP = "PostGISConnectionPool";

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphParser graphParser;
  private final MappingConfigParser mappingConfigParser;
  private final RecordPathCompiler recordPathCompiler;
  private final NifiFlowBuilder flowBuilder;
  private final StageRegistry registry;
  private final PlatformSinkConfig platformSink;
  private final String frostBaseUrl;

  /** Platform-managed sink connection (not a tenant credential). */
  public record PlatformSinkConfig(String postgisUrl, String postgisUser, String postgisPassword) {}

  /**
   * Creates a planner.
   *
   * @param graphParser the graph parser
   * @param mappingConfigParser the mapping parser
   * @param recordPathCompiler the RecordPath compiler
   * @param flowBuilder the NiFi flow builder
   * @param registry the deployable source/sink stages
   * @param platformSink the platform sink connection (may be null)
   * @param frostBaseUrl the FROST SensorThings base URL for FROST sinks (may be null)
   */
  public FlowDeploymentPlanner(
      GraphParser graphParser,
      MappingConfigParser mappingConfigParser,
      RecordPathCompiler recordPathCompiler,
      NifiFlowBuilder flowBuilder,
      StageRegistry registry,
      PlatformSinkConfig platformSink,
      String frostBaseUrl) {
    this.graphParser = graphParser;
    this.mappingConfigParser = mappingConfigParser;
    this.recordPathCompiler = recordPathCompiler;
    this.flowBuilder = flowBuilder;
    this.registry = registry;
    this.platformSink = platformSink;
    this.frostBaseUrl = frostBaseUrl;
  }

  /**
   * Plans the deployment of a single pipeline.
   *
   * @param request the resolved pipeline inputs
   * @return the deployment plan
   * @throws FatalAdapterException if the source/sink is unsupported or the mapping is invalid
   */
  public DeploymentPlan plan(PipelineDeploymentRequest request) throws FatalAdapterException {
    PipelineGraph graph;
    try {
      graph = graphParser.parse(request.graphData());
    } catch (IllegalStateException e) {
      // a corrupt graph payload (missing/duplicate node id, edge to an unknown node) — rejected at
      // construction so a malformed graph never deploys silently
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    Optional<MappingConfig> mapping = parseMapping(graph);
    Optional<String> sourceCron = parseTriggerCron(graph);
    SinkSpec sink = request.sink();
    // A FROST sink runs the find-or-create on the raw SensorThings envelope and has no
    // record-mapping
    // stage — a configured mapping would be silently ignored. Reject the combination rather than
    // deploy a flow whose transformation never runs.
    if (sink.type() == SinkType.FROST && mapping.isPresent()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a FROST sink does not support a record mapping; the SensorThings envelope from the source"
              + " is consumed as-is");
    }
    // compile() throws a checked FatalAdapterException (an op may be unrenderable for the sink),
    // which a lambda in Optional.map() cannot propagate — hence the explicit isPresent() branch.
    List<UpdateRecordProperty> mappingProperties =
        mapping.isPresent()
            ? recordPathCompiler.compile(mapping.get(), geometryEncoding(sink.type()))
            : List.of();

    Datasource source = request.source();
    if (source == null) {
      throw template("<no source>");
    }
    SourceType sourceType =
        SourceType.fromRaw(source.getType()).orElseThrow(() -> template(source.getType()));

    // Cron schedules the source processor. ConsumeMQTT is push-based (it self-triggers on broker
    // messages), so a cron there would only throttle the drain, not the data — reject rather than
    // deploy a misleading schedule. Cron belongs on a pull source (SQL).
    if (sourceCron.isPresent() && sourceType == SourceType.MQTT) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "cron scheduling is not supported for push-based MQTT sources");
    }

    // A SQL source ALWAYS re-reads the whole table on a recurring schedule — an explicit cron, or
    // the QueryDatabaseTableRecord fragment's built-in default (every 5 min) when no cron node is
    // present — and it tracks no max-value column. So without a sink primary key the PostGIS write
    // stays INSERT and every run duplicates all rows. Require a key for ANY SQL→PostGIS pipeline
    // (from x-core-primaryKey on the target, or an explicit configuration.primaryKey); this also
    // surfaces the case where a marker exists but the schema is too ambiguous to resolve one.
    if (sourceType == SourceType.SQL
        && sink.type() == SinkType.POSTGIS
        && sink.primaryKeyColumns().isEmpty()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a SQL source writing to PostGIS requires a primary key on the target data structure (mark"
              + " the identifying attribute with the UML {id} flag) so re-read rows are"
              + " de-duplicated; none was resolved");
    }

    PlanContext out = new PlanContext();
    registry.source(sourceType).bind(source, out);
    bindSink(
        request, sink, out.sinkProperties(), out.controllerServiceProperties(), out.sensitive());

    String processGroupName = "pipeline-" + request.pipelineId();
    String snapshot =
        flowBuilder.build(
            new FlowBuildSpec(
                processGroupName,
                sourceType,
                out.sourceProperties(),
                sink.type(),
                out.sinkProperties(),
                mappingProperties,
                out.controllerServiceProperties(),
                sourceCron.orElse(null)));

    return new DeploymentPlan(processGroupName, snapshot, Map.copyOf(out.sensitive()));
  }

  private Optional<MappingConfig> parseMapping(PipelineGraph graph) throws FatalAdapterException {
    Optional<GraphNode> mappingNode;
    try {
      mappingNode = graph.transformNode();
    } catch (IllegalStateException e) {
      // an unbuildable graph topology (unsupported node kind, multiple/disconnected mapping nodes)
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    if (mappingNode.isEmpty()) {
      return Optional.empty();
    }
    Object rawConfig = mappingNode.get().data().get("mappingConfig");
    if (rawConfig == null) {
      // A wired mapping node must carry a config; a missing one is a corrupted payload that would
      // otherwise deploy untransformed. (A pipeline with no mapping node at all is fine — handled
      // above by the empty Optional.)
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "mapping node has no mappingConfig");
    }
    return Optional.of(mappingConfigParser.parse(mapper.valueToTree(rawConfig)));
  }

  private Optional<String> parseTriggerCron(PipelineGraph graph) throws FatalAdapterException {
    Optional<String> cron;
    try {
      cron = graph.triggerCron();
    } catch (IllegalStateException e) {
      // an unbuildable schedule (multiple cron nodes, blank expression)
      throw new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, e, e.getMessage());
    }
    if (cron.isPresent() && !isValidNifiCron(cron.get())) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "invalid NiFi cron expression: " + cron.get());
    }
    return cron;
  }

  /**
   * Whether {@code expression} is a NiFi cron — exactly 6 whitespace-separated fields ({@code sec
   * min hour day-of-month month day-of-week}). NiFi 2.x replaced Quartz with Spring's cron parser,
   * which dropped Quartz's optional 7th "year" field; a year-qualified expression passes a 7-field
   * count here but is then rejected inside NiFi at deploy as an opaque saga failure. NiFi itself
   * validates the field syntax; this only guards the field count so an obviously malformed value
   * fails the plan early rather than the remote NiFi REST call. Mirrors the 6-field check in the
   * editor's {@code validationService.isValidQuartzCron}. Package-private for unit testing.
   */
  static boolean isValidNifiCron(String expression) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    return expression.trim().split("\\s+").length == 6;
  }

  private void bindSink(
      PipelineDeploymentRequest request,
      SinkSpec sink,
      Map<String, String> sinkProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    switch (sink.type()) {
      case POSTGIS -> {
        // SinkSpec guarantees a non-blank tableName for POSTGIS (the invalid state is rejected at
        // construction), so PutDatabaseRecord always has a target here.
        sinkProperties.put("Table Name", sink.tableName());
        // With a primary key (the data structure's x-core-primaryKey marker), write UPSERT keyed on
        // it so a cron-recurring source that re-reads rows updates instead of duplicating them.
        // NiFi
        // does not derive the conflict key from the table PK — it must be given via Update Keys.
        // The PutDatabaseRecord fragment quotes identifiers and does NOT translate field names: the
        // PostGIS table is created with quoted (case-preserving) identifiers, so an UPSERT of a
        // camelCase key would otherwise emit an unquoted "ON CONFLICT (stationId)" that PostgreSQL
        // folds to "stationid" and rejects as a missing column.
        if (!sink.primaryKeyColumns().isEmpty()) {
          sinkProperties.put("Statement Type", "UPSERT");
          sinkProperties.put("Update Keys", String.join(",", sink.primaryKeyColumns()));
          // UPSERT needs the PostgreSQL DatabaseAdapter to emit ON CONFLICT; the default "Generic"
          // adapter throws "UPSERT not supported" and routes every record to failure.
          sinkProperties.put("Database Type", "PostgreSQL");
        }
        bindPlatformDbcp(controllerServiceProperties, sensitive);
      }
      case FROST -> bindFrost(request, sinkProperties);
    }
  }

  private void bindFrost(PipelineDeploymentRequest request, Map<String, String> sinkProperties)
      throws FatalAdapterException {
    if (frostBaseUrl == null || frostBaseUrl.isBlank()) {
      // A localhost fallback would deploy a flow that silently posts observations into the void.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the FROST base URL (nifi.frost.url) to be configured");
    }
    // The base URL feeds the find-or-create sub-flow (NifiFlowBuilder), which derives the per-stage
    // URLs (/Things, /Datastreams, /Observations) — not a single POST endpoint.
    sinkProperties.put(NifiFlowBuilder.FROST_BASE_URL, frostBaseUrl);
    // The saga's project id scopes those URLs to the dataset's FROST project; the request record
    // guarantees it is present and numeric for a FROST sink.
    sinkProperties.put(NifiFlowBuilder.FROST_PROJECT_ID, request.frostProjectId());
  }

  private void bindPlatformDbcp(
      Map<String, Map<String, String>> controllerServiceProperties,
      Map<String, Map<String, String>> sensitive)
      throws FatalAdapterException {
    if (platformSink == null
        || platformSink.postgisUrl() == null
        || platformSink.postgisUrl().isBlank()) {
      // A POSTGIS flow without a DB connection URL would deploy but never enable its DBCP service.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "POSTGIS sink configured but no platform database connection URL is available");
    }
    Map<String, String> dbcp = new LinkedHashMap<>();
    // Intentionally applied to every PostGIS sink (not only geoPoint flows): unspecified is benign
    // for non-geometry columns and is what lets a geoPoint WKT bind into a geometry column.
    putIfPresent(
        dbcp, "Database Connection URL", withStringtypeUnspecified(platformSink.postgisUrl()));
    putIfPresent(dbcp, "Database User", platformSink.postgisUser());
    if (!dbcp.isEmpty()) {
      controllerServiceProperties.put(DBCP, dbcp);
    }
    if (platformSink.postgisPassword() != null) {
      sensitive
          .computeIfAbsent(DBCP, k -> new LinkedHashMap<>())
          .put("Password", platformSink.postgisPassword());
    }
  }

  private static void putIfPresent(Map<String, String> target, String key, Object value) {
    if (value != null) {
      target.put(key, String.valueOf(value));
    }
  }

  private static FatalAdapterException template(String combination) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_TEMPLATE_ERROR, combination);
  }

  /** The geometry encoding a sink expects: PostGIS parses WKT, FROST expects GeoJSON. */
  private static GeometryEncoding geometryEncoding(SinkType sinkType) {
    return switch (sinkType) {
      case POSTGIS -> GeometryEncoding.WKT;
      case FROST -> GeometryEncoding.GEOJSON;
    };
  }

  /**
   * Ensures the PostGIS JDBC URL carries {@code stringtype=unspecified}, so PutDatabaseRecord's
   * string-bound WKT reaches a {@code geometry} column. With PgJDBC's default ({@code VARCHAR}) the
   * value is sent as {@code varchar}, which has no implicit cast to {@code geometry} →
   * type-mismatch error; {@code unspecified} sends it untyped so the server parses the WKT. Benign
   * for non-geometry columns. Package-private for unit testing.
   */
  static String withStringtypeUnspecified(String url) {
    if (url == null || url.isBlank() || url.contains("stringtype=")) {
      return url;
    }
    return url + (url.contains("?") ? '&' : '?') + "stringtype=unspecified";
  }
}

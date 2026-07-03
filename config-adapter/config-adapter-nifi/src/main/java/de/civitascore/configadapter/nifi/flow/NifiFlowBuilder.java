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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.ReplacementStrategy;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds a NiFi 2.x flow-snapshot JSON programmatically by composing curated per-component building
 * blocks (one processor/controller-service fragment each) according to the engine-neutral pipeline
 * graph. This is the single place that knows NiFi specifics: the adapter only ever emits known-safe
 * processors (ConsumeMQTT, ConvertRecord, UpdateRecord with RecordPath, PutDatabaseRecord, …) —
 * never scripting or Jolt — so a constrained graph can never smuggle arbitrary processors in. The
 * produced snapshot carries no secrets; sensitive controller-service properties are pushed
 * post-upload.
 */
public class NifiFlowBuilder {

  private static final String CS_TOKEN_PREFIX = "${CS:";
  private static final String STRATEGY_PROPERTY = "Replacement Value Strategy";

  /**
   * Sink-property key carrying the FROST SensorThings base URL into the find-or-create sub-flow
   * (set by {@code FlowDeploymentPlanner.bindFrost}; consumed here, not bound as a processor
   * property).
   */
  public static final String FROST_BASE_URL = "Frost Base URL";

  /**
   * Sink-property key carrying the dataset's FROST project id (numeric, from the saga's
   * create-project step; set by {@code FlowDeploymentPlanner.bindFrost}). Required for every FROST
   * sink — the build fails without it. It scopes the find-or-create sub-flow: Things are looked up
   * and created under {@code /Projects(n)} so they are visible through the dataset's project-scoped
   * named API, and the Datastream lookup filters on {@code Thing/Projects/id} (the projects plugin
   * exposes no direct {@code /Projects(n)/Datastreams} collection).
   */
  public static final String FROST_PROJECT_ID = "Frost Project Id";

  /** InvokeHTTP failure-side relationships routed to the error sink (find-or-create stages). */
  private static final List<String> HTTP_FAILURE_RELATIONSHIPS =
      List.of("Failure", "Retry", "No Retry");

  private static final String READER = "JsonTreeReader";
  private static final String WRITER = "JsonRecordSetWriter";
  private static final String DBCP = "PostGISConnectionPool";

  /** Friendly name of the source-side DB connection pool (SQL pull sources). */
  private static final String SOURCE_DBCP = "SourceConnectionPool";

  private final ObjectMapper mapper = new ObjectMapper();

  /**
   * The resolved inputs for one flow.
   *
   * @param processGroupName the process-group name
   * @param sourceType the datasource connector type
   * @param sourceProperties non-sensitive source processor properties (e.g. Broker URI, Topic
   *     Filter)
   * @param sinkType the datasink type
   * @param sinkProperties non-sensitive sink processor properties (e.g. Table Name)
   * @param mappingProperties compiled RecordPath properties (empty if the graph has no mapping
   *     node)
   * @param controllerServiceProperties non-sensitive controller-service properties, keyed by
   *     friendly name
   * @param sourceCron the cron (Quartz) schedule for the source processor, or {@code null} to keep
   *     the source fragment's built-in schedule
   */
  public record FlowBuildSpec(
      String processGroupName,
      SourceType sourceType,
      Map<String, String> sourceProperties,
      SinkType sinkType,
      Map<String, String> sinkProperties,
      List<UpdateRecordProperty> mappingProperties,
      Map<String, Map<String, String>> controllerServiceProperties,
      String sourceCron) {
    public FlowBuildSpec {
      Objects.requireNonNull(processGroupName, "processGroupName");
      Objects.requireNonNull(sourceType, "sourceType");
      Objects.requireNonNull(sinkType, "sinkType");
      sourceProperties = Map.copyOf(Objects.requireNonNull(sourceProperties, "sourceProperties"));
      sinkProperties = Map.copyOf(Objects.requireNonNull(sinkProperties, "sinkProperties"));
      mappingProperties =
          List.copyOf(Objects.requireNonNull(mappingProperties, "mappingProperties"));
      controllerServiceProperties =
          Map.copyOf(
              Objects.requireNonNull(controllerServiceProperties, "controllerServiceProperties"));
      // sourceCron is intentionally nullable — most flows keep the fragment's built-in schedule.
    }
  }

  private record Processor(ObjectNode node, String id, String outRelationship) {}

  /**
   * Builds the flow snapshot for the given spec.
   *
   * @param spec the resolved flow inputs
   * @return the NiFi flow-snapshot JSON
   * @throws FatalAdapterException if a source/sink combination is unsupported or a fragment is
   *     missing
   */
  public String build(FlowBuildSpec spec) throws FatalAdapterException {
    String pgId = deterministicId(spec.processGroupName());

    ObjectNode flow = mapper.createObjectNode();
    flow.put("identifier", pgId);
    flow.put("name", spec.processGroupName());
    flow.put("comments", "");
    flow.put("defaultFlowFileExpiration", "0 sec");
    flow.put("defaultBackPressureObjectThreshold", 10_000);
    flow.put("defaultBackPressureDataSizeThreshold", "1 GB");
    flow.put("flowFileConcurrency", "UNBOUNDED");
    flow.put("flowFileOutboundPolicy", "STREAM_WHEN_AVAILABLE");
    flow.put("scheduledState", "ENABLED");
    flow.put("executionEngine", "INHERITED");
    flow.put("maxConcurrentTasks", 1);
    flow.put("statelessFlowTimeout", "1 min");
    flow.put("componentType", "PROCESS_GROUP");
    ObjectNode pgPosition = flow.putObject("position");
    pgPosition.put("x", 0.0);
    pgPosition.put("y", 0.0);
    flow.putArray("processGroups");
    flow.putArray("remoteProcessGroups");
    flow.putArray("inputPorts");
    flow.putArray("outputPorts");
    flow.putArray("labels");
    flow.putArray("funnels");
    ArrayNode processors = flow.putArray("processors");
    ArrayNode controllerServices = flow.putArray("controllerServices");
    ArrayNode connections = flow.putArray("connections");

    // Controller services first — processors reference them by id.
    Map<String, String> csIdByName = new LinkedHashMap<>();
    addControllerServices(controllerServices, pgId, csIdByName, spec);

    // Processor chain: source -> [convert] -> [mapping...] -> sink. A mapping may need more than
    // one
    // UpdateRecord because a single processor allows only one Replacement Value Strategy, so const
    // (literal-value) fields and record-path fields land on separate processors. The convert step
    // exists only to turn a raw (non-record) source payload into records — the MQTT source emits
    // raw
    // bytes on 'Message'; the SQL source (QueryDatabaseTableRecord) already emits records on
    // 'success', so it is wired straight into the mapping/sink with no convert.
    boolean sqlSource = spec.sourceType() == SourceType.SQL;
    boolean frostSink = spec.sinkType() == SinkType.FROST;
    // The FROST find-or-create works on the SensorThings envelope ($.things/$.observations) that an
    // MQTT STA source delivers. A SQL source emits plain table records, which SplitJson would never
    // match — the flow would silently produce nothing. Reject the combination rather than deploy
    // it.
    if (frostSink && sqlSource) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires an MQTT SensorThings source; a SQL source does not emit the STA"
              + " envelope");
    }
    Processor source =
        loadProcessor(
            sourceFragment(spec.sourceType()), pgId, csIdByName, sqlSource ? "success" : "Message");
    applySchedule(source, spec.sourceCron());
    // ConvertRecord turns raw MQTT bytes into records; a SQL source already emits records, and the
    // FROST find-or-create works on the raw JSON envelope (SplitJson/EvaluateJsonPath) — so both
    // skip the convert step.
    Processor convert =
        (sqlSource || frostSink)
            ? null
            : loadProcessor("convert_record", pgId, csIdByName, "success");
    // FROST find-or-create works on the raw JSON envelope; a record-based UpdateRecord mapping
    // cannot run in that path (it would re-wrap the envelope). The source delivers the final STA
    // shape, so a mapping node is not applied for a FROST sink.
    List<Processor> mappingProcessors =
        frostSink ? List.of() : buildMappingProcessors(spec, pgId, csIdByName);

    // Common prefix: source -> [convert] -> [mapping...]. The sink stage differs: PostGIS/MQTT is a
    // single terminal processor (linear), FROST is a multi-stage find-or-create sub-flow.
    List<Processor> prefix = new ArrayList<>();
    prefix.add(source);
    if (convert != null) {
      prefix.add(convert);
    }
    prefix.addAll(mappingProcessors);
    bindProperties(prefix, spec);
    for (int i = 0; i < prefix.size(); i++) {
      processors.add(prefix.get(i).node());
      if (i > 0) {
        connections.add(connection(pgId, prefix.get(i - 1), prefix.get(i)));
      }
    }
    Processor tail = prefix.get(prefix.size() - 1);
    wireSinkStage(
        spec, pgId, csIdByName, processors, connections, tail, convert, mappingProcessors);

    ObjectNode root = mapper.createObjectNode();
    root.set("flowContents", flow);
    root.putObject("externalControllerServices");
    root.putObject("parameterContexts");
    root.putObject("parameterProviders");
    root.put("flowEncodingVersion", "1.0");
    root.put("latest", false);
    return serialize(root);
  }

  // ─── Fragment loading ───────────────────────────────────────────────────────

  /**
   * Adds the controller services the flow needs: the JSON reader/writer always, a source-side DB
   * pool for a SQL pull source, and the sink DB pool for a PostGIS sink.
   */
  private void addControllerServices(
      ArrayNode controllerServices, String pgId, Map<String, String> csIdByName, FlowBuildSpec spec)
      throws FatalAdapterException {
    addControllerService(controllerServices, "json_tree_reader", pgId, READER, csIdByName, spec);
    addControllerService(
        controllerServices, "json_record_set_writer", pgId, WRITER, csIdByName, spec);
    if (spec.sourceType() == SourceType.SQL) {
      // SQL pull source reads records over its own DB connection pool.
      addControllerService(
          controllerServices, "dbcp_connection_pool", pgId, SOURCE_DBCP, csIdByName, spec);
    }
    if (spec.sinkType() == SinkType.POSTGIS) {
      addControllerService(
          controllerServices, "dbcp_connection_pool", pgId, DBCP, csIdByName, spec);
    }
  }

  private void addControllerService(
      ArrayNode target,
      String fragment,
      String pgId,
      String friendlyName,
      Map<String, String> csIdByName,
      FlowBuildSpec spec)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    node.put("identifier", deterministicId(pgId + ":cs:" + friendlyName));
    node.put("groupIdentifier", pgId);
    // stamp the friendly name so the REST client can match sensitive properties by name post-upload
    node.put("name", friendlyName);
    ObjectNode props = (ObjectNode) node.get("properties");
    spec.controllerServiceProperties().getOrDefault(friendlyName, Map.of()).forEach(props::put);
    target.add(node);
    csIdByName.put(friendlyName, node.get("identifier").asText());
  }

  private Processor loadProcessor(
      String fragment, String pgId, Map<String, String> csIdByName, String outRelationship)
      throws FatalAdapterException {
    return loadProcessor(fragment, pgId, csIdByName, outRelationship, "");
  }

  /**
   * Loads a processor fragment with a deterministic id. The {@code discriminator} keeps ids unique
   * when the same fragment is instantiated more than once (e.g. one UpdateRecord per strategy).
   */
  private Processor loadProcessor(
      String fragment,
      String pgId,
      Map<String, String> csIdByName,
      String outRelationship,
      String discriminator)
      throws FatalAdapterException {
    ObjectNode node = loadFragment(fragment);
    String seed = pgId + ":proc:" + fragment + (discriminator.isEmpty() ? "" : ":" + discriminator);
    String id = deterministicId(seed);
    node.put("identifier", id);
    node.put("groupIdentifier", pgId);
    resolveControllerServiceReferences(node, csIdByName);
    return new Processor(node, id, outRelationship);
  }

  /** Replaces {@code ${CS:Name}} property tokens with the assigned controller-service id. */
  private void resolveControllerServiceReferences(
      ObjectNode component, Map<String, String> csIdByName) throws FatalAdapterException {
    JsonNode properties = component.get("properties");
    if (!(properties instanceof ObjectNode props)) {
      return;
    }
    var fields = props.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode value = entry.getValue();
      if (value.isTextual() && value.asText().startsWith(CS_TOKEN_PREFIX)) {
        String name =
            value.asText().substring(CS_TOKEN_PREFIX.length(), value.asText().length() - 1);
        String id = csIdByName.get(name);
        if (id == null) {
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_FLOW_ERROR, "unresolved controller-service reference: " + name);
        }
        props.put(entry.getKey(), id);
      }
    }
  }

  private ObjectNode loadFragment(String fragment) throws FatalAdapterException {
    String resource = "fragments/" + fragment + ".json";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_FLOW_ERROR, "missing NiFi component fragment: " + resource);
      }
      return (ObjectNode) mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new FatalAdapterException(AdapterErrorCode.NIFI_FLOW_ERROR, e, "reading " + resource);
    }
  }

  // ─── Property binding ───────────────────────────────────────────────────────

  /**
   * One {@code UpdateRecord} per replacement-value strategy, in first-seen order. NiFi allows a
   * single strategy per processor, so a mapping that mixes {@code const} (literal-value) with
   * copies/concats (record-path-value) is split across processors chained in sequence.
   */
  private List<Processor> buildMappingProcessors(
      FlowBuildSpec spec, String pgId, Map<String, String> csIdByName)
      throws FatalAdapterException {
    Map<ReplacementStrategy, List<UpdateRecordProperty>> byStrategy = new LinkedHashMap<>();
    for (UpdateRecordProperty property : spec.mappingProperties()) {
      byStrategy.computeIfAbsent(property.strategy(), k -> new ArrayList<>()).add(property);
    }
    List<Processor> result = new ArrayList<>();
    for (Map.Entry<ReplacementStrategy, List<UpdateRecordProperty>> group : byStrategy.entrySet()) {
      Processor processor =
          loadProcessor("update_record", pgId, csIdByName, "success", group.getKey().name());
      applyMapping(
          (ObjectNode) processor.node().get("properties"), group.getKey(), group.getValue());
      result.add(processor);
    }
    return result;
  }

  private void bindProperties(List<Processor> chain, FlowBuildSpec spec) {
    for (Processor processor : chain) {
      String type = processor.node().path("type").asText();
      ObjectNode props = (ObjectNode) processor.node().get("properties");
      if (type.endsWith("ConsumeMQTT") || type.endsWith("QueryDatabaseTableRecord")) {
        spec.sourceProperties().forEach(props::put);
      } else if (type.endsWith("PutDatabaseRecord") || type.endsWith("InvokeHTTP")) {
        spec.sinkProperties().forEach(props::put);
      }
      // UpdateRecord properties are applied in buildMappingProcessors (per strategy group).
    }
  }

  private void applyMapping(
      ObjectNode props,
      ReplacementStrategy strategy,
      List<UpdateRecordProperty> mappingProperties) {
    List<String> stale = new ArrayList<>();
    props
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (name.startsWith("/")) {
                stale.add(name);
              }
            });
    stale.forEach(props::remove);
    props.put(STRATEGY_PROPERTY, strategy.nifiValue());
    for (UpdateRecordProperty property : mappingProperties) {
      props.put(property.recordPath(), property.value());
    }
  }

  // ─── Error routing ──────────────────────────────────────────────────────────

  /**
   * Routes the {@code failure} relationships of the record processors AND the sink's own write
   * failures to a LogMessage sink (WARN + bulletin) instead of auto-terminating them. Otherwise a
   * malformed message, an unmappable record, or a failed write to FROST/PostGIS would be dropped
   * silently — invisible, undiagnosable data loss, which is exactly what must not happen at the
   * sink. The graph shape is intentionally stable: swapping LogMessage for a durable/recoverable
   * dead-letter sink is a later, isolated change.
   *
   * <p>The source is not wired here: ConsumeMQTT emits only {@code Message} and
   * QueryDatabaseTableRecord only {@code success} — neither has a parse-failure relationship to
   * route. {@code convert} is {@code null} for a SQL source (which emits records directly, so no
   * ConvertRecord step exists); its failure edge is then simply absent. Consequently a SQL-source
   * <em>runtime</em> failure (DB unreachable after deploy, query error) surfaces as a NiFi
   * processor bulletin, not as an error-sink record; deploy-time config errors are caught earlier
   * by the {@code JdbcSqlSourceProbe} and the bind-time guards in {@code FlowDeploymentPlanner}.
   */
  private void wireErrorSink(
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor convert,
      List<Processor> mappingProcessors,
      Processor sink,
      SinkType sinkType)
      throws FatalAdapterException {
    Processor errorSink = loadProcessor("log_message", pgId, csIdByName, null);
    processors.add(errorSink.node());
    if (convert != null) {
      connections.add(connection(pgId, convert, errorSink, "failure"));
    }
    for (Processor mapping : mappingProcessors) {
      connections.add(connection(pgId, mapping, errorSink, "failure"));
    }
    // The sink fragments auto-terminate their failure relationships by default; un-terminate them
    // (NiFi forbids a relationship being both auto-terminated and connected) and route them to the
    // same error sink so a failed write is logged, not lost.
    for (String relationship : sinkFailureRelationships(sinkType)) {
      removeAutoTerminated(sink.node(), relationship);
      connections.add(connection(pgId, sink, errorSink, relationship));
    }
  }

  /** The sink processor's failure-side relationships, by sink type. */
  private static List<String> sinkFailureRelationships(SinkType sinkType) {
    return switch (sinkType) {
      // InvokeHTTP: Original/Response stay terminated (the HTTP response is not consumed).
      case FROST -> List.of("Failure", "Retry", "No Retry");
      // PutDatabaseRecord: success stays terminated (the record landed).
      case POSTGIS -> List.of("failure", "retry");
    };
  }

  /** Removes a relationship from a processor's {@code autoTerminatedRelationships}, if present. */
  private static void removeAutoTerminated(ObjectNode processorNode, String relationship) {
    JsonNode auto = processorNode.get("autoTerminatedRelationships");
    if (auto instanceof ArrayNode array) {
      for (int i = array.size() - 1; i >= 0; i--) {
        if (relationship.equals(array.get(i).asText())) {
          array.remove(i);
        }
      }
    }
  }

  /**
   * Wires the sink stage onto the prefix tail: a FROST find-or-create sub-flow, or a single linear
   * terminal sink (PostGIS). Both route their write failures to a shared LogMessage error sink.
   */
  private void wireSinkStage(
      FlowBuildSpec spec,
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor tail,
      Processor convert,
      List<Processor> mappingProcessors)
      throws FatalAdapterException {
    if (spec.sinkType() == SinkType.FROST) {
      Processor errorSink = loadProcessor("log_message", pgId, csIdByName, null);
      processors.add(errorSink.node());
      if (convert != null) {
        connections.add(connection(pgId, convert, errorSink, "failure"));
      }
      for (Processor mapping : mappingProcessors) {
        connections.add(connection(pgId, mapping, errorSink, "failure"));
      }
      buildFrostFindOrCreate(spec, pgId, csIdByName, processors, connections, tail, errorSink);
    } else {
      Processor sink = loadProcessor(sinkFragment(spec.sinkType()), pgId, csIdByName, null);
      bindProperties(List.of(sink), spec);
      processors.add(sink.node());
      connections.add(connection(pgId, tail, sink));
      wireErrorSink(
          pgId,
          csIdByName,
          processors,
          connections,
          convert,
          mappingProcessors,
          sink,
          spec.sinkType());
    }
  }

  // ─── FROST find-or-create ─────────────────────────────────────────────────────

  /**
   * Builds the FROST find-or-create sub-flow, replacing a single {@code POST /Observations}. The
   * STA envelope is processed in two independent legs cloned from the source:
   *
   * <ul>
   *   <li><b>Things</b> ({@code $.things}): look up by {@code properties/reference}; POST only when
   *       absent, so a re-delivered message never duplicates a Thing.
   *   <li><b>Observations</b> ({@code $.observations}): look up the Datastream by {@code
   *       properties/reference} + {@code name}; if found, merge its {@code @iot.id} into the
   *       observation and POST {@code /Observations}; if not found, route to the error sink (the
   *       Datastream must exist — like the prior engine, the pipeline does not create it).
   * </ul>
   *
   * <p>Each leg captures the body into an attribute before the lookup GET (which overwrites the
   * content) and restores it before the write. <b>Every</b> failure relationship — split/extract
   * {@code failure}, the restore/inject {@code failure}, and the GET/POST {@code Failure/Retry/No
   * Retry} — routes to the shared error (log) sink, so a malformed envelope, unparseable lookup
   * response, or failed write is logged, never silently dropped.
   */
  private void buildFrostFindOrCreate(
      FlowBuildSpec spec,
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor upstream,
      Processor errorSink)
      throws FatalAdapterException {
    String base = spec.sinkProperties().getOrDefault(FROST_BASE_URL, "");
    String projectId = spec.sinkProperties().get(FROST_PROJECT_ID);
    // Every FROST flow is project-scoped: an unscoped flow would write to the server root,
    // invisible to the dataset's named API and open to cross-dataset reference collisions — so a
    // missing project id fails the build instead of degrading silently.
    if (projectId == null || projectId.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the '" + FROST_PROJECT_ID + "' sink property");
    }
    // Things live inside the dataset's FROST project so they are reachable through the
    // project-scoped named API; Observations stay at the root — their scope flows through the
    // resolved Datastream (whose lookup is project-filtered below).
    String thingBase = base + "/Projects(" + projectId + ")";
    buildThingLeg(thingBase, pgId, csIdByName, processors, connections, upstream, errorSink);
    buildObservationLeg(
        base, projectId, pgId, csIdByName, processors, connections, upstream, errorSink);
  }

  /** Things leg: find by reference, POST only when absent (idempotent create). */
  private void buildThingLeg(
      String base,
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor upstream,
      Processor errorSink)
      throws FatalAdapterException {
    Processor route =
        buildLookup(
            pgId,
            csIdByName,
            processors,
            connections,
            upstream,
            errorSink,
            new FrostLeg(
                "thing",
                "$.things",
                List.of(Map.entry("frost.ref", "$.properties.reference")),
                base
                    + "/Things?$filter=properties/reference%20eq%20"
                    + "'${frost.ref:replaceAll(\"'\",\"''\"):urlEncode()}'",
                "new",
                "${frost.id:isEmpty()}"));

    Processor restore = loadProcessor("replace_text", pgId, csIdByName, "success", "thingRestore");
    setProp(restore, "Replacement Value", "${frost.body}");
    Processor post = loadProcessor("invoke_http", pgId, csIdByName, null, "thingPost");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", base + "/Things");
    setProp(post, "Request Content-Type", "application/json");

    processors.add(restore.node());
    processors.add(post.node());
    connections.add(connection(pgId, route, restore));
    connections.add(connection(pgId, restore, post));
    routeFailure(pgId, connections, restore, errorSink);
    routeHttpFailures(pgId, connections, post, errorSink);
  }

  /**
   * Observations leg: find the Datastream, merge its id into the observation, POST (else log). The
   * lookup filters on {@code Thing/Projects/id} so a reference collision with another dataset's
   * Datastream cannot route observations across datasets (the projects plugin has no {@code
   * /Projects(n)/Datastreams} collection to scope the path itself).
   */
  private void buildObservationLeg(
      String base,
      String projectId,
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor upstream,
      Processor errorSink)
      throws FatalAdapterException {
    Processor route =
        buildLookup(
            pgId,
            csIdByName,
            processors,
            connections,
            upstream,
            errorSink,
            new FrostLeg(
                "obs",
                "$.observations",
                List.of(
                    Map.entry("frost.ref", "$.parameters.reference"),
                    Map.entry("frost.name", "$.parameters.name")),
                base
                    + "/Datastreams?$filter=properties/reference%20eq%20"
                    + "'${frost.ref:replaceAll(\"'\",\"''\"):urlEncode()}'"
                    + "%20and%20name%20eq%20'${frost.name:replaceAll(\"'\",\"''\"):urlEncode()}'"
                    + "%20and%20Thing/Projects/id%20eq%20"
                    + projectId,
                "found",
                "${frost.id:isEmpty():not()}"));
    // A missing Datastream must not be dropped silently.
    removeAutoTerminated(route.node(), "unmatched");
    connections.add(connection(pgId, route, errorSink, "unmatched"));

    Processor restore = loadProcessor("replace_text", pgId, csIdByName, "success", "obsRestore");
    setProp(restore, "Replacement Value", "${frost.body}");
    // Merge the resolved Datastream id as the first key of the observation object (no Jolt/script):
    // a regex replace of the leading brace injects "Datastream":{"@iot.id":<id>},. This assumes the
    // observation is a non-empty JSON object (a STA Observation always carries at least `result`,
    // so
    // it is never `{}`); the appended comma would otherwise produce a trailing comma. The
    // Datastream
    // @iot.id is numeric in FROST's default config, so it is injected unquoted.
    Processor inject = loadProcessor("replace_text", pgId, csIdByName, "success", "obsInject");
    setProp(inject, "Replacement Strategy", "Regex Replace");
    setProp(inject, "Search Value", "^\\{");
    setProp(inject, "Replacement Value", "{\"Datastream\":{\"@iot.id\":${frost.id}},");
    Processor post = loadProcessor("invoke_http", pgId, csIdByName, null, "obsPost");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", base + "/Observations");
    setProp(post, "Request Content-Type", "application/json");

    processors.add(restore.node());
    processors.add(inject.node());
    processors.add(post.node());
    connections.add(connection(pgId, route, restore));
    connections.add(connection(pgId, restore, inject));
    connections.add(connection(pgId, inject, post));
    routeFailure(pgId, connections, restore, errorSink);
    routeFailure(pgId, connections, inject, errorSink);
    routeHttpFailures(pgId, connections, post, errorSink);
  }

  /**
   * The per-leg parameters of a find-or-create lookup chain. {@code refProps} is an ordered list,
   * not a map: the keys are dynamic properties absent from the fragment, so they are appended to
   * the processor JSON in iteration order — and the snapshot must be byte-deterministic.
   */
  private record FrostLeg(
      String disc,
      String splitPath,
      List<Map.Entry<String, String>> refProps,
      String getUrl,
      String routeRelationship,
      String routeCondition) {}

  /**
   * Builds the shared lookup chain: split the array, capture the body (json) + reference
   * attribute(s), GET by {@code $filter}, extract the {@code @iot.id}, and route by the given
   * condition. Every intermediate {@code failure} (split/extract) and the GET's {@code
   * Failure/Retry/No Retry} are routed to the error sink so nothing is dropped silently. Returns
   * the route processor so the caller attaches its leg-specific tail.
   */
  private Processor buildLookup(
      String pgId,
      Map<String, String> csIdByName,
      ArrayNode processors,
      ArrayNode connections,
      Processor upstream,
      Processor errorSink,
      FrostLeg leg)
      throws FatalAdapterException {
    String disc = leg.disc();
    String splitPath = leg.splitPath();
    List<Map.Entry<String, String>> refProps = leg.refProps();
    String getUrl = leg.getUrl();
    String routeRelationship = leg.routeRelationship();
    String routeCondition = leg.routeCondition();
    Processor split = loadProcessor("split_json", pgId, csIdByName, "split", disc + "Split");
    setProp(split, "JsonPath Expression", splitPath);

    Processor extractBody =
        loadProcessor("evaluate_json_path", pgId, csIdByName, "matched", disc + "Body");
    setProp(extractBody, "Return Type", "json");
    setProp(extractBody, "frost.body", "$");

    Processor extractRef =
        loadProcessor("evaluate_json_path", pgId, csIdByName, "matched", disc + "Ref");
    for (Map.Entry<String, String> ref : refProps) {
      setProp(extractRef, ref.getKey(), ref.getValue());
    }

    Processor get = loadProcessor("invoke_http", pgId, csIdByName, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", getUrl);

    Processor extractId =
        loadProcessor("evaluate_json_path", pgId, csIdByName, "matched", disc + "Id");
    setProp(extractId, "frost.id", "$.value[0]['@iot.id']");

    Processor route =
        loadProcessor("route_on_attribute", pgId, csIdByName, routeRelationship, disc + "Route");
    setProp(route, routeRelationship, routeCondition);

    for (Processor p : List.of(split, extractBody, extractRef, get, extractId, route)) {
      processors.add(p.node());
    }
    connections.add(connection(pgId, upstream, split));
    connections.add(connection(pgId, split, extractBody));
    connections.add(connection(pgId, extractBody, extractRef));
    connections.add(connection(pgId, extractRef, get));
    removeAutoTerminated(get.node(), "Response");
    connections.add(connection(pgId, get, extractId));
    connections.add(connection(pgId, extractId, route));
    for (String relationship : HTTP_FAILURE_RELATIONSHIPS) {
      removeAutoTerminated(get.node(), relationship);
      connections.add(connection(pgId, get, errorSink, relationship));
    }
    // A malformed envelope (split) or an unparseable lookup response (extract) must be logged, not
    // dropped — route every intermediate 'failure' to the error sink.
    for (Processor stage : List.of(split, extractBody, extractRef, extractId)) {
      routeFailure(pgId, connections, stage, errorSink);
    }
    return route;
  }

  private void routeHttpFailures(
      String pgId, ArrayNode connections, Processor http, Processor errorSink) {
    for (String relationship : HTTP_FAILURE_RELATIONSHIPS) {
      removeAutoTerminated(http.node(), relationship);
      connections.add(connection(pgId, http, errorSink, relationship));
    }
  }

  /**
   * Routes a processor's single {@code failure} relationship to the error sink (no silent drop).
   */
  private void routeFailure(
      String pgId, ArrayNode connections, Processor processor, Processor errorSink) {
    removeAutoTerminated(processor.node(), "failure");
    connections.add(connection(pgId, processor, errorSink, "failure"));
  }

  private static void setProp(Processor processor, String key, String value) {
    ((ObjectNode) processor.node().get("properties")).put(key, value);
  }

  // ─── Connections ────────────────────────────────────────────────────────────

  private ObjectNode connection(String pgId, Processor source, Processor destination) {
    return connection(pgId, source, destination, source.outRelationship());
  }

  private ObjectNode connection(
      String pgId, Processor source, Processor destination, String relationship) {
    ObjectNode connection = mapper.createObjectNode();
    connection.put(
        "identifier",
        deterministicId(
            pgId + ":conn:" + source.id() + ":" + relationship + "->" + destination.id()));
    ObjectNode src = connection.putObject("source");
    src.put("id", source.id());
    src.put("type", "PROCESSOR");
    src.put("groupId", pgId);
    ObjectNode dst = connection.putObject("destination");
    dst.put("id", destination.id());
    dst.put("type", "PROCESSOR");
    dst.put("groupId", pgId);
    connection.put("groupIdentifier", pgId);
    ArrayNode rels = connection.putArray("selectedRelationships");
    rels.add(relationship);
    connection.put("backPressureObjectThreshold", 10_000);
    connection.put("backPressureDataSizeThreshold", "1 GB");
    connection.put("flowFileExpiration", "0 sec");
    connection.put("loadBalanceStrategy", "DO_NOT_LOAD_BALANCE");
    connection.put("loadBalanceCompression", "DO_NOT_COMPRESS");
    connection.put("labelIndex", 0);
    connection.put("zIndex", 0);
    connection.putArray("prioritizers");
    connection.putArray("bends");
    connection.put("componentType", "CONNECTION");
    return connection;
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  private static String sourceFragment(SourceType sourceType) {
    return switch (sourceType) {
      case MQTT -> "consume_mqtt";
      case SQL -> "query_database_table_record";
    };
  }

  /**
   * Switches the entry processor to cron-driven scheduling. A {@code null} cron leaves the source
   * fragment's built-in schedule (timer-driven for MQTT, the fragment default for SQL). Only the
   * source's schedule drives the flow — downstream processors stay timer-driven and run when
   * FlowFiles arrive in their queues.
   */
  private static void applySchedule(Processor source, String cron) {
    if (cron == null) {
      return;
    }
    source.node().put("schedulingStrategy", "CRON_DRIVEN");
    source.node().put("schedulingPeriod", cron);
  }

  private static String sinkFragment(SinkType sinkType) {
    return switch (sinkType) {
      case POSTGIS -> "put_database_record";
      case FROST -> "invoke_http";
    };
  }

  private static String deterministicId(String seed) {
    return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String serialize(ObjectNode root) throws FatalAdapterException {
    try {
      return mapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_FLOW_ERROR, e, "snapshot serialization");
    }
  }
}

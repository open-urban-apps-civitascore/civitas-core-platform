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

import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.removeAutoTerminated;
import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.setProp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.ConvertRecordStage;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.RecordMappingStage;
import de.civitascore.configadapter.nifi.flow.stage.SourceCapability;
import de.civitascore.configadapter.nifi.flow.stage.SourceStage;
import de.civitascore.configadapter.nifi.flow.stage.StageRegistry;
import de.civitascore.configadapter.nifi.flow.stage.StageResult;
import de.civitascore.configadapter.nifi.flow.stage.TransformStage;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

  private final ObjectMapper mapper = new ObjectMapper();
  private final StageRegistry registry;

  public NifiFlowBuilder(StageRegistry registry) {
    this.registry = registry;
  }

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

    BuildContext ctx =
        new BuildContext(mapper, pgId, spec, processors, controllerServices, connections);
    SourceStage sourceStage = registry.source(spec.sourceType());

    // Controller services first — processors reference them by id.
    addControllerServices(ctx, sourceStage, spec);

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
    // Common prefix: source -> [convert] -> [mapping...]. The sink stage differs: PostGIS/MQTT is a
    // single terminal processor (linear), FROST is a multi-stage find-or-create sub-flow.
    List<Processor> prefix = new ArrayList<>(sourceStage.build(ctx).chain());
    List<Processor> failureSources = new ArrayList<>();
    for (TransformStage transform : transformsFor(sourceStage, spec)) {
      StageResult result = transform.build(ctx);
      prefix.addAll(result.chain());
      failureSources.addAll(result.failureSources());
    }
    for (int i = 0; i < prefix.size(); i++) {
      ctx.addProcessor(prefix.get(i));
      if (i > 0) {
        ctx.addChainConnection(prefix.get(i - 1), prefix.get(i));
      }
    }
    Processor tail = prefix.get(prefix.size() - 1);
    wireSinkStage(ctx, spec, tail, failureSources);

    ObjectNode root = mapper.createObjectNode();
    root.set("flowContents", flow);
    root.putObject("externalControllerServices");
    root.putObject("parameterContexts");
    root.putObject("parameterProviders");
    root.put("flowEncodingVersion", "1.0");
    root.put("latest", false);
    return serialize(root);
  }

  // ─── Controller services ────────────────────────────────────────────────────

  /**
   * Adds the controller services the flow needs: the JSON reader/writer always (they are chain
   * infrastructure referenced by convert/mapping fragments), then the source stage's own services,
   * then the sink DB pool for a PostGIS sink. The order is part of the byte-stable snapshot
   * contract.
   */
  private void addControllerServices(BuildContext ctx, SourceStage sourceStage, FlowBuildSpec spec)
      throws FatalAdapterException {
    ctx.addControllerService(Fragment.JSON_TREE_READER, READER);
    ctx.addControllerService(Fragment.JSON_RECORD_SET_WRITER, WRITER);
    sourceStage.registerControllerServices(ctx);
    if (spec.sinkType() == SinkType.POSTGIS) {
      registry.sink(spec.sinkType()).registerControllerServices(ctx);
    }
  }

  // ─── Transforms ─────────────────────────────────────────────────────────────

  /**
   * The transforms between source and sink, derived structurally. ConvertRecord turns a raw source
   * payload into records — skipped when the source already emits records or the sink consumes the
   * raw JSON envelope (the FROST find-or-create works on SplitJson/EvaluateJsonPath, and a
   * record-based UpdateRecord mapping cannot run in that path: it would re-wrap the envelope).
   */
  private List<TransformStage> transformsFor(SourceStage source, FlowBuildSpec spec) {
    boolean rawJsonSink = spec.sinkType() == SinkType.FROST;
    List<TransformStage> transforms = new ArrayList<>();
    if (!rawJsonSink && !source.capabilities().contains(SourceCapability.EMITS_RECORDS)) {
      transforms.add(new ConvertRecordStage());
    }
    if (!rawJsonSink && !spec.mappingProperties().isEmpty()) {
      transforms.add(new RecordMappingStage());
    }
    return transforms;
  }

  // ─── Error routing ──────────────────────────────────────────────────────────

  /**
   * Wires the sink stage onto the prefix tail: a FROST find-or-create sub-flow, or a single linear
   * terminal sink (PostGIS). Both route their write failures to a shared LogMessage error sink.
   */
  private void wireSinkStage(
      BuildContext ctx, FlowBuildSpec spec, Processor tail, List<Processor> failureSources)
      throws FatalAdapterException {
    if (spec.sinkType() == SinkType.FROST) {
      Processor errorSink = ctx.loadProcessor(Fragment.LOG_MESSAGE, null);
      ctx.addProcessor(errorSink);
      for (Processor failureSource : failureSources) {
        ctx.addConnection(failureSource, errorSink, "failure");
      }
      buildFrostFindOrCreate(ctx, spec, tail, errorSink);
    } else {
      registry.sink(spec.sinkType()).build(ctx, tail, failureSources);
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
      BuildContext ctx, FlowBuildSpec spec, Processor upstream, Processor errorSink)
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
    buildThingLeg(ctx, thingBase, upstream, errorSink);
    buildObservationLeg(ctx, base, projectId, upstream, errorSink);
  }

  /** Things leg: find by reference, POST only when absent (idempotent create). */
  private void buildThingLeg(BuildContext ctx, String base, Processor upstream, Processor errorSink)
      throws FatalAdapterException {
    Processor route =
        buildLookup(
            ctx,
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

    Processor restore = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "thingRestore");
    setProp(restore, "Replacement Value", "${frost.body}");
    Processor post = ctx.loadProcessor(Fragment.INVOKE_HTTP, null, "thingPost");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", base + "/Things");
    setProp(post, "Request Content-Type", "application/json");

    ctx.addProcessor(restore);
    ctx.addProcessor(post);
    ctx.addChainConnection(route, restore);
    ctx.addChainConnection(restore, post);
    ctx.routeFailure(restore, errorSink);
    routeHttpFailures(ctx, post, errorSink);
  }

  /**
   * Observations leg: find the Datastream, merge its id into the observation, POST (else log). The
   * lookup filters on {@code Thing/Projects/id} so a reference collision with another dataset's
   * Datastream cannot route observations across datasets (the projects plugin has no {@code
   * /Projects(n)/Datastreams} collection to scope the path itself).
   */
  private void buildObservationLeg(
      BuildContext ctx, String base, String projectId, Processor upstream, Processor errorSink)
      throws FatalAdapterException {
    Processor route =
        buildLookup(
            ctx,
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
    removeAutoTerminated(route, "unmatched");
    ctx.addConnection(route, errorSink, "unmatched");

    Processor restore = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "obsRestore");
    setProp(restore, "Replacement Value", "${frost.body}");
    // Merge the resolved Datastream id as the first key of the observation object (no Jolt/script):
    // a regex replace of the leading brace injects "Datastream":{"@iot.id":<id>},. This assumes the
    // observation is a non-empty JSON object (a STA Observation always carries at least `result`,
    // so
    // it is never `{}`); the appended comma would otherwise produce a trailing comma. The
    // Datastream
    // @iot.id is numeric in FROST's default config, so it is injected unquoted.
    Processor inject = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "obsInject");
    setProp(inject, "Replacement Strategy", "Regex Replace");
    setProp(inject, "Search Value", "^\\{");
    setProp(inject, "Replacement Value", "{\"Datastream\":{\"@iot.id\":${frost.id}},");
    Processor post = ctx.loadProcessor(Fragment.INVOKE_HTTP, null, "obsPost");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", base + "/Observations");
    setProp(post, "Request Content-Type", "application/json");

    ctx.addProcessor(restore);
    ctx.addProcessor(inject);
    ctx.addProcessor(post);
    ctx.addChainConnection(route, restore);
    ctx.addChainConnection(restore, inject);
    ctx.addChainConnection(inject, post);
    ctx.routeFailure(restore, errorSink);
    ctx.routeFailure(inject, errorSink);
    routeHttpFailures(ctx, post, errorSink);
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
      BuildContext ctx, Processor upstream, Processor errorSink, FrostLeg leg)
      throws FatalAdapterException {
    String disc = leg.disc();
    String splitPath = leg.splitPath();
    List<Map.Entry<String, String>> refProps = leg.refProps();
    String getUrl = leg.getUrl();
    String routeRelationship = leg.routeRelationship();
    String routeCondition = leg.routeCondition();
    Processor split = ctx.loadProcessor(Fragment.SPLIT_JSON, "split", disc + "Split");
    setProp(split, "JsonPath Expression", splitPath);

    Processor extractBody =
        ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Body");
    setProp(extractBody, "Return Type", "json");
    setProp(extractBody, "frost.body", "$");

    Processor extractRef = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Ref");
    for (Map.Entry<String, String> ref : refProps) {
      setProp(extractRef, ref.getKey(), ref.getValue());
    }

    Processor get = ctx.loadProcessor(Fragment.INVOKE_HTTP, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", getUrl);

    Processor extractId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Id");
    setProp(extractId, "frost.id", "$.value[0]['@iot.id']");

    Processor route =
        ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, routeRelationship, disc + "Route");
    setProp(route, routeRelationship, routeCondition);

    for (Processor p : List.of(split, extractBody, extractRef, get, extractId, route)) {
      ctx.addProcessor(p);
    }
    ctx.addChainConnection(upstream, split);
    ctx.addChainConnection(split, extractBody);
    ctx.addChainConnection(extractBody, extractRef);
    ctx.addChainConnection(extractRef, get);
    removeAutoTerminated(get, "Response");
    ctx.addChainConnection(get, extractId);
    ctx.addChainConnection(extractId, route);
    for (String relationship : HTTP_FAILURE_RELATIONSHIPS) {
      removeAutoTerminated(get, relationship);
      ctx.addConnection(get, errorSink, relationship);
    }
    // A malformed envelope (split) or an unparseable lookup response (extract) must be logged, not
    // dropped — route every intermediate 'failure' to the error sink.
    for (Processor stage : List.of(split, extractBody, extractRef, extractId)) {
      ctx.routeFailure(stage, errorSink);
    }
    return route;
  }

  private void routeHttpFailures(BuildContext ctx, Processor http, Processor errorSink) {
    for (String relationship : HTTP_FAILURE_RELATIONSHIPS) {
      removeAutoTerminated(http, relationship);
      ctx.addConnection(http, errorSink, relationship);
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

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

/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage;

import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.removeAutoTerminated;
import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.setProp;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.flow.PipelineDeploymentRequest;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.mapping.FrostEnvelopePlan;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import java.util.List;
import java.util.Map;

/**
 * FROST SensorThings sink: a find-or-create sub-flow instead of a single terminal processor,
 * consuming the STA envelope ({@code $.things}/{@code $.observations}) in one of two modes.
 * <b>Passthrough</b> (no mapping): the source must deliver the envelope itself ({@link
 * SourceCapability#EMITS_STA_ENVELOPE}, MQTT). <b>Envelope rebuild</b> (record mapping present,
 * {@link MappingSupport#ENVELOPE}): any source works — the mapped record's flat fields are rebuilt
 * into the envelope by a generated ReplaceText template before the legs.
 */
public final class FrostSinkStage implements SinkStage {

  /**
   * Sink-property key carrying the FROST SensorThings base URL into the find-or-create sub-flow
   * (set by the bind half; consumed by the build half, not bound as a processor property).
   */
  public static final String FROST_BASE_URL = "Frost Base URL";

  /**
   * Sink-property key carrying the dataset's FROST project id (numeric, from the saga's
   * create-project step; set by the bind half). Required for every FROST sink — the build fails
   * without it. It scopes the find-or-create sub-flow: Things are looked up and created under
   * {@code /Projects(n)} so they are visible through the dataset's project-scoped named API, and
   * the Datastream lookup filters on {@code Thing/Projects/id} (the projects plugin exposes no
   * direct {@code /Projects(n)/Datastreams} collection).
   */
  public static final String FROST_PROJECT_ID = "Frost Project Id";

  /** InvokeHTTP failure-side relationships routed to the error sink (find-or-create stages). */
  private static final List<String> HTTP_FAILURE_RELATIONSHIPS =
      List.of("Failure", "Retry", "No Retry");

  private final String frostBaseUrl;

  public FrostSinkStage(String frostBaseUrl) {
    this.frostBaseUrl = frostBaseUrl;
  }

  @Override
  public SinkType type() {
    return SinkType.FROST;
  }

  @Override
  public SinkInput input() {
    return SinkInput.RAW_JSON;
  }

  @Override
  public Map<SourceCapability, String> requiredSourceCapabilities(boolean mappingPresent) {
    // With a mapping, the envelope is rebuilt from the mapped record — any source works (including
    // SQL). Without one, the find-or-create consumes the source's envelope as-is
    // ($.things/$.observations): a source emitting plain records would never match SplitJson and
    // the flow would silently produce nothing, so the passthrough case keeps the requirement.
    if (mappingPresent) {
      return Map.of();
    }
    return Map.of(
        SourceCapability.EMITS_STA_ENVELOPE,
        "FROST sink without a record mapping requires a source that emits the SensorThings"
            + " envelope (MQTT); add a record mapping or use an MQTT SensorThings source");
  }

  @Override
  public GeometryEncoding geometryEncoding() {
    return GeometryEncoding.GEOJSON;
  }

  @Override
  public MappingSupport mappingSupport() {
    // Raw-JSON sink, but a mapping is accepted: the compiled flat fields are rebuilt into the
    // envelope by the pre-region in build(...), driven by the plan's staEnvelope.
    return MappingSupport.ENVELOPE;
  }

  @Override
  public void bind(PipelineDeploymentRequest request, PlanContext out)
      throws FatalAdapterException {
    if (frostBaseUrl == null || frostBaseUrl.isBlank()) {
      // A localhost fallback would deploy a flow that silently posts observations into the void.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the FROST base URL (nifi.frost.url) to be configured");
    }
    // The base URL feeds the find-or-create sub-flow, which derives the per-stage URLs (/Things,
    // /Datastreams, /Observations) — not a single POST endpoint.
    out.putSinkProperty(FROST_BASE_URL, frostBaseUrl);
    // The saga's project id scopes those URLs to the dataset's FROST project; the request record
    // guarantees it is present and numeric for a FROST sink.
    out.putSinkProperty(FROST_PROJECT_ID, request.frostProjectId());
  }

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
  @Override
  public void build(
      BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources)
      throws FatalAdapterException {
    Processor errorSink = ctx.loadProcessor(Fragment.LOG_MESSAGE, null);
    ctx.addProcessor(errorSink);
    for (Processor failureSource : upstreamFailureSources) {
      ctx.addConnection(failureSource, errorSink, "failure");
    }

    String base = ctx.spec().sinkProperties().getOrDefault(FROST_BASE_URL, "");
    String projectId = ctx.spec().sinkProperties().get(FROST_PROJECT_ID);
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
    Processor upstream = buildEnvelopeRegion(ctx, upstreamTail, errorSink);
    buildThingLeg(ctx, thingBase, upstream, errorSink);
    buildObservationLeg(ctx, base, projectId, upstream, errorSink);
  }

  /**
   * The envelope-rebuild pre-region for a mapped flow: split the record-writer array into single
   * records ({@code $[*]} — the JsonRecordSetWriter always writes an array, and a SQL source
   * delivers many records per FlowFile; this is also what enforces 1 record = 1 STA element),
   * capture the flat mapped fields into FlowFile attributes, and ReplaceText the content with the
   * generated envelope template so the unchanged find-or-create legs consume their usual shape. A
   * passthrough flow (no mapping) returns {@code upstreamTail} untouched — that path stays
   * byte-identical.
   */
  private Processor buildEnvelopeRegion(
      BuildContext ctx, Processor upstreamTail, Processor errorSink) throws FatalAdapterException {
    FrostEnvelopePlan plan = ctx.spec().staEnvelope();
    if (plan == null) {
      return upstreamTail;
    }
    Processor split = ctx.loadProcessor(Fragment.SPLIT_JSON, "split", "staRecordSplit");
    setProp(split, "JsonPath Expression", "$[*]");
    Processor capture = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", "staCapture");
    for (String key : plan.flatKeys()) {
      setProp(capture, key, "$." + key);
    }
    Processor envelope = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "staEnvelope");
    setProp(envelope, "Replacement Value", plan.template());

    ctx.addProcessor(split);
    ctx.addProcessor(capture);
    ctx.addProcessor(envelope);
    ctx.addChainConnection(upstreamTail, split);
    ctx.addChainConnection(split, capture);
    ctx.addChainConnection(capture, envelope);
    // A record that fails to split, capture or rebuild must be logged, never dropped silently.
    ctx.routeFailure(split, errorSink);
    ctx.routeFailure(capture, errorSink);
    ctx.routeFailure(envelope, errorSink);
    return envelope;
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
    // Merge the resolved Datastream id as the first key of the observation object: a regex replace
    // of the leading brace injects "Datastream":{"@iot.id":<id>},. This assumes the observation is
    // a non-empty JSON object (a STA Observation always carries at least `result`, so it is never
    // `{}`); the appended comma would otherwise produce a trailing comma. The Datastream @iot.id is
    // numeric in FROST's default config, so it is injected unquoted.
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
}

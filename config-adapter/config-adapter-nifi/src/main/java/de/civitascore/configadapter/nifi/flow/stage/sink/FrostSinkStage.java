/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.flow.stage.sink;

import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.addAutoTerminated;
import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.removeAutoTerminated;
import static de.civitascore.configadapter.nifi.flow.stage.BuildContext.setProp;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.model.dataset.DataStructureSchema;
import de.civitascore.configadapter.model.dataset.DataStructureSchema.ResolvedDefinition;
import de.civitascore.configadapter.model.dataset.UnresolvableDataStructureException;
import de.civitascore.configadapter.nifi.flow.SinkResolutionContext;
import de.civitascore.configadapter.nifi.flow.SinkType;
import de.civitascore.configadapter.nifi.flow.stage.BuildContext;
import de.civitascore.configadapter.nifi.flow.stage.Fragment;
import de.civitascore.configadapter.nifi.flow.stage.MappingSupport;
import de.civitascore.configadapter.nifi.flow.stage.PayloadForm;
import de.civitascore.configadapter.nifi.flow.stage.PlanContext;
import de.civitascore.configadapter.nifi.flow.stage.Processor;
import de.civitascore.configadapter.nifi.flow.stage.SinkStage;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FreeAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.KeyAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaBagAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import de.civitascore.configadapter.nifi.mapping.SinkPreRegionPlan;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FROST SensorThings sink in one of two modes. <b>Passthrough</b> (no mapping): the source must
 * deliver the STA envelope itself ({@link PayloadForm#STA_ENVELOPE}, MQTT), consumed by two upsert
 * legs ({@code $.things}/{@code $.observations}). <b>Mapped</b> (record mapping present, {@link
 * MappingSupport#ENVELOPE}): any source works — the compiled {@link FrostEntityPlan} drives one
 * linear upsert chain per record (split → capture → Thing → Datastream → Observation).
 */
public final class FrostSinkStage implements SinkStage<FrostSinkSpec> {

  /**
   * Sink-property key carrying the FROST SensorThings base URL into the upsert sub-flow (set by the
   * bind half; consumed by the build half, not bound as a processor property).
   */
  public static final String FROST_BASE_URL = "Frost Base URL";

  /**
   * Sink-property key carrying the dataset's FROST project id (numeric, from the saga's
   * create-project step; set by the bind half). Required for every FROST sink — the build fails
   * without it. It scopes the upsert sub-flow: Things are looked up and written under {@code
   * /Projects(n)} so they are visible through the dataset's project-scoped named API, and the
   * Datastream lookup filters on {@code Thing/Projects/id} (the projects plugin exposes no direct
   * {@code /Projects(n)/Datastreams} collection).
   */
  public static final String FROST_PROJECT_ID = "Frost Project Id";

  /** Non-secret InvokeHTTP property used when FROST is configured with Basic Auth. */
  static final String FROST_BASIC_AUTH_USERNAME = "Frost Basic Auth Username";

  /** Friendly name shared by all FROST InvokeHTTP processors for post-upload secret patching. */
  static final String FROST_HTTP_PROCESSOR = "FrostPublish";

  /** InvokeHTTP failure-side relationships routed to the error sink (upsert stages). */
  private static final List<String> HTTP_FAILURE_RELATIONSHIPS =
      List.of("Failure", "Retry", "No Retry");

  private final String frostBaseUrl;
  private final FrostSinkAuth frostAuth;

  public FrostSinkStage(String frostBaseUrl, FrostSinkAuth frostAuth) {
    this.frostBaseUrl = frostBaseUrl;
    this.frostAuth = frostAuth;
  }

  @Override
  public SinkType type() {
    return SinkType.FROST;
  }

  @Override
  public Set<PayloadForm> acceptedInputs(boolean mappedUpstream) {
    // With a mapping, the entity bodies are rendered from the mapped record's flat fields — any
    // record-convertible source works (including SQL). Without one, the find-or-create consumes the
    // source's envelope
    // as-is ($.things/$.observations): a source emitting plain records would never match SplitJson
    // and the flow would silently produce nothing, so passthrough demands the envelope itself.
    return mappedUpstream ? Set.of(PayloadForm.RECORDS) : Set.of(PayloadForm.STA_ENVELOPE);
  }

  @Override
  public String inputRejectionMessage(boolean mappedUpstream) {
    // Only reachable in passthrough mode: with a mapping every payload form is RECORDS or
    // convertible, so no rejection can occur.
    return "FROST sink without a record mapping requires a source that emits the SensorThings"
        + " envelope (MQTT); add a record mapping or use an MQTT SensorThings source";
  }

  @Override
  public GeometryEncoding geometryEncoding() {
    return GeometryEncoding.GEOJSON;
  }

  @Override
  public MappingSupport mappingSupport() {
    // Raw-JSON sink, but a mapping is accepted: the compilation's entity plan drives the mapped
    // find-or-create chain in build(...).
    return MappingSupport.ENVELOPE;
  }

  @Override
  public Class<FrostSinkSpec> specType() {
    return FrostSinkSpec.class;
  }

  @Override
  public FrostSinkSpec parseSpec(Map<String, Object> datasink, SinkResolutionContext ctx)
      throws FatalAdapterException {
    // A FROST flow must be scoped to the dataset's project — unscoped it would post to the
    // server root, invisible to the project-scoped named API. The id is a saga-wide variable
    // from the FROST create/update-project step, so its absence means a mis-ordered or
    // hand-crafted saga.
    if (ctx.frostProjectId() == null || ctx.frostProjectId().isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "FROST sink requires the saga's 'projectId' (result of the FROST create-project step)");
    }
    try {
      return new FrostSinkSpec(ctx.frostProjectId(), resolveStaProperties(datasink));
    } catch (UnresolvableDataStructureException e) {
      // The one sink-spec defect the modeller can fix themselves (designate a root element in the
      // data structure) — its dedicated code carries that remedy as the safe external message,
      // where INVALID_PAYLOAD would only say "Validation failed".
      throw new FatalAdapterException(
          AdapterErrorCode.UNRESOLVABLE_DATA_STRUCTURE, e, e.getMessage());
    } catch (IllegalArgumentException e) {
      // e.g. a non-numeric projectId; keep the raw detail internal and publish only the safe
      // external message for the error code.
      throw new FatalAdapterException(AdapterErrorCode.INVALID_PAYLOAD, e, e.getMessage());
    }
  }

  /**
   * The {@code properties} bag of the sink's Thing-shaped target structure ({@code
   * datasinks[].dataStructure}, the mapping's target the portal embeds at publish time), per
   * entity: every attribute the bag declares, its match key being the {@code x-core-primaryKey}
   * attribute (fallback: one named {@code reference}) — SensorThings keeps identifiers in {@code
   * properties}, and so may any number of free attributes alongside it. Null when the datasink
   * carries no structure — a passthrough flow needs none; the mapping compilation rejects a mapped
   * flow without keys, since only it knows a mapping is present. A structurally broken schema
   * (unresolvable root, mis-shaped {@code Datastreams} class) throws {@link
   * IllegalArgumentException} instead of degrading to "no keys" — the caller turns it into a
   * payload error; a legitimately absent {@code Datastreams} or {@code properties} class yields
   * empty attributes.
   */
  @SuppressWarnings("unchecked")
  private static StaProperties resolveStaProperties(Map<String, Object> datasink) {
    if (!(datasink.get("dataStructure") instanceof Map<?, ?> ds)) {
      return null;
    }
    Map<String, Object> schema = (Map<String, Object>) ds;
    ResolvedDefinition thing = DataStructureSchema.resolveDefinitionAt(schema, List.of());
    List<StaBagAttribute> datastreamBag =
        thing.properties().containsKey("Datastreams")
            ? entityBag(schema, List.of("Datastreams"))
            : List.of();
    return new StaProperties(entityBag(schema, List.of()), datastreamBag);
  }

  @SuppressWarnings("unchecked")
  private static List<StaBagAttribute> entityBag(Map<String, Object> schema, List<String> path) {
    // SensorThings keeps identifiers (and any free attributes) in the entity's 'properties' bag
    // rather than top-level, so the bag is read from the 'properties' class, not the entity class
    // itself. A structurally broken entity class throws here (resolveDefinitionAt is unguarded) and
    // surfaces as a payload error rather than degrading to "no attributes".
    Object bagSpec =
        DataStructureSchema.resolveDefinitionAt(schema, path).properties().get("properties");
    if (!(bagSpec instanceof Map<?, ?> spec) || isEmptyBagSpec((Map<String, Object>) spec)) {
      // No 'properties' bag, or one modelled with no attributes: legitimate (passthrough /
      // lookup-only). A bag that DOES declare content (a $ref or nested properties) is resolved
      // below, so a broken $ref there still surfaces as a payload error rather than degrading
      // silently to "no attributes".
      return List.of();
    }
    List<String> propertiesPath = new ArrayList<>(path);
    propertiesPath.add("properties");

    // Resolves the bag class fully (follows a local $ref / allOf) — the modelled bag may be a named
    // class shared across entities.
    Map<String, Object> attributes =
        DataStructureSchema.resolveDefinitionAt(schema, propertiesPath).properties();
    String keyName = matchKeyName(schema, propertiesPath, attributes);

    List<StaBagAttribute> bag = new ArrayList<>();
    for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
      String name = attribute.getKey();
      if (name.equals(keyName)) {
        // The match key is a string identifier — it reaches the $filter and must be quoted.
        bag.add(new KeyAttribute(name));
        continue;
      }
      boolean scalar =
          attribute.getValue() instanceof Map<?, ?> attrSpec
              && DataStructureSchema.isScalar((Map<String, Object>) attrSpec);
      // A free scalar renders type-inferred (a mapped number stays a JSON number); a non-scalar
      // (object/array/$ref) is embedded verbatim as raw JSON.
      bag.add(new FreeAttribute(name, scalar ? StaJsonType.ANY : StaJsonType.RAW_JSON));
    }
    return bag;
  }

  /**
   * The bag's match-key attribute name: the {@code x-core-primaryKey}-marked attribute (only the
   * first — a composite key cannot be one bag attribute), else a declared {@code reference}
   * attribute, else null (no key; rejected by the compiler once the entity is mapped).
   */
  private static String matchKeyName(
      Map<String, Object> schema, List<String> propertiesPath, Map<String, Object> attributes) {
    List<String> marked = DataStructureSchema.primaryKeyColumnsAt(schema, propertiesPath);
    if (!marked.isEmpty()) {
      return marked.getFirst();
    }
    return attributes.containsKey("reference") ? "reference" : null;
  }

  /**
   * Whether a {@code properties} bag spec declares no resolvable content — an inline object without
   * {@code $ref}, {@code allOf} or nested {@code properties}. Such a bag legitimately carries no
   * attributes; a bag that does declare content is resolved by the caller so a broken reference in
   * it surfaces as an error instead of degrading to "no attributes".
   */
  private static boolean isEmptyBagSpec(Map<String, Object> spec) {
    if (spec.containsKey("$ref") || spec.containsKey("allOf")) {
      return false;
    }
    return !(spec.get("properties") instanceof Map<?, ?> nested) || nested.isEmpty();
  }

  @Override
  public void bind(FrostSinkSpec spec, PlanContext out) throws FatalAdapterException {
    if (frostBaseUrl == null || frostBaseUrl.isBlank()) {
      // A localhost fallback would deploy a flow that silently posts observations into the void.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the FROST base URL (nifi.frost.url) to be configured");
    }
    // The base URL feeds the find-or-create sub-flow, which derives the per-stage URLs (/Things,
    // /Datastreams, /Observations) — not a single POST endpoint.
    out.putSinkProperty(FROST_BASE_URL, frostBaseUrl);
    // The saga's project id scopes those URLs to the dataset's FROST project; the spec
    // guarantees it is present and numeric.
    out.putSinkProperty(FROST_PROJECT_ID, spec.projectId());
    // Authentication is platform-managed. The username may enter the snapshot, but the password or
    // password is patched onto every FrostPublish processor only after upload.
    frostAuth.bind(out);
  }

  /**
   * Builds the FROST sub-flow. A mapped flow (entity plan present) becomes the linear upsert chain
   * of {@link #buildMappedChain}. A passthrough flow processes the source's STA envelope in two
   * independent legs:
   *
   * <ul>
   *   <li><b>Things</b> ({@code $.things}): look up by {@code properties/reference}; PATCH the
   *       resolved {@code @iot.id} when present, otherwise POST a new Thing.
   *   <li><b>Observations</b> ({@code $.observations}): look up the Datastream by {@code
   *       properties/reference} + {@code name}; if found, merge its {@code @iot.id} into the
   *       observation and POST {@code /Observations}; if not found, route to the error sink (the
   *       Datastream must exist — the passthrough flow does not create it).
   * </ul>
   *
   * <p>Each passthrough leg captures the body into an attribute before the lookup GET (which
   * overwrites the content) and restores it before the write. In both modes <b>every</b> failure
   * relationship — split/extract {@code failure}, the restore/inject {@code failure}, and the
   * GET/POST {@code Failure/Retry/No Retry} — routes to the shared error (log) sink, so a malformed
   * payload, unparseable lookup response, or failed write is logged, never silently dropped.
   */
  @Override
  public void build(
      BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources)
      throws FatalAdapterException {
    Processor errorSink = ctx.loadProcessor(Fragment.LOG_MESSAGE, null);
    // Append the FROST response body: a create rejected by a SensorThings constraint (e.g. a
    // malformed TimeInterval, a missing required property) answers 4xx with the reason in the body,
    // which InvokeHTTP captures into frost.response.body. Without it the operator sees only the
    // status code and cannot tell which field the mapping got wrong.
    setProp(
        errorSink,
        "log-message",
        "Pipeline record dropped (cause in the failing processor's bulletin):"
            + " httpStatus=${invokehttp.status.code} exception=${invokehttp.java.exception.class}"
            + " frostResponse=${frost.response.body} file=${uuid}");
    ctx.addProcessor(errorSink);
    for (Processor failureSource : upstreamFailureSources) {
      ctx.addConnection(failureSource, errorSink, "failure");
    }

    String base = ctx.spec().sinkProperties().get(FROST_BASE_URL);
    String projectId = ctx.spec().sinkProperties().get(FROST_PROJECT_ID);
    // The build is reachable without the bind half (a FlowBuildSpec can be composed
    // directly), so its guarantees are re-checked here: a missing base URL would deploy relative
    // InvokeHTTP URLs
    // that post observations into the void, and an unscoped flow would write to the server root,
    // invisible to the dataset's named API and open to cross-dataset reference collisions.
    if (base == null || base.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the '" + FROST_BASE_URL + "' sink property");
    }
    if (projectId == null || projectId.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink requires the '" + FROST_PROJECT_ID + "' sink property");
    }
    // Things live inside the dataset's FROST project so they are reachable through the
    // project-scoped named API; Observations stay at the root — their scope flows through the
    // resolved Datastream (whose lookup is project-filtered below).
    SinkPreRegionPlan handoff = ctx.spec().sinkPreRegion();
    if (handoff == null) {
      // The build is reachable directly with a hand-composed spec, not only through the
      // planner: a compiled mapping without an entity plan would silently deploy an
      // untransformed passthrough — fail the build instead.
      if (ctx.spec().mappingPresent()) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "a record mapping was compiled for a raw-JSON sink but no entity plan was built");
      }
      // Passthrough: the source delivers the STA envelope itself; the legs consume it unchanged.
      String thingBase = base + "/Projects(" + projectId + ")";
      buildThingLeg(ctx, thingBase, upstreamTail, errorSink);
      buildObservationLeg(ctx, base, projectId, upstreamTail, errorSink);
      return;
    }
    if (!(handoff instanceof FrostEntityPlan plan)) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "FROST sink cannot consume the pre-region plan " + handoff.getClass().getSimpleName());
    }
    buildMappedChain(ctx, plan, base, projectId, upstreamTail, errorSink);
  }

  /**
   * The mapped flow: one linear upsert chain per record. The pre-region splits the record-writer
   * array into single records ({@code $[*]} — the JsonRecordSetWriter always writes an array, and a
   * SQL source delivers many records per FlowFile; this is also what enforces 1 record = 1 Thing)
   * and captures the flat mapped fields into FlowFile attributes; every later stage is
   * attribute-driven, so no body capture/restore is needed. The stages run strictly in sequence —
   * Thing before Datastream before Observation — because each entity's create needs its parent's
   * {@code @iot.id}; parallel legs would race a brand-new Thing against its first observation.
   *
   * <p>Each creatable entity stage is an upsert: GET by the plan's match-key filter, PATCH the
   * extracted {@code @iot.id} on a hit, or POST the body template and re-GET the id on a miss. A
   * miss on a lookup-only entity routes to the error sink, matching the passthrough legs'
   * semantics.
   */
  private void buildMappedChain(
      BuildContext ctx,
      FrostEntityPlan plan,
      String base,
      String projectId,
      Processor upstreamTail,
      Processor errorSink)
      throws FatalAdapterException {
    Processor split = ctx.loadProcessor(Fragment.SPLIT_JSON, "split", "staRecordSplit");
    setProp(split, "JsonPath Expression", "$[*]");
    Processor capture = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", "staCapture");
    for (String key : plan.flatKeys()) {
      setProp(capture, key, "$." + key);
    }
    // A record whose match-key value is missing/empty must not enter the chain: the lookup
    // filter would match nothing, the miss route would CREATE an entity with an empty key, and
    // every later empty-keyed record would silently converge on that garbage entity. Guard once
    // over all match keys and route offenders to the error sink.
    Processor keyGuard = ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, "unmatched", "staKeyGuard");
    setProp(keyGuard, "missing", emptyKeyCondition(plan));
    removeAutoTerminated(keyGuard, "unmatched");

    ctx.addProcessor(split);
    ctx.addProcessor(capture);
    ctx.addProcessor(keyGuard);
    ctx.addChainConnection(upstreamTail, split);
    ctx.addChainConnection(split, capture);
    ctx.addChainConnection(capture, keyGuard);
    ctx.addConnection(keyGuard, errorSink, "missing");
    // A record that fails to split or capture must be logged, never dropped silently.
    ctx.routeFailure(split, errorSink);
    ctx.routeFailure(capture, errorSink);

    String thingBase = base + "/Projects(" + projectId + ")";
    List<Tail> tails =
        buildEntityStage(
            ctx,
            "thing",
            thingBase + "/Things?$filter=" + filterExpression(plan.thingFilter(), null),
            FrostEntityPlan.THING_ID_ATTRIBUTE,
            plan.thingBody(),
            plan.thingUpdateBody(),
            thingBase + "/Things",
            List.of(new Tail(keyGuard, "unmatched")),
            errorSink);

    if (plan.locationBody() != null) {
      tails =
          buildRelatedCollectionUpsert(
              ctx,
              "location",
              base + "/Things(${" + FrostEntityPlan.THING_ID_ATTRIBUTE + "})/Locations",
              "frost.location.id",
              plan.locationBody(),
              base + "/Locations",
              tails,
              errorSink);
    }

    if (!plan.datastreamFilter().isEmpty()) {
      // The Datastream lookup filters on Thing/Projects/id: Datastreams are not project-scoped
      // themselves, so a match-key collision with another dataset must not resolve across
      // datasets.
      tails =
          buildEntityStage(
              ctx,
              "ds",
              base + "/Datastreams?$filter=" + filterExpression(plan.datastreamFilter(), projectId),
              FrostEntityPlan.DS_ID_ATTRIBUTE,
              plan.datastreamBody(),
              plan.datastreamUpdateBody(),
              base + "/Datastreams",
              tails,
              errorSink);

      if (plan.sensorBody() != null) {
        tails =
            buildRelatedEntityLookupAndPatch(
                ctx,
                "sensor",
                base + "/Datastreams(${" + FrostEntityPlan.DS_ID_ATTRIBUTE + "})/Sensor",
                "frost.sensor.id",
                plan.sensorBody(),
                base + "/Sensors",
                tails,
                errorSink);
      }
      if (plan.observedPropertyBody() != null) {
        tails =
            buildRelatedEntityLookupAndPatch(
                ctx,
                "observedProperty",
                base + "/Datastreams(${" + FrostEntityPlan.DS_ID_ATTRIBUTE + "})/ObservedProperty",
                "frost.observedProperty.id",
                plan.observedPropertyBody(),
                base + "/ObservedProperties",
                tails,
                errorSink);
      }
    }

    if (plan.observationBody() != null) {
      Processor renderBody = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "obsBody");
      setProp(renderBody, "Replacement Value", plan.observationBody());
      Processor post = loadFrostHttp(ctx, null, "obsPost");
      setProp(post, "HTTP Method", "POST");
      setProp(post, "HTTP URL", base + "/Observations");
      setProp(post, "Request Content-Type", "application/json");
      setProp(post, "Response Body Attribute Name", "frost.response.body");
      ctx.addProcessor(renderBody);
      ctx.addProcessor(post);
      connect(ctx, tails, renderBody);
      ctx.addChainConnection(renderBody, post);
      ctx.routeFailure(renderBody, errorSink);
      routeHttpFailures(ctx, post, errorSink);
      return;
    }
    // Without an observation the chain ends here — the metadata is ensured, nothing more to
    // write. The tails must be auto-terminated explicitly: the confirm route's 'unmatched' (the
    // created-and-confirmed path) is not auto-terminated by its fragment, and an unconnected
    // relationship leaves the processor invalid — NiFi silently skips invalid processors and the
    // queue in front stalls forever.
    for (Tail tail : tails) {
      addAutoTerminated(tail.processor(), tail.relationship());
    }
  }

  /**
   * The RouteOnAttribute condition matching a record with at least one empty match-key attribute
   * (EvaluateJsonPath represents a missing/null field as an empty attribute).
   */
  private String emptyKeyCondition(FrostEntityPlan plan) {
    List<FrostEntityPlan.FilterTerm> terms = new ArrayList<>(plan.thingFilter());
    terms.addAll(plan.datastreamFilter());
    StringBuilder condition =
        new StringBuilder("${").append(terms.getFirst().flatKey()).append(":isEmpty()");
    for (FrostEntityPlan.FilterTerm term : terms.subList(1, terms.size())) {
      condition.append(":or(${").append(term.flatKey()).append(":isEmpty()})");
    }
    return condition.append('}').toString();
  }

  /**
   * One lookup/upsert stage. Returns the tails both outcomes converge on: the successful PATCH of
   * an existing entity and — for a newly created entity — the confirmed re-GET result.
   */
  private List<Tail> buildEntityStage(
      BuildContext ctx,
      String disc,
      String lookupUrl,
      String idAttribute,
      String body,
      String updateBody,
      String postUrl,
      List<Tail> upstream,
      Processor errorSink)
      throws FatalAdapterException {
    Processor get = loadFrostHttp(ctx, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", lookupUrl);
    Processor extractId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Id");
    setProp(extractId, idAttribute, "$.value[0]['@iot.id']");
    Processor route = ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, "new", disc + "Route");
    setProp(route, "new", "${" + idAttribute + ":isEmpty()}");

    for (Processor p : List.of(get, extractId, route)) {
      ctx.addProcessor(p);
    }
    connect(ctx, upstream, get);
    removeAutoTerminated(get, "Response");
    ctx.addChainConnection(get, extractId);
    ctx.addChainConnection(extractId, route);
    routeHttpFailures(ctx, get, errorSink);
    ctx.routeFailure(extractId, errorSink);

    if (body == null) {
      // Lookup-only: the entity must pre-exist; a miss is a data error, not a create.
      ctx.addConnection(route, errorSink, "new");
      return List.of(new Tail(route, "unmatched"));
    }

    Tail updated = buildPatchPath(ctx, disc, idAttribute, updateBody, postUrl, route, errorSink);

    Processor renderBody = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", disc + "Body");
    setProp(renderBody, "Replacement Value", body);
    Processor post = loadFrostHttp(ctx, "Response", disc + "Post");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", postUrl);
    setProp(post, "Request Content-Type", "application/json");
    // Capture the FROST response body ONLY on the POST: a rejected create answers 4xx with the
    // failing constraint in the body, which the error sink logs. The GET/reGet responses must stay
    // in the FlowFile content — the EvaluateJsonPath extractors read $.value[0].@iot.id from it, so
    // diverting their body to an attribute would break find-or-create entirely.
    setProp(post, "Response Body Attribute Name", "frost.response.body");
    Processor reGet = loadFrostHttp(ctx, "Response", disc + "ReGet");
    setProp(reGet, "HTTP Method", "GET");
    setProp(reGet, "HTTP URL", lookupUrl);
    Processor reId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "ReId");
    setProp(reId, idAttribute, "$.value[0]['@iot.id']");
    // A POST that returns 2xx but whose re-GET cannot re-find the entity by its own match key
    // (FROST normalises/stores the key differently than rendered, a visibility edge, …) would
    // otherwise inject an empty id: the child stage would post "@iot.id":} and take a misleading
    // 400, or a terminal entity would drop silently. Route the still-empty id to the error sink as
    // its own condition so "create unconfirmed" is logged as itself.
    Processor confirm =
        ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, "unconfirmed", disc + "Confirm");
    setProp(confirm, "unconfirmed", "${" + idAttribute + ":isEmpty()}");

    for (Processor p : List.of(renderBody, post, reGet, reId, confirm)) {
      ctx.addProcessor(p);
    }
    ctx.addConnection(route, renderBody, "new");
    ctx.addChainConnection(renderBody, post);
    removeAutoTerminated(post, "Response");
    ctx.addChainConnection(post, reGet);
    removeAutoTerminated(reGet, "Response");
    ctx.addChainConnection(reGet, reId);
    ctx.addChainConnection(reId, confirm);
    ctx.routeFailure(renderBody, errorSink);
    routeHttpFailures(ctx, post, errorSink);
    routeHttpFailures(ctx, reGet, errorSink);
    ctx.routeFailure(reId, errorSink);
    ctx.addConnection(confirm, errorSink, "unconfirmed");

    return List.of(new Tail(confirm, "unmatched"), updated);
  }

  /**
   * Upserts the first entity of a parent's collection navigation. A missing child is POSTed to the
   * navigation collection, which makes SensorThings link it to the parent automatically; an
   * existing child is PATCHed by its resolved id. The mapped Thing shape contains one Location,
   * matching SensorThings' recommendation that a Thing normally has only one current Location.
   */
  private List<Tail> buildRelatedCollectionUpsert(
      BuildContext ctx,
      String disc,
      String navigationUrl,
      String idAttribute,
      String body,
      String entityUrl,
      List<Tail> upstream,
      Processor errorSink)
      throws FatalAdapterException {
    Processor get = loadFrostHttp(ctx, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", navigationUrl + "?$top=1");
    Processor extractId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Id");
    setProp(extractId, idAttribute, "$.value[0]['@iot.id']");
    Processor route = ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, "new", disc + "Route");
    setProp(route, "new", "${" + idAttribute + ":isEmpty()}");

    for (Processor processor : List.of(get, extractId, route)) {
      ctx.addProcessor(processor);
    }
    connect(ctx, upstream, get);
    removeAutoTerminated(get, "Response");
    ctx.addChainConnection(get, extractId);
    ctx.addChainConnection(extractId, route);
    routeHttpFailures(ctx, get, errorSink);
    ctx.routeFailure(extractId, errorSink);

    Tail updated = buildPatchPath(ctx, disc, idAttribute, body, entityUrl, route, errorSink);

    Processor renderBody = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", disc + "Body");
    setProp(renderBody, "Replacement Value", body);
    Processor post = loadFrostHttp(ctx, "Original", disc + "Post");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", navigationUrl);
    setProp(post, "Request Content-Type", "application/json");
    setProp(post, "Response Body Attribute Name", "frost.response.body");
    ctx.addProcessor(renderBody);
    ctx.addProcessor(post);
    ctx.addConnection(route, renderBody, "new");
    ctx.addChainConnection(renderBody, post);
    removeAutoTerminated(post, "Original");
    ctx.routeFailure(renderBody, errorSink);
    routeHttpFailures(ctx, post, errorSink);

    return List.of(new Tail(post, "Original"), updated);
  }

  /** Resolves a single-valued navigation entity and PATCHes the concrete entity by its id. */
  private List<Tail> buildRelatedEntityLookupAndPatch(
      BuildContext ctx,
      String disc,
      String navigationUrl,
      String idAttribute,
      String body,
      String entityUrl,
      List<Tail> upstream,
      Processor errorSink)
      throws FatalAdapterException {
    Processor get = loadFrostHttp(ctx, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", navigationUrl);
    Processor extractId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Id");
    setProp(extractId, idAttribute, "$['@iot.id']");
    Processor route = ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, "missing", disc + "Route");
    setProp(route, "missing", "${" + idAttribute + ":isEmpty()}");

    for (Processor processor : List.of(get, extractId, route)) {
      ctx.addProcessor(processor);
    }
    connect(ctx, upstream, get);
    removeAutoTerminated(get, "Response");
    ctx.addChainConnection(get, extractId);
    ctx.addChainConnection(extractId, route);
    routeHttpFailures(ctx, get, errorSink);
    ctx.routeFailure(extractId, errorSink);
    ctx.addConnection(route, errorSink, "missing");

    return List.of(buildPatchPath(ctx, disc, idAttribute, body, entityUrl, route, errorSink));
  }

  /** Builds the lookup-hit branch that PATCHes the concrete entity resolved by {@code @iot.id}. */
  private Tail buildPatchPath(
      BuildContext ctx,
      String disc,
      String idAttribute,
      String body,
      String entityUrl,
      Processor route,
      Processor errorSink)
      throws FatalAdapterException {
    Processor updateBody = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", disc + "UpdateBody");
    setProp(updateBody, "Replacement Value", body);
    Processor patch = loadFrostHttp(ctx, "Original", disc + "Patch");
    setProp(patch, "HTTP Method", "PATCH");
    setProp(patch, "HTTP URL", entityUrl + "(${" + idAttribute + "})");
    setProp(patch, "Request Content-Type", "application/json");
    setProp(patch, "Response Body Attribute Name", "frost.response.body");
    ctx.addProcessor(updateBody);
    ctx.addProcessor(patch);
    removeAutoTerminated(route, "unmatched");
    ctx.addConnection(route, updateBody, "unmatched");
    ctx.addChainConnection(updateBody, patch);
    removeAutoTerminated(patch, "Original");
    ctx.routeFailure(updateBody, errorSink);
    routeHttpFailures(ctx, patch, errorSink);
    return new Tail(patch, "Original");
  }

  /** A stage outcome: the processor and the relationship the next stage consumes. */
  private record Tail(Processor processor, String relationship) {}

  private void connect(BuildContext ctx, List<Tail> tails, Processor next) {
    for (Tail tail : tails) {
      if ("unmatched".equals(tail.relationship())) {
        removeAutoTerminated(tail.processor(), "unmatched");
      }
      ctx.addConnection(tail.processor(), next, tail.relationship());
    }
  }

  /**
   * The OData {@code $filter} expression of a stage's lookup, URL-encoded like the passthrough
   * legs': match-key terms conjoined with {@code and}, values escaped against quote breakout and
   * URL-encoded at flow time. {@code projectId} appends the Thing/Projects scope term when given.
   */
  private String filterExpression(List<FrostEntityPlan.FilterTerm> terms, String projectId) {
    StringBuilder filter = new StringBuilder();
    for (FrostEntityPlan.FilterTerm term : terms) {
      if (!filter.isEmpty()) {
        filter.append("%20and%20");
      }
      filter
          .append(term.frostPath())
          .append("%20eq%20'${")
          .append(term.flatKey())
          .append(":replaceAll(\"'\",\"''\"):urlEncode()}'");
    }
    if (projectId != null) {
      filter.append("%20and%20Thing/Projects/id%20eq%20").append(projectId);
    }
    return filter.toString();
  }

  /** Things leg: find by reference, PATCH the existing entity or POST when absent. */
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
    Processor post = loadFrostHttp(ctx, null, "thingPost");
    setProp(post, "HTTP Method", "POST");
    setProp(post, "HTTP URL", base + "/Things");
    setProp(post, "Request Content-Type", "application/json");

    Processor updateRestore =
        ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "thingUpdateRestore");
    setProp(updateRestore, "Replacement Value", "${frost.body}");
    Processor patch = loadFrostHttp(ctx, null, "thingPatch");
    setProp(patch, "HTTP Method", "PATCH");
    setProp(patch, "HTTP URL", base + "/Things(${frost.id})");
    setProp(patch, "Request Content-Type", "application/json");
    setProp(patch, "Response Body Attribute Name", "frost.response.body");

    ctx.addProcessor(restore);
    ctx.addProcessor(post);
    ctx.addProcessor(updateRestore);
    ctx.addProcessor(patch);
    ctx.addChainConnection(route, restore);
    ctx.addChainConnection(restore, post);
    removeAutoTerminated(route, "unmatched");
    ctx.addConnection(route, updateRestore, "unmatched");
    ctx.addChainConnection(updateRestore, patch);
    ctx.routeFailure(restore, errorSink);
    ctx.routeFailure(updateRestore, errorSink);
    routeHttpFailures(ctx, post, errorSink);
    routeHttpFailures(ctx, patch, errorSink);
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
    Processor post = loadFrostHttp(ctx, null, "obsPost");
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
    Processor split = ctx.loadProcessor(Fragment.SPLIT_JSON, "split", disc + "Split");
    setProp(split, "JsonPath Expression", leg.splitPath());

    Processor extractBody =
        ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Body");
    setProp(extractBody, "Return Type", "json");
    setProp(extractBody, "frost.body", "$");

    Processor extractRef = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Ref");
    for (Map.Entry<String, String> ref : leg.refProps()) {
      setProp(extractRef, ref.getKey(), ref.getValue());
    }

    Processor get = loadFrostHttp(ctx, "Response", disc + "Get");
    setProp(get, "HTTP Method", "GET");
    setProp(get, "HTTP URL", leg.getUrl());

    Processor extractId = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", disc + "Id");
    setProp(extractId, "frost.id", "$.value[0]['@iot.id']");

    Processor route =
        ctx.loadProcessor(Fragment.ROUTE_ON_ATTRIBUTE, leg.routeRelationship(), disc + "Route");
    setProp(route, leg.routeRelationship(), leg.routeCondition());

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

  /** Loads an InvokeHTTP processor and applies the non-secret half of FROST Basic Auth, if used. */
  private Processor loadFrostHttp(BuildContext ctx, String relationship, String discriminator)
      throws FatalAdapterException {
    Processor http = ctx.loadProcessor(Fragment.INVOKE_HTTP, relationship, discriminator);
    String username = ctx.spec().sinkProperties().get(FROST_BASIC_AUTH_USERNAME);
    if (username != null) {
      setProp(http, "Request Username", username);
    }
    return http;
  }
}

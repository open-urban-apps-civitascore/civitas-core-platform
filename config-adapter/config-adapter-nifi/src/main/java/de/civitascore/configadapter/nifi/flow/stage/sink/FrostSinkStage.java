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
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.FreeAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.KeyAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaBagAttribute;
import de.civitascore.configadapter.nifi.mapping.FrostMappingCompiler.StaProperties;
import de.civitascore.configadapter.nifi.mapping.FrostPortPlan;
import de.civitascore.configadapter.nifi.mapping.GeometryEncoding;
import de.civitascore.configadapter.nifi.mapping.SinkPort;
import de.civitascore.configadapter.nifi.mapping.SinkPreRegionPlan;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FROST SensorThings sink. {@code PutFrostRecord} writes every record through the port of the sink,
 * with a mapping or without one: a Mapping renders the record into the port structure, and without
 * a Mapping the record must have that structure already. Either way the sink consumes records
 * ({@link PayloadForm#RECORDS}), so a source that emits raw JSON — MQTT — gets a ConvertRecord in
 * front of it, and the split hands the processor one JSON object per record.
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

  /** Sink-property key carrying the selected port into the processor property of the same name. */
  public static final String FROST_PORT = "Port";

  /** Friendly name of the web client service the batch request uses. */
  static final String FROST_WEB_CLIENT = "FrostWebClient";

  /** Non-secret InvokeHTTP property used when FROST is configured with Basic Auth. */
  static final String FROST_BASIC_AUTH_USERNAME = "Frost Basic Auth Username";

  /** Friendly name shared by all FROST InvokeHTTP processors for post-upload secret patching. */
  static final String FROST_HTTP_PROCESSOR = "FrostPublish";

  /** The processor relationships that must not drop a record: both route to the error sink. */
  private static final List<String> SINK_FAILURE_RELATIONSHIPS = List.of("failure", "retry");

  /**
   * What an InvokeHTTP request does with its response, which decides the relationship its 2xx
   * FlowFile leaves on. Capturing the body into an attribute suppresses the response FlowFile on
   * success, so only {@code Original} carries the request FlowFile onwards and {@code Response}
   * never receives anything — one decision, never two.
   */
  private enum HttpResponseUse {
    CAPTURE_INTO_ATTRIBUTE("Original", true),
    /**
     * Required wherever a downstream EvaluateJsonPath reads the response, such as every
     * find-or-create lookup.
     */
    READ_FROM_CONTENT("Response", false),
    CAPTURE_AND_END(null, true),
    END(null, false);

    private final String continuation;
    private final boolean capturesBody;

    HttpResponseUse(String continuation, boolean capturesBody) {
      this.continuation = continuation;
      this.capturesBody = capturesBody;
    }

    /** The relationship the 2xx FlowFile leaves on, or null where the request is terminal. */
    String continuation() {
      return continuation;
    }

    boolean capturesBody() {
      return capturesBody;
    }
  }

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
    // Records in both modes. The chain splits a record array ($[*]) and PutFrostRecord reads one
    // JSON object per FlowFile, so a raw MQTT message has to become a record first — accepting
    // RECORDS is what makes the flow builder put a ConvertRecord in front. Accepting the raw
    // envelope instead split a JSON object into its member values, and every message reached the
    // processor as an array it refused.
    return Set.of(PayloadForm.RECORDS);
  }

  @Override
  public String inputRejectionMessage(boolean mappedUpstream) {
    // Not reachable today: every payload form is RECORDS or convertible to it. Kept precise for the
    // day a form appears that is neither.
    return "FROST sink requires records in the structure of its port; add a record mapping, or use"
        + " a source whose messages already have that structure";
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
      return new FrostSinkSpec(
          ctx.frostProjectId(), resolvePort(datasink), resolveStaProperties(datasink));
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
   * The port the Dataset configuration declares. There is no default and no fallback to the old
   * derivation from the Mapping: a sink without a port is not configured, and deriving one would
   * put the decision back where this field took it from.
   *
   * @throws FatalAdapterException when the configuration names no port, or names one outside the
   *     closed set. An unknown value must not pass through — it would reach a processor property.
   */
  @SuppressWarnings("unchecked")
  private static SinkPort resolvePort(Map<String, Object> datasink) throws FatalAdapterException {
    String declared = null;
    if (datasink.get("configuration") instanceof Map<?, ?> configuration) {
      Object value = ((Map<String, Object>) configuration).get("port");
      declared = value == null ? null : String.valueOf(value);
    }
    if (declared == null || declared.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.INVALID_PAYLOAD,
          "the FROST sink node carries no port; select one of: " + SinkPort.labels());
    }
    return SinkPort.fromLabel(declared)
        .orElseThrow(
            () ->
                new FatalAdapterException(
                    AdapterErrorCode.INVALID_PAYLOAD,
                    "the FROST sink node carries an unknown port; select one of: "
                        + SinkPort.labels()));
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
    String datastreamsProperty = entityPropertyName(thing.properties(), "Datastreams");
    List<StaBagAttribute> datastreamBag =
        datastreamsProperty != null ? entityBag(schema, List.of(datastreamsProperty)) : List.of();
    return new StaProperties(entityBag(schema, List.of()), datastreamBag);
  }

  /** Finds an entity collection exported from a labelled or an unlabelled UML relationship. */
  private static String entityPropertyName(Map<String, Object> properties, String expected) {
    if (properties.containsKey(expected)) {
      return expected;
    }
    String singular =
        expected.endsWith("s") ? expected.substring(0, expected.length() - 1) : expected;
    String match = null;
    for (String property : properties.keySet()) {
      if (!property.equalsIgnoreCase(expected) && !property.equalsIgnoreCase(singular)) {
        continue;
      }
      if (match != null) {
        return null;
      }
      match = property;
    }
    return match;
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
    // The port is the write logic. The spec guarantees it is one of the closed set, so the value
    // that reaches the processor property is never tenant text.
    out.putSinkProperty(FROST_PORT, spec.port().label());
    // Authentication is platform-managed. The username may enter the snapshot, but the password or
    // password is patched onto every FrostPublish processor only after upload.
    frostAuth.bind(out);
  }

  /** Registers the web client service the processor sends its batch request with. */
  @Override
  public void registerControllerServices(BuildContext ctx) throws FatalAdapterException {
    ctx.addControllerService(Fragment.WEB_CLIENT_SERVICE, FROST_WEB_CLIENT);
  }

  /**
   * Builds the FROST sub-flow: one processor, one topology.
   *
   * <p>The record array of the writer is split into single records, the mapped fields are captured
   * into attributes, the port body is rendered from them, and {@code PutFrostRecord} writes the
   * record. The processor decides find-or-create on the server through the {@code if} condition of
   * the batch extension, so the twenty to forty processors of the generated graph are gone, and
   * every Pipeline gets the same shape.
   *
   * <p>Every failure relationship routes to the shared error sink — the split, the capture, the
   * render, and the processor's own {@code failure}. Its {@code retry} goes there too: NiFi retries
   * that relationship by the fragment's retry settings and transfers the record on when the
   * attempts are used up, and an unrouted relationship would drop it silently.
   */
  @Override
  public void build(
      BuildContext ctx, Processor upstreamTail, List<Processor> upstreamFailureSources)
      throws FatalAdapterException {
    Processor errorSink = ctx.loadProcessor(Fragment.LOG_MESSAGE, null);
    // A record rejected by the processor carries the entity, the status and the reason FROST gave.
    setProp(
        errorSink,
        "log-message",
        "Pipeline record dropped: entity=${frost.error.entity} status=${frost.error.status}"
            + " reason=${frost.error.message} file=${uuid}");
    ctx.addProcessor(errorSink);
    for (Processor failureSource : upstreamFailureSources) {
      ctx.addConnection(failureSource, errorSink, "failure");
    }

    String base = requireSinkProperty(ctx, FROST_BASE_URL);
    String projectId = requireSinkProperty(ctx, FROST_PROJECT_ID);
    String port = requireSinkProperty(ctx, FROST_PORT);

    Processor split = ctx.loadProcessor(Fragment.SPLIT_JSON, "split", "staRecordSplit");
    // The JsonRecordSetWriter always writes an array, and a SQL source delivers many records in one
    // FlowFile. The split is what makes one record one atomicity group in the batch.
    setProp(split, "JsonPath Expression", "$[*]");
    ctx.addProcessor(split);
    ctx.addChainConnection(upstreamTail, split);
    ctx.routeFailure(split, errorSink);

    Processor tail = split;
    SinkPreRegionPlan handoff = ctx.spec().sinkPreRegion();
    if (handoff != null) {
      if (!(handoff instanceof FrostPortPlan plan)) {
        throw new FatalAdapterException(
            AdapterErrorCode.NIFI_TEMPLATE_ERROR,
            "FROST sink cannot consume the pre-region plan " + handoff.getClass().getSimpleName());
      }
      tail = buildRender(ctx, plan, split, errorSink);
    } else if (ctx.spec().mappingPresent()) {
      // A compiled mapping without a port plan would deploy a flow that sends the mapped record
      // instead of the port structure — fail the build instead.
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR,
          "a record mapping was compiled for the FROST sink but no port plan was built");
    }

    Processor put = ctx.loadProcessor(Fragment.PUT_FROST_RECORD, null, "frostPut");
    setProp(put, FROST_PORT, port);
    setProp(put, "FROST Base URL", base);
    setProp(put, "FROST Project Id", projectId);
    ctx.setControllerServiceProp(put, "Web Client Service Provider", FROST_WEB_CLIENT);
    String username = ctx.spec().sinkProperties().get(FROST_BASIC_AUTH_USERNAME);
    if (username != null) {
      setProp(put, "Basic Auth Username", username);
    }
    ctx.addProcessor(put);
    ctx.addChainConnection(tail, put);
    for (String relationship : SINK_FAILURE_RELATIONSHIPS) {
      removeAutoTerminated(put, relationship);
      ctx.addConnection(put, errorSink, relationship);
    }
  }

  /**
   * Captures the mapped fields into attributes and renders the port body from them. The processor
   * reads the record as JSON, so the flow hands it the port structure, not the flat record.
   */
  private Processor buildRender(
      BuildContext ctx, FrostPortPlan plan, Processor upstream, Processor errorSink)
      throws FatalAdapterException {
    Processor capture = ctx.loadProcessor(Fragment.EVALUATE_JSON_PATH, "matched", "staCapture");
    for (String key : plan.flatKeys()) {
      setProp(capture, key, "$." + key);
    }
    Processor render = ctx.loadProcessor(Fragment.REPLACE_TEXT, "success", "staPortBody");
    setProp(render, "Replacement Value", plan.body());

    ctx.addProcessor(capture);
    ctx.addProcessor(render);
    ctx.addChainConnection(upstream, capture);
    ctx.addChainConnection(capture, render);
    ctx.routeFailure(capture, errorSink);
    ctx.routeFailure(render, errorSink);
    return render;
  }

  /**
   * A sink property the build cannot work without. The build is reachable without the bind half (a
   * FlowBuildSpec can be composed directly), so its guarantees are re-checked here.
   */
  private static String requireSinkProperty(BuildContext ctx, String key)
      throws FatalAdapterException {
    String value = ctx.spec().sinkProperties().get(key);
    if (value == null || value.isBlank()) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_TEMPLATE_ERROR, "FROST sink requires the '" + key + "' property");
    }
    return value;
  }
}

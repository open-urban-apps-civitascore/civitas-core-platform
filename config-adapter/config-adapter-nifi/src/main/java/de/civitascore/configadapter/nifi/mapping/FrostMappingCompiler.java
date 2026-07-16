/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.nifi.mapping;

import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.FrostEntityPlan.FilterTerm;
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaEntity;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaTarget;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.TargetKind;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Compiles a FROST-targeted {@link MappingConfig} — record-anchored paths of a Thing-shaped target
 * structure — into (a) flat {@code UpdateRecord} properties, one intermediate root-level record
 * field per mapped path, and (b) a {@link FrostEntityPlan}: per-entity lookup filter terms and
 * create-body templates the sink's linear find-or-create chain consumes.
 *
 * <p>Validation is mandatory here, not left to the runtime error sink: the saga/API path bypasses
 * the editor's validation, and a mapping missing a match key would deploy a flow that routes every
 * message to the error sink — an invisible permanent failure instead of a clear plan error. Thing
 * and Datastream follow two rules: the <b>match key</b> paths (from {@code x-core-primaryKey} under
 * the entity's {@code properties}, fallback {@code properties.reference}) must be mapped — the
 * Thing's always, the Datastream's once any Datastream/Observation path is touched — and the fixed
 * <b>create set</b> is all-or-nothing: all present makes the entity creatable (miss → POST), none
 * present leaves it lookup-only (miss → error sink). Locations and Observations are create-only
 * (Thing deep insert / append). Runtime <em>data</em> errors (a malformed date, a wrong type) stay
 * error-sink territory; the deployed chain guards empty match-key values into the error sink before
 * any lookup.
 *
 * <p>Everything emitted is byte-deterministic: template keys follow the catalog order, flat keys
 * and capture properties follow the mapping's insertion order — never map iteration of unspecified
 * order.
 */
public class FrostMappingCompiler {

  /**
   * Flat keys must be valid as RecordPath/Avro field, JsonPath segment, attribute and EL ref. The
   * leaf part exists for readability only — the index prefix guarantees uniqueness — so it is
   * capped to keep the generated attribute names short in the NiFi UI; the exact bound is
   * arbitrary.
   */
  private static final int MAX_LEAF_LENGTH = 40;

  private final RecordPathCompiler recordPathCompiler;

  /**
   * One attribute of an entity's schema-derived {@code properties} bag: a {@link KeyAttribute} (the
   * match key — the one attribute marked {@code x-core-primaryKey}, fallback one named {@code
   * reference}) or a {@link FreeAttribute} (any other modelled attribute). The name of every
   * attribute must pass {@link StaTargetCatalog#isSafeKeyName(String)} (it reaches a template key,
   * and the key also a {@code $filter} URL) and must not be {@code properties} (it would shadow the
   * bag it lives in).
   */
  public sealed interface StaBagAttribute permits KeyAttribute, FreeAttribute {
    String name();

    /** The JSON type the value renders as. */
    StaJsonType type();

    private static void requireValidName(String name) {
      Objects.requireNonNull(name, "name");
      if (!StaTargetCatalog.isSafeKeyName(name)) {
        throw new IllegalArgumentException("unsafe properties attribute name: " + name);
      }
      if ("properties".equals(name)) {
        throw new IllegalArgumentException("a properties attribute must not be named 'properties'");
      }
    }
  }

  /**
   * The match key: a string identifier that renders quoted in the create body and also contributes
   * the entity's {@code $filter} term. Always {@link StaJsonType#STRING} — it is interpolated into
   * a {@code $filter} URL, so a non-string type is not representable.
   */
  public record KeyAttribute(String name) implements StaBagAttribute {
    public KeyAttribute {
      StaBagAttribute.requireValidName(name);
    }

    @Override
    public StaJsonType type() {
      return StaJsonType.STRING;
    }
  }

  /**
   * A free bag attribute rendered into the create body only (never the {@code $filter}): a scalar
   * is {@link StaJsonType#ANY} (its JSON type inferred from the mapped value), an {@code
   * object}/{@code array}/{@code $ref} is {@link StaJsonType#RAW_JSON} (embedded verbatim).
   */
  public record FreeAttribute(String name, StaJsonType type) implements StaBagAttribute {
    public FreeAttribute {
      StaBagAttribute.requireValidName(name);
      Objects.requireNonNull(type, "type");
    }
  }

  /**
   * The schema-derived {@code properties} bag of the mapping's Thing-shaped target structure, per
   * entity: every attribute the bag declares, in declaration order. SensorThings keeps identifiers
   * in {@code properties}, and so may any number of free attributes the tenant models alongside the
   * key. Each entity's bag holds at most one {@link KeyAttribute}.
   *
   * @param thing the Thing's bag attributes (its match key is required for a valid FROST target)
   * @param datastream the Datastream's bag attributes (empty when the structure has no Datastreams
   *     class)
   */
  public record StaProperties(List<StaBagAttribute> thing, List<StaBagAttribute> datastream) {
    public StaProperties {
      thing = List.copyOf(Objects.requireNonNull(thing, "thing"));
      datastream = List.copyOf(Objects.requireNonNull(datastream, "datastream"));
      requireAtMostOneKey(thing, "thing");
      requireAtMostOneKey(datastream, "datastream");
    }

    private static void requireAtMostOneKey(List<StaBagAttribute> bag, String entity) {
      if (bag.stream().filter(attr -> attr instanceof KeyAttribute).count() > 1) {
        throw new IllegalArgumentException("a " + entity + " properties bag has more than one key");
      }
    }

    /** The match-key attribute name of an entity's bag (at most one — the {@link KeyAttribute}). */
    private static List<String> keysOf(List<StaBagAttribute> bag) {
      return bag.stream()
          .filter(attr -> attr instanceof KeyAttribute)
          .map(StaBagAttribute::name)
          .toList();
    }

    public List<String> thingKeys() {
      return keysOf(thing);
    }

    public List<String> datastreamKeys() {
      return keysOf(datastream);
    }

    /** A properties bag whose only attribute per entity is the named match key. */
    public static StaProperties ofKeys(List<String> thingKeys, List<String> datastreamKeys) {
      return new StaProperties(keyAttributes(thingKeys), keyAttributes(datastreamKeys));
    }

    private static List<StaBagAttribute> keyAttributes(List<String> keys) {
      return keys.stream().map(key -> (StaBagAttribute) new KeyAttribute(key)).toList();
    }
  }

  /**
   * The two halves of one compilation: the flat mapping properties (for the UpdateRecord chain) and
   * the entity plan (for the sink's find-or-create chain).
   */
  public record FrostCompilation(List<UpdateRecordProperty> flatProperties, FrostEntityPlan plan) {
    public FrostCompilation {
      flatProperties = List.copyOf(flatProperties);
    }
  }

  public FrostMappingCompiler(RecordPathCompiler recordPathCompiler) {
    this.recordPathCompiler = recordPathCompiler;
  }

  /**
   * Compiles the mapping.
   *
   * @param mapping the parsed mapping with record-anchored target paths
   * @param properties the target structure's per-entity {@code properties} bag (attributes + match
   *     key)
   * @return the flat properties and the entity plan
   * @throws FatalAdapterException if a target path is outside the catalog, a touched entity misses
   *     its match key or maps its create set partially, or a constant is {@code null}
   */
  public FrostCompilation compile(MappingConfig mapping, StaProperties properties)
      throws FatalAdapterException {
    validateKeyNames(properties);
    Map<String, StaTarget> targetsByPath = targetsByPath(properties);
    validate(mapping, properties, targetsByPath);

    Map<String, String> flatKeyByPath = new LinkedHashMap<>();
    int index = 0;
    for (String path : mapping.fields().keySet()) {
      flatKeyByPath.put(path, "sta_" + index + "_" + leafOf(path));
      index++;
    }

    List<UpdateRecordProperty> flatProperties = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      flatProperties.add(
          recordPathCompiler.compileField(
              "/" + flatKeyByPath.get(field.getKey()), field.getValue()));
    }

    boolean thingCreatable = hasCompleteCreateSet(mapping, StaEntity.THING);
    boolean datastreamCreatable = hasCompleteCreateSet(mapping, StaEntity.DATASTREAM);
    boolean observationMapped = touches(mapping, StaEntity.OBSERVATION);

    String thingBody =
        thingCreatable ? renderThingBody(mapping, properties, flatKeyByPath, targetsByPath) : null;
    String datastreamBody =
        datastreamCreatable
            ? renderDatastreamBody(mapping, properties, flatKeyByPath, targetsByPath)
            : null;
    String observationBody =
        observationMapped ? renderObservationBody(mapping, flatKeyByPath, targetsByPath) : null;

    List<FilterTerm> thingFilter =
        filterTerms(properties.thingKeys(), StaEntity.THING, flatKeyByPath);
    List<FilterTerm> datastreamFilter =
        touchesDatastreamTier(mapping)
            ? filterTerms(properties.datastreamKeys(), StaEntity.DATASTREAM, flatKeyByPath)
            : List.of();

    return new FrostCompilation(
        flatProperties,
        new FrostEntityPlan(
            List.copyOf(flatKeyByPath.values()),
            thingFilter,
            thingBody,
            datastreamFilter,
            datastreamBody,
            observationBody));
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  private void validateKeyNames(StaProperties properties) throws FatalAdapterException {
    // Attribute-name shape (safe identifier, not the reserved 'properties') is enforced at the
    // StaBagAttribute boundary; the only domain rule left is that the Thing must declare a key.
    if (properties.thingKeys().isEmpty()) {
      throw reject(
          "the FROST target structure declares no match key on its Thing's properties class; mark"
              + " an attribute under properties with the UML {id} flag or add a 'reference'"
              + " attribute under properties");
    }
  }

  /**
   * The full targetable vocabulary: the fixed catalog paths plus one schema-derived target per
   * {@code properties} bag attribute of the Thing and Datastream ({@code KEY} for the match key,
   * {@code OPTIONAL} for the free attributes, each carrying its modelled JSON type).
   */
  private Map<String, StaTarget> targetsByPath(StaProperties properties) {
    Map<String, StaTarget> byPath = new LinkedHashMap<>();
    for (StaTarget target : bagTargets(StaEntity.THING, properties.thing())) {
      byPath.put(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.THING)) {
      byPath.putIfAbsent(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.LOCATION)) {
      byPath.put(target.path(), target);
    }
    for (StaTarget target : bagTargets(StaEntity.DATASTREAM, properties.datastream())) {
      byPath.put(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.DATASTREAM)) {
      byPath.putIfAbsent(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.OBSERVATION)) {
      byPath.put(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.FEATURE_OF_INTEREST)) {
      byPath.put(target.path(), target);
    }
    return byPath;
  }

  /**
   * One {@code $.…properties.<name>} target per bag attribute — KEY for the match key, else
   * OPTIONAL.
   */
  private List<StaTarget> bagTargets(StaEntity entity, List<StaBagAttribute> bag) {
    return bag.stream()
        .map(
            attr ->
                new StaTarget(
                    StaTargetCatalog.keyPath(entity, attr.name()),
                    entity,
                    attr.type(),
                    attr instanceof KeyAttribute ? TargetKind.KEY : TargetKind.OPTIONAL))
        .toList();
  }

  private void validate(
      MappingConfig mapping, StaProperties properties, Map<String, StaTarget> targetsByPath)
      throws FatalAdapterException {
    if (mapping.fields().isEmpty()) {
      throw reject("a FROST mapping must map at least the Thing's match key");
    }
    validateFieldEntries(mapping, targetsByPath);

    requireKeys(mapping, properties.thingKeys(), StaEntity.THING);
    requireCompleteCreateSet(mapping, StaEntity.THING);
    validateLocation(mapping);
    validateDatastream(mapping, properties);
    validateObservation(mapping);
    validateFeatureOfInterest(mapping);
  }

  private void validateFieldEntries(MappingConfig mapping, Map<String, StaTarget> targetsByPath)
      throws FatalAdapterException {
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      if (!targetsByPath.containsKey(field.getKey())) {
        throw reject(
            "unsupported FROST mapping target path: '"
                + field.getKey()
                + "'; supported paths are: "
                + supportedPaths(targetsByPath));
      }
      if (field.getValue() instanceof ConstNode constant && constant.value() == null) {
        // EvaluateJsonPath cannot distinguish a null constant from a missing value, and the
        // placeholder's null branch already covers "absent" — a null constant is a mis-configured
        // mapping, not a meaningful value.
        throw reject(
            "a FROST mapping does not accept a null constant; omit the target field instead");
      }
    }
  }

  private void validateLocation(MappingConfig mapping) throws FatalAdapterException {
    if (!touches(mapping, StaEntity.LOCATION)) {
      return;
    }
    if (!hasCompleteCreateSet(mapping, StaEntity.THING)) {
      throw reject(
          "mapping a Location requires a creatable Thing (map "
              + createSetOf(StaEntity.THING)
              + ") — Locations are created only via the Thing's deep insert");
    }
    requireCompleteCreateSet(mapping, StaEntity.LOCATION);
  }

  private void validateDatastream(MappingConfig mapping, StaProperties properties)
      throws FatalAdapterException {
    if (!touchesDatastreamTier(mapping)) {
      return;
    }
    if (properties.datastreamKeys().isEmpty()) {
      throw reject(
          "the FROST target structure declares no match key on its Datastream's properties class;"
              + " mark an attribute under properties with the UML {id} flag or add a 'reference'"
              + " attribute under properties");
    }
    requireKeys(mapping, properties.datastreamKeys(), StaEntity.DATASTREAM);
    requireCompleteCreateSet(mapping, StaEntity.DATASTREAM);
  }

  private void validateObservation(MappingConfig mapping) throws FatalAdapterException {
    if (!touches(mapping, StaEntity.OBSERVATION)) {
      return;
    }
    String resultPath = "$.Datastreams[].Observations[].result";
    if (!mapping.fields().containsKey(resultPath)) {
      throw reject("a mapped Observation must map " + resultPath);
    }
  }

  private void validateFeatureOfInterest(MappingConfig mapping) throws FatalAdapterException {
    if (!touches(mapping, StaEntity.FEATURE_OF_INTEREST)) {
      return;
    }
    if (!touches(mapping, StaEntity.OBSERVATION)) {
      throw reject(
          "mapping a FeatureOfInterest requires a mapped Observation — the feature is deep-inserted"
              + " into the observation body");
    }
    requireCompleteCreateSet(mapping, StaEntity.FEATURE_OF_INTEREST);
  }

  /** Every match-key path of the entity must be mapped once the entity is touched at all. */
  private void requireKeys(MappingConfig mapping, List<String> keys, StaEntity entity)
      throws FatalAdapterException {
    String missing =
        keys.stream()
            .map(key -> StaTargetCatalog.keyPath(entity, key))
            .filter(path -> !mapping.fields().containsKey(path))
            .collect(Collectors.joining(", "));
    if (!missing.isEmpty()) {
      throw reject(
          "a FROST mapping must map the "
              + entity.name().toLowerCase(Locale.ROOT)
              + " match key(s): "
              + missing);
    }
  }

  /**
   * The entity's create set is all-or-nothing: all present makes it creatable, none keeps it
   * lookup-only, anything in between is a mis-configured mapping.
   */
  private void requireCompleteCreateSet(MappingConfig mapping, StaEntity entity)
      throws FatalAdapterException {
    List<StaTarget> createSet = createTargets(entity);
    long mapped = createSet.stream().filter(t -> mapping.fields().containsKey(t.path())).count();
    if (mapped != 0 && mapped != createSet.size()) {
      String missing =
          createSet.stream()
              .map(StaTarget::path)
              .filter(path -> !mapping.fields().containsKey(path))
              .collect(Collectors.joining(", "));
      throw reject(
          "a partially mapped "
              + entity.name().toLowerCase(Locale.ROOT)
              + " create set cannot be deployed; also map: "
              + missing
              + " (or map none of the create fields for lookup-only)");
    }
  }

  private boolean hasCompleteCreateSet(MappingConfig mapping, StaEntity entity) {
    List<StaTarget> createSet = createTargets(entity);
    return !createSet.isEmpty()
        && createSet.stream().allMatch(t -> mapping.fields().containsKey(t.path()));
  }

  private List<StaTarget> createTargets(StaEntity entity) {
    return StaTargetCatalog.targetsOf(entity).stream()
        .filter(t -> t.kind() == TargetKind.CREATE)
        .toList();
  }

  private String createSetOf(StaEntity entity) {
    return createTargets(entity).stream().map(StaTarget::path).collect(Collectors.joining(", "));
  }

  private boolean touches(MappingConfig mapping, StaEntity entity) {
    return StaTargetCatalog.targetsOf(entity).stream()
        .anyMatch(t -> mapping.fields().containsKey(t.path()));
  }

  /**
   * Whether the mapping reaches the datastream stage at all: either the datastream itself or an
   * observation is mapped (an observation always implies its datastream must be found first). A
   * FeatureOfInterest never widens this — it may only be mapped alongside an observation.
   */
  private boolean touchesDatastreamTier(MappingConfig mapping) {
    return touches(mapping, StaEntity.DATASTREAM) || touches(mapping, StaEntity.OBSERVATION);
  }

  private String supportedPaths(Map<String, StaTarget> targetsByPath) {
    return String.join(", ", targetsByPath.keySet());
  }

  // ─── Filters ────────────────────────────────────────────────────────────────

  private List<FilterTerm> filterTerms(
      List<String> keys, StaEntity entity, Map<String, String> flatKeyByPath) {
    return keys.stream()
        .map(
            key ->
                new FilterTerm(
                    "properties/" + key, flatKeyByPath.get(StaTargetCatalog.keyPath(entity, key))))
        .toList();
  }

  // ─── Body templates ─────────────────────────────────────────────────────────

  private String renderThingBody(
      MappingConfig mapping,
      StaProperties properties,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(mapping, StaEntity.THING, properties.thing(), flatKeyByPath, targetsByPath);
    if (touches(mapping, StaEntity.LOCATION)) {
      Map<String, Object> location =
          entityTree(mapping, StaEntity.LOCATION, List.of(), flatKeyByPath, targetsByPath);
      tree.put("Locations", List.of(location));
    }
    return renderObject(tree);
  }

  private String renderDatastreamBody(
      MappingConfig mapping,
      StaProperties properties,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(
            mapping, StaEntity.DATASTREAM, properties.datastream(), flatKeyByPath, targetsByPath);
    tree.put("Thing", Map.of("@iot.id", "${" + FrostEntityPlan.THING_ID_ATTRIBUTE + "}"));
    return renderObject(tree);
  }

  private String renderObservationBody(
      MappingConfig mapping,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(mapping, StaEntity.OBSERVATION, List.of(), flatKeyByPath, targetsByPath);
    if (touches(mapping, StaEntity.FEATURE_OF_INTEREST)) {
      tree.put(
          "FeatureOfInterest",
          entityTree(
              mapping, StaEntity.FEATURE_OF_INTEREST, List.of(), flatKeyByPath, targetsByPath));
    }
    tree.put("Datastream", Map.of("@iot.id", "${" + FrostEntityPlan.DS_ID_ATTRIBUTE + "}"));
    return renderObject(tree);
  }

  /**
   * The entity's body tree: fixed fields in catalog order, then the mapped {@code properties} bag
   * attributes (the match key — a created entity must carry it, or the next message could never
   * find it — plus any free attributes the tenant mapped, in declaration order). Leaves are
   * rendered placeholders; inner nodes are the static intermediate objects (unitOfMeasurement,
   * Sensor, …).
   */
  private Map<String, Object> entityTree(
      MappingConfig mapping,
      StaEntity entity,
      List<StaBagAttribute> bag,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree = new LinkedHashMap<>();
    for (StaTarget target : StaTargetCatalog.targetsOf(entity)) {
      if (!mapping.fields().containsKey(target.path())) {
        continue;
      }
      String placeholder =
          placeholder(
              target, flatKeyByPath.get(target.path()), mapping.fields().get(target.path()));
      String[] segments = target.relativePath().split("\\.");
      Map<String, Object> node = tree;
      for (int i = 0; i < segments.length - 1; i++) {
        @SuppressWarnings("unchecked")
        Map<String, Object> child =
            (Map<String, Object>) node.computeIfAbsent(segments[i], k -> new LinkedHashMap<>());
        node = child;
      }
      node.put(segments[segments.length - 1], placeholder);
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    for (StaBagAttribute attr : bag) {
      String path = StaTargetCatalog.keyPath(entity, attr.name());
      if (!mapping.fields().containsKey(path)) {
        continue;
      }
      properties.put(
          attr.name(),
          placeholder(
              targetsByPath.get(path), flatKeyByPath.get(path), mapping.fields().get(path)));
    }
    if (!properties.isEmpty()) {
      tree.put("properties", properties);
    }
    return tree;
  }

  private String renderObject(Map<String, Object> tree) {
    StringBuilder object = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : tree.entrySet()) {
      if (!first) {
        object.append(',');
      }
      first = false;
      object.append('"').append(entry.getKey()).append("\":");
      object.append(renderValue(entry.getValue()));
    }
    return object.append('}').toString();
  }

  private String renderValue(Object value) {
    if (value instanceof Map<?, ?> nested) {
      @SuppressWarnings("unchecked")
      Map<String, Object> nestedTree = (Map<String, Object>) nested;
      return renderObject(nestedTree);
    }
    if (value instanceof List<?> elements) {
      return elements.stream().map(this::renderValue).collect(Collectors.joining(",", "[", "]"));
    }
    return String.valueOf(value);
  }

  /**
   * The EL placeholder for one mapped path. EvaluateJsonPath represents null/missing values as an
   * empty attribute, so non-string and optional placeholders fall back to a JSON {@code null} via
   * {@code isEmpty():ifElse(...)} — an unquoted empty value would be invalid JSON, and {@code null}
   * is the correct semantics (absent data is not an error). Required strings stay plain: an empty
   * string is valid JSON and the mapping's data-quality responsibility; an empty match-key value is
   * caught by the chain's key guard before any lookup. {@code RAW_JSON} embeds the flat value
   * verbatim (a GeoJSON object rendered as a string by the record chain).
   */
  private String placeholder(StaTarget target, String flatKey, ValueNode node) {
    if (target.type() == StaJsonType.RAW_JSON) {
      return "${" + flatKey + "}";
    }
    if (target.type() == StaJsonType.ANY && inferredUnquoted(node)) {
      return "${" + flatKey + ":isEmpty():ifElse('null', ${" + flatKey + "})}";
    }
    if (target.kind() != TargetKind.OPTIONAL) {
      return "\"${" + flatKey + ":escapeJson()}\"";
    }
    return "${"
        + flatKey
        + ":isEmpty():ifElse('null', ${"
        + flatKey
        + ":escapeJson():prepend('\"'):append('\"')})}";
  }

  /**
   * Whether an {@code any}-typed value serializes unquoted (JSON number/boolean): {@code
   * toInt}/{@code toFloat} coerce to a number, a constant carries its literal's JSON type;
   * everything else (copy/concat/toString/format/toDate) renders as a string.
   */
  private boolean inferredUnquoted(ValueNode node) {
    if (node instanceof ConvertNode convert) {
      return switch (convert.op()) {
        case TO_INT, TO_FLOAT -> true;
        case TO_DATE, FORMAT, TO_STRING -> false;
      };
    }
    return node instanceof ConstNode constant
        && (constant.value() instanceof Number || constant.value() instanceof Boolean);
  }

  // ─── Helpers ────────────────────────────────────────────────────────────────

  /**
   * The path's last segment, reduced to {@code [a-z0-9_]} — readability only, the index in front
   * guarantees uniqueness.
   */
  private static String leafOf(String path) {
    String leaf =
        path.substring(path.lastIndexOf('.') + 1)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_]", "");
    return leaf.length() > MAX_LEAF_LENGTH ? leaf.substring(0, MAX_LEAF_LENGTH) : leaf;
  }

  private static FatalAdapterException reject(String detail) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_MAPPING_ERROR, detail);
  }
}

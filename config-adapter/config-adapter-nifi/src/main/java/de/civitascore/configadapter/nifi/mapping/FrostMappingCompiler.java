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
 * and Datastream follow two rules: the <b>match key</b> paths (from {@code x-core-primaryKey},
 * fallback {@code reference}) must be mapped — the Thing's always, the Datastream's once any
 * Datastream/Observation path is touched — and the fixed <b>create set</b> is all-or-nothing: all
 * present makes the entity creatable (miss → POST), none present leaves it lookup-only (miss →
 * error sink). Locations and Observations are create-only (Thing deep insert / append). Runtime
 * <em>data</em> errors (a malformed date, a wrong type) stay error-sink territory; the deployed
 * chain guards empty match-key values into the error sink before any lookup.
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
   * The schema-derived match keys of the mapping's target structure, resolved by the sink spec: the
   * entity classes' {@code x-core-primaryKey} attributes (fallback: a {@code reference} attribute).
   * Key names must pass {@link StaTargetCatalog#isSafeKeyName(String)} — they end up in {@code
   * $filter} expressions and template keys.
   *
   * @param thingKeys the Thing class's key attribute names (never empty for a valid FROST target)
   * @param datastreamKeys the Datastream class's key attribute names (empty when the structure has
   *     no Datastreams class)
   */
  public record StaKeys(List<String> thingKeys, List<String> datastreamKeys) {
    public StaKeys {
      thingKeys = List.copyOf(Objects.requireNonNull(thingKeys, "thingKeys"));
      datastreamKeys = List.copyOf(Objects.requireNonNull(datastreamKeys, "datastreamKeys"));
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
   * @param keys the target structure's match keys
   * @return the flat properties and the entity plan
   * @throws FatalAdapterException if a target path is outside the catalog, a touched entity misses
   *     its match key or maps its create set partially, a key name is unsafe, or a constant is
   *     {@code null}
   */
  public FrostCompilation compile(MappingConfig mapping, StaKeys keys)
      throws FatalAdapterException {
    validateKeyNames(keys);
    Map<String, StaTarget> targetsByPath = targetsByPath(keys);
    validate(mapping, keys, targetsByPath);

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
              "/" + flatKeyByPath.get(field.getKey()), field.getValue(), GeometryEncoding.GEOJSON));
    }

    boolean thingCreatable = hasCompleteCreateSet(mapping, StaEntity.THING);
    boolean datastreamCreatable = hasCompleteCreateSet(mapping, StaEntity.DATASTREAM);
    boolean observationMapped = touches(mapping, StaEntity.OBSERVATION);

    String thingBody =
        thingCreatable ? renderThingBody(mapping, keys, flatKeyByPath, targetsByPath) : null;
    String datastreamBody =
        datastreamCreatable
            ? renderDatastreamBody(mapping, keys, flatKeyByPath, targetsByPath)
            : null;
    String observationBody =
        observationMapped ? renderObservationBody(mapping, flatKeyByPath, targetsByPath) : null;

    List<FilterTerm> thingFilter = filterTerms(keys.thingKeys(), StaEntity.THING, flatKeyByPath);
    boolean datastreamTouched =
        touches(mapping, StaEntity.DATASTREAM) || touches(mapping, StaEntity.OBSERVATION);
    List<FilterTerm> datastreamFilter =
        datastreamTouched
            ? filterTerms(keys.datastreamKeys(), StaEntity.DATASTREAM, flatKeyByPath)
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

  private void validateKeyNames(StaKeys keys) throws FatalAdapterException {
    for (String key : keys.thingKeys()) {
      requireSafeKeyName(key, "Thing");
      requireUnreservedKeyName(key, StaEntity.THING);
    }
    for (String key : keys.datastreamKeys()) {
      requireSafeKeyName(key, "Datastream");
      requireUnreservedKeyName(key, StaEntity.DATASTREAM);
    }
    if (keys.thingKeys().isEmpty()) {
      throw reject(
          "the FROST target structure declares no match key on its Thing class; mark the"
              + " identifying attribute with the UML {id} flag or add a 'reference' attribute");
    }
  }

  private void requireSafeKeyName(String key, String entity) throws FatalAdapterException {
    if (!StaTargetCatalog.isSafeKeyName(key)) {
      // The key name is tenant-modelled and ends up in $filter expressions and template keys —
      // anything outside the identifier whitelist is rejected, never escaped.
      throw reject("the " + entity + " match-key attribute '" + key + "' is not a safe identifier");
    }
  }

  /**
   * A match key named like a fixed catalog field ({@code name}, {@code Sensor}, …) would make one
   * mapping path mean two things — the standard SensorThings field and the {@code properties}-bag
   * key — so it is rejected rather than resolved by precedence.
   */
  private void requireUnreservedKeyName(String key, StaEntity entity) throws FatalAdapterException {
    boolean reserved =
        StaTargetCatalog.byPath(StaTargetCatalog.keyPath(entity, key)).isPresent()
            || StaTargetCatalog.targetsOf(entity).stream()
                .anyMatch(target -> target.relativePath().split("\\.")[0].equals(key));
    if (reserved) {
      throw reject(
          "the match-key attribute '"
              + key
              + "' collides with a standard SensorThings field of "
              + entity.name().toLowerCase(Locale.ROOT)
              + "; rename the identifying attribute");
    }
  }

  /** The full targetable vocabulary: fixed catalog paths plus the schema-derived key paths. */
  private Map<String, StaTarget> targetsByPath(StaKeys keys) {
    Map<String, StaTarget> byPath = new LinkedHashMap<>();
    for (String key : keys.thingKeys()) {
      String path = StaTargetCatalog.keyPath(StaEntity.THING, key);
      byPath.put(path, new StaTarget(path, StaEntity.THING, StaJsonType.STRING, TargetKind.KEY));
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.THING)) {
      byPath.putIfAbsent(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.LOCATION)) {
      byPath.put(target.path(), target);
    }
    for (String key : keys.datastreamKeys()) {
      String path = StaTargetCatalog.keyPath(StaEntity.DATASTREAM, key);
      byPath.put(
          path, new StaTarget(path, StaEntity.DATASTREAM, StaJsonType.STRING, TargetKind.KEY));
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.DATASTREAM)) {
      byPath.putIfAbsent(target.path(), target);
    }
    for (StaTarget target : StaTargetCatalog.targetsOf(StaEntity.OBSERVATION)) {
      byPath.put(target.path(), target);
    }
    return byPath;
  }

  private void validate(MappingConfig mapping, StaKeys keys, Map<String, StaTarget> targetsByPath)
      throws FatalAdapterException {
    if (mapping.fields().isEmpty()) {
      throw reject("a FROST mapping must map at least the Thing's match key");
    }
    validateFieldEntries(mapping, targetsByPath);

    requireKeys(mapping, keys.thingKeys(), StaEntity.THING);
    requireCompleteCreateSet(mapping, StaEntity.THING);
    validateLocation(mapping);
    validateDatastream(mapping, keys);
    validateObservation(mapping);
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

  private void validateDatastream(MappingConfig mapping, StaKeys keys)
      throws FatalAdapterException {
    if (!touches(mapping, StaEntity.DATASTREAM) && !touches(mapping, StaEntity.OBSERVATION)) {
      return;
    }
    if (keys.datastreamKeys().isEmpty()) {
      throw reject(
          "the FROST target structure declares no match key on its Datastream class; mark the"
              + " identifying attribute with the UML {id} flag or add a 'reference' attribute");
    }
    requireKeys(mapping, keys.datastreamKeys(), StaEntity.DATASTREAM);
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
      StaKeys keys,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(mapping, StaEntity.THING, keys.thingKeys(), flatKeyByPath, targetsByPath);
    if (touches(mapping, StaEntity.LOCATION)) {
      Map<String, Object> location =
          entityTree(mapping, StaEntity.LOCATION, List.of(), flatKeyByPath, targetsByPath);
      tree.put("Locations", List.of(location));
    }
    return renderObject(tree);
  }

  private String renderDatastreamBody(
      MappingConfig mapping,
      StaKeys keys,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(
            mapping, StaEntity.DATASTREAM, keys.datastreamKeys(), flatKeyByPath, targetsByPath);
    tree.put("Thing", Map.of("@iot.id", "${" + FrostEntityPlan.THING_ID_ATTRIBUTE + "}"));
    return renderObject(tree);
  }

  private String renderObservationBody(
      MappingConfig mapping,
      Map<String, String> flatKeyByPath,
      Map<String, StaTarget> targetsByPath) {
    Map<String, Object> tree =
        entityTree(mapping, StaEntity.OBSERVATION, List.of(), flatKeyByPath, targetsByPath);
    tree.put("Datastream", Map.of("@iot.id", "${" + FrostEntityPlan.DS_ID_ATTRIBUTE + "}"));
    return renderObject(tree);
  }

  /**
   * The entity's body tree: fixed fields in catalog order, then the match keys under {@code
   * properties} — a created entity must carry its match key, or the next message could never find
   * it. Leaves are rendered placeholders; inner nodes are the static intermediate objects
   * (unitOfMeasurement, Sensor, …).
   */
  private Map<String, Object> entityTree(
      MappingConfig mapping,
      StaEntity entity,
      List<String> keys,
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
    if (!keys.isEmpty()) {
      Map<String, Object> properties = new LinkedHashMap<>();
      for (String key : keys) {
        StaTarget target = targetsByPath.get(StaTargetCatalog.keyPath(entity, key));
        properties.put(
            key,
            placeholder(
                target, flatKeyByPath.get(target.path()), mapping.fields().get(target.path())));
      }
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

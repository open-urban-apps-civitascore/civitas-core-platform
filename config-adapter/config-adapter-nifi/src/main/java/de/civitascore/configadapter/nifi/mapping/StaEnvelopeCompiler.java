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
import de.civitascore.configadapter.nifi.mapping.RecordPathCompiler.UpdateRecordProperty;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaGroup;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaJsonType;
import de.civitascore.configadapter.nifi.mapping.StaTargetCatalog.StaTarget;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles a FROST-targeted {@link MappingConfig} into (a) flat {@code UpdateRecord} properties —
 * one intermediate root-level record field per mapped envelope path — and (b) a {@link
 * FrostEnvelopePlan} whose ReplaceText template rebuilds those flat fields into the SensorThings
 * envelope the find-or-create legs consume.
 *
 * <p>Validation against {@link StaTargetCatalog} is mandatory here, not left to the runtime error
 * sink: the saga/API path bypasses the editor's validation, and a mapping missing a lookup key
 * would deploy a flow that routes every message to the error sink — an invisible permanent failure
 * instead of a clear plan error. Runtime <em>data</em> errors (empty reference value, malformed
 * date) intentionally stay error-sink territory.
 *
 * <p>Everything emitted is byte-deterministic: template keys follow the catalog order, flat keys
 * and capture properties follow the mapping's insertion order — never map iteration of unspecified
 * order.
 */
public class StaEnvelopeCompiler {

  /** Flat keys must be valid as RecordPath/Avro field, JsonPath segment, attribute and EL ref. */
  private static final int MAX_LEAF_LENGTH = 40;

  private final RecordPathCompiler recordPathCompiler;

  /**
   * The two halves of one compilation: the flat mapping properties (for the UpdateRecord chain) and
   * the envelope plan (for the sink's rebuild region).
   */
  public record EnvelopeCompilation(
      List<UpdateRecordProperty> flatProperties, FrostEnvelopePlan plan) {
    public EnvelopeCompilation {
      flatProperties = List.copyOf(flatProperties);
    }
  }

  public StaEnvelopeCompiler(RecordPathCompiler recordPathCompiler) {
    this.recordPathCompiler = recordPathCompiler;
  }

  /**
   * Compiles the mapping.
   *
   * @param mapping the parsed mapping targeting STA envelope paths
   * @return the flat properties and the envelope plan
   * @throws FatalAdapterException if a target path is outside the catalog, a mapped group misses a
   *     required path, no group is mapped at all, or a constant is {@code null}
   */
  public EnvelopeCompilation compile(MappingConfig mapping) throws FatalAdapterException {
    validate(mapping);

    // The shared, ordered flat-key table (targetPath → sta_<index>_<leaf>): the index is the
    // mapping's insertion position, which keeps keys collision-free without any assumption about
    // segment content; used by the flat compilation and the template alike.
    Map<String, String> flatKeyByPath = new LinkedHashMap<>();
    int index = 0;
    for (String path : mapping.fields().keySet()) {
      flatKeyByPath.put(path, "sta_" + index + "_" + leafOf(path));
      index++;
    }

    List<UpdateRecordProperty> flatProperties = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      UpdateRecordProperty property =
          recordPathCompiler.compileField(
              "/" + flatKeyByPath.get(field.getKey()), field.getValue(), GeometryEncoding.GEOJSON);
      // UpdateRecord evaluates NiFi EL in dynamic property values (both strategies), so a tenant
      // const/separator containing ${ENV_VAR} would expand and exfiltrate into the record. $$ is
      // EL's literal escape — same rationale as rejectExpressionLanguage in the SQL path.
      flatProperties.add(
          new UpdateRecordProperty(
              property.recordPath(), property.value().replace("$", "$$"), property.strategy()));
    }

    String template = renderTemplate(mapping, flatKeyByPath);
    return new EnvelopeCompilation(
        flatProperties, new FrostEnvelopePlan(template, List.copyOf(flatKeyByPath.values())));
  }

  // ─── Validation ─────────────────────────────────────────────────────────────

  private void validate(MappingConfig mapping) throws FatalAdapterException {
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      if (StaTargetCatalog.byPath(field.getKey()).isEmpty()) {
        throw reject(
            "unsupported FROST mapping target path: '"
                + field.getKey()
                + "'; supported paths are: "
                + StaTargetCatalog.supportedPaths());
      }
      if (field.getValue() instanceof ConstNode constant && constant.value() == null) {
        // EvaluateJsonPath cannot distinguish a null constant from a missing value, and the
        // placeholder's null branch already covers "absent" — a null constant is a mis-configured
        // mapping, not a meaningful value.
        throw reject(
            "a FROST mapping does not accept a null constant; omit the target field" + " instead");
      }
    }
    // After the whitelist loop every mapped path is a catalog path, so only an empty mapping can
    // leave both groups untouched.
    if (mapping.fields().isEmpty()) {
      throw reject(
          "a FROST mapping must map at least one SensorThings element ($.things[] or"
              + " $.observations[])");
    }
    for (StaGroup group : StaGroup.values()) {
      requireGroupCoverage(mapping, group);
    }
  }

  /**
   * A mapped group must cover all its required paths — most importantly the find-or-create lookup
   * keys, without which every message of a deployed flow would dead-end in the error sink.
   */
  private void requireGroupCoverage(MappingConfig mapping, StaGroup group)
      throws FatalAdapterException {
    List<StaTarget> groupTargets =
        StaTargetCatalog.targets().stream().filter(target -> target.group() == group).toList();
    boolean touched =
        groupTargets.stream().anyMatch(target -> mapping.fields().containsKey(target.path()));
    if (!touched) {
      return;
    }
    String missing =
        groupTargets.stream()
            .filter(StaTarget::required)
            .map(StaTarget::path)
            .filter(path -> !mapping.fields().containsKey(path))
            .collect(Collectors.joining(", "));
    if (!missing.isEmpty()) {
      throw reject(
          "a FROST mapping targeting " + group.pathPrefix() + " must also map: " + missing);
    }
  }

  // ─── Template ───────────────────────────────────────────────────────────────

  /**
   * Renders the envelope skeleton: both top-level keys always present (an unmapped group is an
   * empty array — SplitJson simply yields 0 splits, the legs stay wired unchanged), one element per
   * mapped group, keys in catalog order so the template bytes do not depend on the tenant's mapping
   * order.
   */
  private String renderTemplate(MappingConfig mapping, Map<String, String> flatKeyByPath) {
    StringBuilder template = new StringBuilder("{");
    boolean firstGroup = true;
    for (StaGroup group : StaGroup.values()) {
      if (!firstGroup) {
        template.append(',');
      }
      firstGroup = false;
      template.append('"').append(group.envelopeKey()).append("\":[");
      String element = renderElement(mapping, flatKeyByPath, group);
      if (element != null) {
        template.append(element);
      }
      template.append(']');
    }
    return template.append('}').toString();
  }

  /** The group's single element object, or {@code null} when the group is unmapped. */
  private String renderElement(
      MappingConfig mapping, Map<String, String> flatKeyByPath, StaGroup group) {
    // An ordered tree of the mapped paths: leaves are rendered placeholders, inner nodes are the
    // static intermediate objects (properties/parameters).
    Map<String, Object> tree = new LinkedHashMap<>();
    for (StaTarget target : StaTargetCatalog.targets()) {
      if (target.group() != group || !mapping.fields().containsKey(target.path())) {
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
    return tree.isEmpty() ? null : renderObject(tree);
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
      if (entry.getValue() instanceof Map<?, ?> nested) {
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedTree = (Map<String, Object>) nested;
        object.append(renderObject(nestedTree));
      } else {
        object.append(entry.getValue());
      }
    }
    return object.append('}').toString();
  }

  /**
   * The EL placeholder for one mapped path. EvaluateJsonPath represents null/missing values as an
   * empty attribute, so non-string and optional placeholders fall back to a JSON {@code null} via
   * {@code isEmpty():ifElse(...)} — an unquoted empty value would be invalid JSON, and {@code null}
   * is the correct semantics (absent data is not an error). Required strings stay plain: an empty
   * string is valid JSON and the mapping's data-quality responsibility; an empty lookup reference
   * runs controlled into {@code unmatched} → error sink.
   */
  private String placeholder(StaTarget target, String flatKey, ValueNode node) {
    if (target.type() == StaJsonType.ANY && inferredUnquoted(node)) {
      return "${" + flatKey + ":isEmpty():ifElse('null', ${" + flatKey + "})}";
    }
    if (target.required()) {
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

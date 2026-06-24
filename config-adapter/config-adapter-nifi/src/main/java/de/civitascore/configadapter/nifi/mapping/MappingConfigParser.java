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

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.configadapter.exception.FatalAdapterException;
import de.civitascore.configadapter.model.AdapterErrorCode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConcatNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the inline {@code mappingConfig} of a {@code mapping} graph node into a typed {@link
 * MappingConfig}. The grammar is closed: a field value is either a shorthand source-path string
 * (copy) or an object with a known {@code op}. Anything else — an unknown op, a malformed operand,
 * a bare literal — is rejected with {@link AdapterErrorCode#NIFI_MAPPING_ERROR}, so a tenant can
 * never smuggle an unsupported transform past this boundary.
 */
public class MappingConfigParser {

  /**
   * Parses a {@code mappingConfig} JSON object.
   *
   * @param root the {@code mappingConfig} node
   * @return the parsed mapping
   * @throws FatalAdapterException if the structure is malformed or contains an unsupported
   *     operation
   */
  public MappingConfig parse(JsonNode root) throws FatalAdapterException {
    if (root == null || !root.isObject()) {
      throw reject("mappingConfig must be a JSON object");
    }
    String source = optionalText(root, "source");
    String target = optionalText(root, "target");

    Map<String, ValueNode> fields = new LinkedHashMap<>();
    JsonNode fieldsNode = root.get("fields");
    if (fieldsNode != null && fieldsNode.isObject()) {
      var iterator = fieldsNode.fields();
      while (iterator.hasNext()) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        fields.put(entry.getKey(), parseValue(entry.getValue()));
      }
    }
    return new MappingConfig(source, target, Collections.unmodifiableMap(fields));
  }

  private ValueNode parseValue(JsonNode node) throws FatalAdapterException {
    if (node.isTextual()) {
      return copyOf(node.asText());
    }
    if (!node.isObject()) {
      throw reject(
          "field value must be a source-path string or an op object, got: " + node.getNodeType());
    }

    JsonNode opNode = node.get("op");
    if (opNode == null || !opNode.isTextual()) {
      throw reject("op object is missing a textual 'op' field");
    }
    String op = opNode.asText();

    return switch (op) {
      case "copy" -> parseCopy(node);
      case "const" -> parseConst(node);
      case "concat" -> parseConcat(node);
      case "geoPoint" -> parseGeoPoint(node);
      default -> parseConversionOrReject(op, node);
    };
  }

  private ValueNode parseGeoPoint(JsonNode node) throws FatalAdapterException {
    JsonNode lon = node.get("lon");
    JsonNode lat = node.get("lat");
    if (lon == null || lat == null) {
      throw reject("geoPoint requires a 'lon' and a 'lat'");
    }
    // Each operand is itself a value (a source-path shorthand or a nested op such as toFloat), so
    // recurse — this keeps the grammar closed and lets a coordinate be converted inline. A geometry
    // operand (a Point built from a Point) is nonsensical and would render to malformed WKT, so it
    // is rejected here rather than failing late in PostGIS.
    ValueNode lonNode = requireScalarOperand(parseValue(lon), "geoPoint 'lon'");
    ValueNode latNode = requireScalarOperand(parseValue(lat), "geoPoint 'lat'");
    return new GeoPointNode(lonNode, latNode);
  }

  private ValueNode parseCopy(JsonNode node) throws FatalAdapterException {
    JsonNode sourcePath = node.get("sourcePath");
    if (sourcePath == null || !sourcePath.isTextual()) {
      throw reject("copy requires a textual 'sourcePath'");
    }
    return copyOf(sourcePath.asText());
  }

  private ValueNode parseConst(JsonNode node) throws FatalAdapterException {
    if (!node.has("value")) {
      throw reject("const requires a 'value'");
    }
    return new ConstNode(jsonToValue(node.get("value")), optionalText(node, "valueType"));
  }

  private ValueNode parseConcat(JsonNode node) throws FatalAdapterException {
    JsonNode inputs = node.get("inputs");
    if (inputs == null || !inputs.isArray()) {
      throw reject("concat requires an 'inputs' array");
    }
    List<ValueNode> parsed = new ArrayList<>();
    for (JsonNode input : inputs) {
      parsed.add(requireScalarOperand(parseValue(input), "concat input"));
    }
    return new ConcatNode(optionalText(node, "separator"), List.copyOf(parsed));
  }

  private ValueNode parseConversionOrReject(String op, JsonNode node) throws FatalAdapterException {
    Optional<ConversionOp> conversion = ConversionOp.fromRaw(op);
    if (conversion.isEmpty()) {
      throw reject("unsupported mapping op: " + op);
    }
    JsonNode input = node.get("input");
    if (input == null) {
      throw reject(op + " requires an 'input'");
    }
    String pattern = optionalText(node, "pattern");
    boolean requiresPattern = conversion.get().requiresPattern();
    if (requiresPattern && (pattern == null || pattern.isBlank())) {
      throw reject(op + " requires a non-blank 'pattern'");
    }
    // The converse half of the ValueNode invariant: a non-date op must not carry a pattern. Reject
    // a stray one here (a clean FatalAdapterException) rather than silently ignoring it downstream.
    if (!requiresPattern && pattern != null) {
      throw reject(op + " does not take a 'pattern'");
    }
    return new ConvertNode(
        conversion.get(), requireScalarOperand(parseValue(input), op + " input"), pattern);
  }

  /**
   * Wraps a source path into a {@link CopyNode}, rejecting a blank path. A blank (empty/whitespace)
   * path resolves to the record root {@code /} in {@link JsonPaths}, which silently copies the
   * whole record into the target field — and for a geometry coordinate produces structurally-valid
   * but garbage WKT that only fails far downstream in PostGIS. Rejecting it here turns a late NiFi
   * row failure into a clean deploy-time error.
   */
  private CopyNode copyOf(String sourcePath) throws FatalAdapterException {
    if (sourcePath.isBlank()) {
      throw reject("a source path must be non-blank");
    }
    return new CopyNode(sourcePath);
  }

  /**
   * Rejects a geometry-producing operand ({@code geoPoint}) where only a scalar is meaningful — a
   * coordinate of another {@code geoPoint}, a {@code concat} input, or a conversion input. A nested
   * geometry would render to malformed WKT ({@code concat('POINT(', concat('POINT(', …), …)}); it
   * is knowable at parse time, so it is rejected rather than failing late in PostGIS.
   */
  private ValueNode requireScalarOperand(ValueNode node, String where)
      throws FatalAdapterException {
    if (node instanceof GeoPointNode) {
      throw reject(where + " must be a scalar value, not a geoPoint geometry");
    }
    return node;
  }

  private static Object jsonToValue(JsonNode node) {
    if (node.isTextual()) {
      return node.asText();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isNumber()) {
      return node.numberValue();
    }
    if (node.isNull()) {
      return null;
    }
    return node.toString();
  }

  private static String optionalText(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private static FatalAdapterException reject(String detail) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_MAPPING_ERROR, detail);
  }
}

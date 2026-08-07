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
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConcatNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConstNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.ConvertNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.CopyNode;
import de.civitascore.configadapter.nifi.mapping.ValueNode.GeoPointNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles a {@link MappingConfig} into NiFi {@code UpdateRecord} dynamic properties — using
 * RecordPath only, never Jolt or scripting. Each target field becomes one property whose name is
 * the destination RecordPath and whose value is either a RecordPath value expression or a literal.
 *
 * <p>Numeric and UUID conversions ({@code toInt}/{@code toFloat}/{@code toUuid}) are intentionally
 * transparent here: the value is copied unchanged and the actual coercion happens at the sink —
 * PutDatabaseRecord coerces to the target column types (the PostGIS adapter owns the typed table
 * DDL). This keeps every transform expressible in pure RecordPath, with no schema knowledge in this
 * adapter.
 */
public class RecordPathCompiler {

  /**
   * NiFi's RecordPath {@code toString(subject, charset)} requires the charset argument even though
   * it only affects {@code bytes} subjects; for any other subject the argument is inert. The
   * RecordPath parser rejects a single-argument call at compile time regardless of subject type, so
   * a fixed charset is always passed.
   */
  private static final String TO_STRING_CHARSET = "UTF-8";

  private static final String ISO_DATE_PATTERN = "yyyy-MM-dd";

  /**
   * The NiFi {@code UpdateRecord} "Replacement Value Strategy" for a property. A {@code const} uses
   * {@code literal-value} (a bare RecordPath literal is not evaluated as a value by UpdateRecord);
   * everything else is a {@code record-path-value} expression. A single processor allows only one
   * strategy, so the flow builder emits one {@code UpdateRecord} per strategy when a mapping mixes
   * both.
   */
  public enum ReplacementStrategy {
    RECORD_PATH_VALUE("record-path-value"),
    LITERAL_VALUE("literal-value");

    private final String nifiValue;

    ReplacementStrategy(String nifiValue) {
      this.nifiValue = nifiValue;
    }

    /**
     * Returns the exact strategy string NiFi expects in the processor configuration.
     *
     * @return the NiFi strategy value
     */
    public String nifiValue() {
      return nifiValue;
    }
  }

  /**
   * A single {@code UpdateRecord} dynamic property.
   *
   * @param recordPath the destination RecordPath (the property name)
   * @param value the replacement value (a RecordPath expression or a literal)
   * @param strategy the replacement value strategy
   */
  public record UpdateRecordProperty(
      String recordPath, String value, ReplacementStrategy strategy) {}

  /**
   * Compiles all field rules of a mapping into ordered {@code UpdateRecord} properties.
   *
   * @param mapping the parsed mapping
   * @param geometryEncoding how a geometry op ({@code geoPoint}) must be rendered for the target
   *     sink (WKT for PostGIS, GeoJSON for FROST)
   * @return the properties, one per target field, in mapping order, plus the fan-out they were
   *     compiled against
   * @throws FatalAdapterException if an op cannot be rendered for the requested encoding, or the
   *     mapping reads from independent source arrays
   */
  public CompiledMapping compile(MappingConfig mapping, GeometryEncoding geometryEncoding)
      throws FatalAdapterException {
    ForkPlan fork = ForkPlan.forMapping(mapping);
    List<UpdateRecordProperty> properties = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      JsonPaths.ParsedPath target = parsePath(field.getKey(), "target");
      properties.add(
          compileField(target.recordPath(), field.getValue(), geometryEncoding, target, fork));
    }
    return new CompiledMapping(properties, fork);
  }

  /**
   * Compiles a single field rule against an explicit destination. This flat-compilation entry point
   * redirects each rule into an intermediate root-level field instead of the mapping's own target
   * path.
   */
  UpdateRecordProperty compileField(String destination, ValueNode node, ForkPlan fork)
      throws FatalAdapterException {
    // The flat representation has no target array context, but source paths still pass through the
    // same ambiguity checks.
    return compileField(
        destination, node, GeometryEncoding.GEOJSON, new JsonPaths.ParsedPath(List.of(), -1), fork);
  }

  private UpdateRecordProperty compileField(
      String destination,
      ValueNode node,
      GeometryEncoding geometryEncoding,
      JsonPaths.ParsedPath target,
      ForkPlan fork)
      throws FatalAdapterException {
    if (node instanceof ConstNode constant) {
      // A bare RecordPath literal is not evaluated as a value by UpdateRecord, so a const must use
      // the literal-value strategy. The builder groups properties by strategy into separate
      // UpdateRecord processors.
      return new UpdateRecordProperty(
          destination,
          NifiExpressionLanguage.escape(String.valueOf(constant.value())),
          ReplacementStrategy.LITERAL_VALUE);
    }
    return new UpdateRecordProperty(
        destination,
        NifiExpressionLanguage.escape(render(node, geometryEncoding, target, fork)),
        ReplacementStrategy.RECORD_PATH_VALUE);
  }

  private String render(
      ValueNode node, GeometryEncoding geometryEncoding, JsonPaths.ParsedPath target, ForkPlan fork)
      throws FatalAdapterException {
    return switch (node) {
      case CopyNode copy -> renderCopy(copy, target, fork);
      case ConstNode constant -> literal(constant.value());
      case ConcatNode concat -> renderConcat(concat, geometryEncoding, target, fork);
      case ConvertNode convert -> renderConvert(convert, geometryEncoding, target, fork);
      case GeoPointNode geoPoint -> renderGeoPoint(geoPoint, geometryEncoding, target, fork);
    };
  }

  /**
   * Renders a source path relative to the selected target field when both live below the same
   * innermost array. NiFi evaluates an absolute wildcard source as a multi-value selection; using
   * it as the replacement for every wildcard target can assign the entire selection to each
   * element. A relative path (for example {@code ../sourceName}) keeps evaluation anchored at the
   * current array record and therefore preserves element-wise semantics.
   *
   * <p>When the mapping fans out, this relative form is not needed and not wanted: the upstream
   * {@code ForkRecord} has already flattened one element per record, so every path — element field
   * or ancestor field — is a plain root-level selection.
   */
  private String renderCopy(CopyNode copy, JsonPaths.ParsedPath target, ForkPlan fork)
      throws FatalAdapterException {
    JsonPaths.ParsedPath source = parsePath(copy.sourcePath(), "source");
    if (fork.required()) {
      if (target.hasArrayContext()) {
        // ForkPlan rejects this pairing before compilation: an in-place array target cannot share a
        // mapping with a fan-out. Falling through would emit a relative '../field' against an
        // already-flattened record — wrong output, no error.
        throw reject(
            "target '"
                + target.recordPath()
                + "' keeps an array level while the mapping fans out over '"
                + fork.recordPath()
                + "'");
      }
      return fork.rewriteSource(source);
    }
    if (!source.hasArrayContext()) {
      return source.recordPath(); // root scalar/object; valid as an absolute value or broadcast
    }
    if (!target.hasArrayContext()) {
      throw incompatibleArrayContexts(copy.sourcePath(), target);
    }
    if (!source.arrayContext().equals(target.arrayContext())) {
      throw incompatibleArrayContexts(copy.sourcePath(), target);
    }

    List<String> sourceSuffix = source.suffixWithinArray();
    List<String> targetSuffix = target.suffixWithinArray();
    if (sourceSuffix.isEmpty() || targetSuffix.isEmpty()) {
      throw reject(
          "array element paths must select a field below the array; source="
              + copy.sourcePath()
              + ", target="
              + target.recordPath());
    }

    return "../".repeat(targetSuffix.size())
        + String.join(
            "/", sourceSuffix.stream().map(segment -> segment.replace("[]", "[*]")).toList());
  }

  private FatalAdapterException incompatibleArrayContexts(
      String sourcePath, JsonPaths.ParsedPath target) {
    return reject(
        "cannot map array source '"
            + sourcePath
            + "' to target '"
            + target.recordPath()
            + "': element-wise mapping requires the same array context");
  }

  private String renderConcat(
      ConcatNode concat,
      GeometryEncoding geometryEncoding,
      JsonPaths.ParsedPath target,
      ForkPlan fork)
      throws FatalAdapterException {
    String separator = concat.separator();
    StringBuilder builder = new StringBuilder("concat(");
    List<ValueNode> inputs = concat.inputs();
    for (int i = 0; i < inputs.size(); i++) {
      if (i > 0) {
        builder.append(", ");
        if (separator != null) {
          builder.append(quote(separator)).append(", ");
        }
      }
      builder.append(render(inputs.get(i), geometryEncoding, target, fork));
    }
    return builder.append(')').toString();
  }

  private String renderConvert(
      ConvertNode convert,
      GeometryEncoding geometryEncoding,
      JsonPaths.ParsedPath target,
      ForkPlan fork)
      throws FatalAdapterException {
    String inner = render(convert.input(), geometryEncoding, target, fork);
    return switch (convert.op()) {
      // RecordPath has one parse function and it always yields a time component, so the timestamp
      // op is the bare call and the date-only op is the one that needs extra work.
      case TO_DATE_TIME -> "toDate(" + inner + ", " + quote(convert.pattern()) + ")";
      // That time component reaches a DATE column as epoch millis and is rejected there, while the
      // row vanishes without a deployment error. Re-formatting yields a plain string the server
      // parses into the column's own type (see PostgisSinkStage#withStringtypeUnspecified).
      case TO_DATE ->
          "format(toDate("
              + inner
              + ", "
              + quote(convert.pattern())
              + "), "
              + quote(ISO_DATE_PATTERN)
              + ")";
      case FORMAT -> "format(" + inner + ", " + quote(convert.pattern()) + ")";
      case TO_STRING -> "toString(" + inner + ", " + quote(TO_STRING_CHARSET) + ")";
      // RecordPath's only UUID function, uuid5(), mints a new identifier rather than converting
      // one.
      case TO_INT, TO_FLOAT, TO_UUID -> inner;
    };
  }

  /**
   * Renders a {@code geoPoint} per encoding. {@code WKT} (PostGIS): {@code POINT(lon lat)} via
   * {@code concat}, with no {@code SRID=} prefix — the geometry column stamps its own SRID (a fixed
   * one would clash with a non-4326 column). {@code GEOJSON} (FROST): a GeoJSON Point built as a
   * string via {@code concat} — the FROST entity template embeds it verbatim.
   */
  private String renderGeoPoint(
      GeoPointNode geoPoint,
      GeometryEncoding geometryEncoding,
      JsonPaths.ParsedPath target,
      ForkPlan fork)
      throws FatalAdapterException {
    String lon = render(geoPoint.lon(), geometryEncoding, target, fork);
    String lat = render(geoPoint.lat(), geometryEncoding, target, fork);
    return switch (geometryEncoding) {
      case WKT ->
          "concat("
              + quote("POINT(")
              + ", "
              + lon
              + ", "
              + quote(" ")
              + ", "
              + lat
              + ", "
              + quote(")")
              + ")";
      case GEOJSON ->
          // RecordPath has no object constructor, so the GeoJSON Point is built as a string —
          // the FROST body template embeds the flat field verbatim (RAW_JSON), turning it back
          // into a JSON object.
          "concat("
              + quote("{\"type\":\"Point\",\"coordinates\":[")
              + ", "
              + lon
              + ", "
              + quote(",")
              + ", "
              + lat
              + ", "
              + quote("]}")
              + ")";
    };
  }

  private static String literal(Object value) {
    if (value instanceof String text) {
      return quote(text);
    }
    return String.valueOf(value);
  }

  /**
   * Renders a string as a RecordPath single-quoted literal, escaping backslashes and embedded
   * single quotes so tenant-supplied text (e.g. a constant {@code O'Brien} or a concat separator)
   * can never break out of the literal and corrupt the generated expression.
   *
   * @param text the raw text
   * @return the quoted, escaped RecordPath literal
   */
  private static String quote(String text) {
    return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  private static JsonPaths.ParsedPath parsePath(String path, String role)
      throws FatalAdapterException {
    try {
      return JsonPaths.parse(path);
    } catch (IllegalArgumentException e) {
      throw new FatalAdapterException(
          AdapterErrorCode.NIFI_MAPPING_ERROR,
          e,
          "invalid " + role + " CORE path '" + path + "': " + e.getMessage());
    }
  }

  private static FatalAdapterException reject(String detail) {
    return new FatalAdapterException(AdapterErrorCode.NIFI_MAPPING_ERROR, detail);
  }
}

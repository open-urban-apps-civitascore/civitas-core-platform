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
 * <p>Numeric conversions ({@code toInt}/{@code toFloat}) are intentionally transparent here: the
 * value is copied unchanged and the actual coercion happens at the sink — PutDatabaseRecord coerces
 * to the target column types (the PostGIS adapter owns the typed table DDL). This keeps every
 * transform expressible in pure RecordPath, with no schema knowledge in this adapter.
 */
public class RecordPathCompiler {

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
   * @return the properties, one per target field, in mapping order
   * @throws FatalAdapterException if an op cannot be rendered for the requested encoding
   */
  public List<UpdateRecordProperty> compile(
      MappingConfig mapping, GeometryEncoding geometryEncoding) throws FatalAdapterException {
    List<UpdateRecordProperty> properties = new ArrayList<>();
    for (Map.Entry<String, ValueNode> field : mapping.fields().entrySet()) {
      String destination = JsonPaths.toRecordPath(field.getKey());
      properties.add(compileField(destination, field.getValue(), geometryEncoding));
    }
    return List.copyOf(properties);
  }

  private UpdateRecordProperty compileField(
      String destination, ValueNode node, GeometryEncoding geometryEncoding)
      throws FatalAdapterException {
    if (node instanceof ConstNode constant) {
      // A bare RecordPath literal is not evaluated as a value by UpdateRecord, so a const must use
      // the literal-value strategy. The builder groups properties by strategy into separate
      // UpdateRecord processors.
      return new UpdateRecordProperty(
          destination, String.valueOf(constant.value()), ReplacementStrategy.LITERAL_VALUE);
    }
    return new UpdateRecordProperty(
        destination, render(node, geometryEncoding), ReplacementStrategy.RECORD_PATH_VALUE);
  }

  private String render(ValueNode node, GeometryEncoding geometryEncoding)
      throws FatalAdapterException {
    return switch (node) {
      case CopyNode copy -> JsonPaths.toRecordPath(copy.sourcePath());
      case ConstNode constant -> literal(constant.value());
      case ConcatNode concat -> renderConcat(concat, geometryEncoding);
      case ConvertNode convert -> renderConvert(convert, geometryEncoding);
      case GeoPointNode geoPoint -> renderGeoPoint(geoPoint, geometryEncoding);
    };
  }

  private String renderConcat(ConcatNode concat, GeometryEncoding geometryEncoding)
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
      builder.append(render(inputs.get(i), geometryEncoding));
    }
    return builder.append(')').toString();
  }

  private String renderConvert(ConvertNode convert, GeometryEncoding geometryEncoding)
      throws FatalAdapterException {
    String inner = render(convert.input(), geometryEncoding);
    return switch (convert.op()) {
      case TO_DATE -> "toDate(" + inner + ", " + quote(convert.pattern()) + ")";
      case FORMAT -> "format(" + inner + ", " + quote(convert.pattern()) + ")";
      case TO_STRING -> "toString(" + inner + ")";
      case TO_INT, TO_FLOAT -> inner;
    };
  }

  /**
   * Renders a {@code geoPoint} for the target sink. For a PostGIS sink the value is a WKT literal
   * {@code POINT(<lon> <lat>)} assembled with {@code concat}; the geometry column parses it on
   * insert and stamps its own SRID, so no {@code SRID=} prefix is emitted (a fixed one would clash
   * with a non-4326 column). GeoJSON (FROST) cannot be produced as a RecordPath value — RecordPath
   * has no object constructor and emitting a JSON string would be double-encoded — so it is
   * rejected until a dedicated FROST geometry path exists.
   */
  private String renderGeoPoint(GeoPointNode geoPoint, GeometryEncoding geometryEncoding)
      throws FatalAdapterException {
    String lon = render(geoPoint.lon(), geometryEncoding);
    String lat = render(geoPoint.lat(), geometryEncoding);
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
          throw new FatalAdapterException(
              AdapterErrorCode.NIFI_MAPPING_ERROR,
              "geoPoint is not yet supported for GeoJSON (FROST) sinks");
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
}

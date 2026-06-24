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

import java.util.List;
import java.util.Objects;

/**
 * A single target-field rule in a CORE Mapping. A value is either a copy of a source path, a
 * constant, a concatenation, or a type conversion. The sealed hierarchy lets the RecordPath
 * compiler exhaustively pattern-match over the rule kinds.
 */
public sealed interface ValueNode
    permits ValueNode.CopyNode,
        ValueNode.ConstNode,
        ValueNode.ConcatNode,
        ValueNode.ConvertNode,
        ValueNode.GeoPointNode {

  /**
   * Copies the value at {@code sourcePath} (a source JSONPath) into the target field. This is also
   * the parsed form of the shorthand {@code "$.target": "$.source"}.
   *
   * @param sourcePath the source JSONPath
   */
  record CopyNode(String sourcePath) implements ValueNode {
    public CopyNode {
      Objects.requireNonNull(sourcePath, "sourcePath");
    }
  }

  /**
   * A constant literal value for the target field.
   *
   * @param value the literal value (String, Number, Boolean, or null)
   * @param valueType the optional declared value type (may be null)
   */
  record ConstNode(Object value, String valueType) implements ValueNode {}

  /**
   * Concatenates the rendered inputs, optionally joined by a separator.
   *
   * @param separator the separator inserted between inputs (may be null)
   * @param inputs the ordered inputs
   */
  record ConcatNode(String separator, List<ValueNode> inputs) implements ValueNode {
    public ConcatNode {
      inputs = List.copyOf(inputs);
    }
  }

  /**
   * Applies a type conversion to a nested input value. {@code toDate}/{@code format} require a
   * non-blank pattern, which this constructor enforces. The converse half — a non-date op must not
   * carry a pattern — is enforced upstream by {@code MappingConfigParser}, which rejects a stray
   * pattern (with a user-facing error) before this node is constructed; together they keep an
   * illegal combination from reaching the RecordPath compiler.
   *
   * @param op the conversion operation
   * @param input the value to convert
   * @param pattern the format/parse pattern (required for {@code toDate}/{@code format}, else null)
   */
  record ConvertNode(ConversionOp op, ValueNode input, String pattern) implements ValueNode {
    public ConvertNode {
      Objects.requireNonNull(op, "op");
      Objects.requireNonNull(input, "input");
      if (op.requiresPattern() && (pattern == null || pattern.isBlank())) {
        throw new IllegalArgumentException(op.rawOp() + " requires a non-blank pattern");
      }
    }
  }

  /**
   * Builds a geometry point from two scalar inputs (longitude, latitude). Unlike the other ops this
   * one is compiled <em>sink-dependently</em>: a PostGIS sink needs a WKT value its geometry column
   * can parse, while a FROST sink needs a GeoJSON object — so the rendering is selected by the
   * {@link GeometryEncoding} passed to the RecordPath compiler, not fixed here. The two operands
   * are named ({@code lon}/{@code lat}) rather than positional, so this is its own node kind rather
   * than a {@link ConvertNode} (single input) or {@link ConcatNode} (an ordered list).
   *
   * @param lon the longitude value (X)
   * @param lat the latitude value (Y)
   */
  record GeoPointNode(ValueNode lon, ValueNode lat) implements ValueNode {
    public GeoPointNode {
      Objects.requireNonNull(lon, "lon");
      Objects.requireNonNull(lat, "lat");
    }
  }
}

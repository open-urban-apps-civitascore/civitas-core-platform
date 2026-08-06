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

import java.util.ArrayList;
import java.util.List;

/** Translates the CORE Mapping JSONPath dialect into NiFi RecordPath. */
final class JsonPaths {

  private JsonPaths() {}

  /**
   * Converts a CORE JSONPath such as {@code $.a.b[0].c} into the equivalent RecordPath {@code
   * /a/b[0]/c}. The root {@code $} becomes {@code /}. Concrete array indices are preserved and
   * CORE's empty array selector ({@code []}) becomes NiFi's all-elements selector ({@code [*]}).
   *
   * @param jsonPath the source JSONPath
   * @return the equivalent RecordPath
   */
  static String toRecordPath(String jsonPath) {
    return parse(jsonPath).recordPath();
  }

  /** Parsed CORE path used to reason about an UpdateRecord field's array context. */
  record ParsedPath(List<String> segments, int lastArraySegment) {
    ParsedPath {
      segments = List.copyOf(segments);
    }

    String recordPath() {
      return segments.isEmpty() ? "/" : "/" + String.join("/", recordSegments());
    }

    boolean hasArrayContext() {
      return lastArraySegment >= 0;
    }

    /** Path from the root through the innermost array selector, in CORE notation. */
    List<String> arrayContext() {
      return hasArrayContext() ? segments.subList(0, lastArraySegment + 1) : List.of();
    }

    /** Segments below the innermost array selector. */
    List<String> suffixWithinArray() {
      return hasArrayContext() ? segments.subList(lastArraySegment + 1, segments.size()) : segments;
    }

    private List<String> recordSegments() {
      return segments.stream().map(segment -> segment.replace("[]", "[*]")).toList();
    }
  }

  /**
   * Parses the deliberately small CORE path dialect emitted by the mapping editor. Property names
   * are dot-separated; selectors stay attached to their property segment. Empty segments are
   * rejected so malformed tenant input never becomes a different, apparently valid RecordPath.
   */
  static ParsedPath parse(String jsonPath) {
    if (jsonPath == null || jsonPath.isBlank() || "$".equals(jsonPath)) {
      return new ParsedPath(List.of(), -1);
    }
    String path = jsonPath;
    if (path.startsWith("$")) {
      path = path.substring(1);
    }
    if (path.startsWith(".")) {
      path = path.substring(1);
    }
    if (path.isEmpty()) {
      return new ParsedPath(List.of(), -1);
    }

    String[] rawSegments = path.split("\\.", -1);
    List<String> segments = new ArrayList<>(rawSegments.length);
    int lastArraySegment = -1;
    for (String segment : rawSegments) {
      if (segment.isEmpty()) {
        throw new IllegalArgumentException(
            "CORE path contains an empty property segment: " + jsonPath);
      }
      rejectExpressionLanguage(segment, jsonPath);
      segments.add(segment);
      if (segment.contains("[]")) {
        lastArraySegment = segments.size() - 1;
      }
    }
    return new ParsedPath(segments, lastArraySegment);
  }

  /**
   * Rejects the Expression Language metacharacters in a property segment. A path segment reaches
   * {@code ForkRecord}'s EL-enabled fork property as the property <em>value</em>, where escaping is
   * not an option: {@code $$} is handed to NiFi's RecordPathValidator and can render the processor
   * invalid, which NiFi skips on start — trading a wrong read for a queue that never drains. No
   * legitimate CORE property name contains these, so rejecting is free.
   */
  private static void rejectExpressionLanguage(String segment, String jsonPath) {
    for (char metacharacter : new char[] {'$', '{', '}'}) {
      if (segment.indexOf(metacharacter) >= 0) {
        throw new IllegalArgumentException(
            "CORE path segment '"
                + segment
                + "' contains '"
                + metacharacter
                + "', which NiFi would evaluate as an Expression Language reference: "
                + jsonPath);
      }
    }
  }
}

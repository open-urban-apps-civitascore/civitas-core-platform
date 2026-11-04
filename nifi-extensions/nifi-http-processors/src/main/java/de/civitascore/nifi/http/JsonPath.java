/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.http;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * A JSON path of member names, quoted member names and array indexes only, as in {@code
 * $.data[0].id} or {@code $['next cursor']}. It is the grammar of the portal backend, so the
 * processor evaluates no filter and no script expression. Two paths are equal when their texts are.
 */
final class JsonPath {

  private static final String MEMBER = "\\.([A-Za-z_][A-Za-z0-9_-]*)";
  private static final String QUOTED_MEMBER = "\\['([^'\\\\]+)'\\]";
  private static final String INDEX = "\\[([0-9]+)\\]";

  private static final Pattern SEGMENT =
      Pattern.compile(MEMBER + "|" + QUOTED_MEMBER + "|" + INDEX);
  private static final Pattern GRAMMAR = Pattern.compile("\\$(" + SEGMENT.pattern() + ")+");

  private static final int INT_BITS = 31;

  private final String text;
  private final List<Segment> segments;

  JsonPath(String text) {
    if (!GRAMMAR.matcher(text).matches()) {
      throw new IllegalArgumentException(
          "a JSON path must start with $ and hold only member names and array indexes");
    }
    this.text = text;
    this.segments = SEGMENT.matcher(text).results().map(Segment::fromMatch).toList();
  }

  /** Empty when the text is not of the grammar. */
  static Optional<JsonPath> parse(String text) {
    return GRAMMAR.matcher(text).matches() ? Optional.of(new JsonPath(text)) : Optional.empty();
  }

  static Optional<JsonPath> required(String text, String keyPath, List<String> violations) {
    if (!ApiCallParser.notBlank(text, keyPath, violations)) {
      return Optional.empty();
    }
    Optional<JsonPath> path = parse(text);
    if (path.isEmpty()) {
      violations.add(keyPath + " must start with $ and hold only member names and array indexes");
    }
    return path;
  }

  /** The value at the path, or a {@link MissingNode} when a step finds nothing. */
  JsonNode valueIn(JsonNode root) {
    JsonNode current = root;
    for (Segment segment : segments) {
      current =
          segment.name() != null ? current.path(segment.name()) : current.path(segment.index());
    }
    return current;
  }

  /** Whether the current value of a parser, in the given context, is the value at the path. */
  boolean isAt(JsonStreamContext context) {
    JsonStreamContext current = context;
    for (int i = segments.size() - 1; i >= 0; i--) {
      if (current == null || !segments.get(i).isAt(current)) {
        return false;
      }
      current = current.getParent();
    }
    return current != null && current.inRoot();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof JsonPath path && text.equals(path.text);
  }

  @Override
  public int hashCode() {
    return text.hashCode();
  }

  @Override
  public String toString() {
    return text;
  }

  /**
   * @param name the member name; null for an array index
   * @param index the array index; -1 for a member name, and for an index beyond the {@code int}
   *     range, which no array holds
   */
  private record Segment(String name, int index) {

    static Segment fromMatch(MatchResult match) {
      if (match.group(3) == null) {
        return new Segment(match.group(1) != null ? match.group(1) : match.group(2), -1);
      }
      BigInteger index = new BigInteger(match.group(3));
      return new Segment(null, index.bitLength() > INT_BITS ? -1 : index.intValue());
    }

    boolean isAt(JsonStreamContext context) {
      if (name == null) {
        return context.inArray() && context.getCurrentIndex() == index;
      }
      return context.inObject() && name.equals(context.getCurrentName());
    }
  }
}

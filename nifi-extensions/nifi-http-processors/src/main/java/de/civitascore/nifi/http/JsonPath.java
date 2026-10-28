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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A JSON path of member names, quoted member names and array indexes only, as in {@code
 * $.data[0].id} or {@code $['next cursor']}. It is the grammar of the portal backend, so the
 * processor evaluates no filter and no script expression.
 */
record JsonPath(String text) {

  private static final String MEMBER = "\\.([A-Za-z_][A-Za-z0-9_-]*)";
  private static final String QUOTED_MEMBER = "\\['([^'\\\\]+)'\\]";
  private static final String INDEX = "\\[([0-9]+)\\]";

  private static final Pattern SEGMENT =
      Pattern.compile(MEMBER + "|" + QUOTED_MEMBER + "|" + INDEX);
  private static final Pattern GRAMMAR = Pattern.compile("\\$(" + SEGMENT.pattern() + ")+");

  private static final int INT_BITS = 31;

  JsonPath {
    if (!GRAMMAR.matcher(text).matches()) {
      throw new IllegalArgumentException(
          "a JSON path must start with $ and hold only member names and array indexes");
    }
  }

  /** Empty when the text is not of the grammar. */
  static Optional<JsonPath> parse(String text) {
    return GRAMMAR.matcher(text).matches() ? Optional.of(new JsonPath(text)) : Optional.empty();
  }

  /** The value at the path, or a {@link MissingNode} when a step finds nothing. */
  JsonNode valueIn(JsonNode root) {
    JsonNode current = root;
    Matcher segment = SEGMENT.matcher(text);
    while (segment.find()) {
      String name = segment.group(1) != null ? segment.group(1) : segment.group(2);
      current = name != null ? current.path(name) : current.path(index(segment.group(3)));
    }
    return current;
  }

  /** An index beyond the {@code int} range gives -1, which no array holds. */
  private static int index(String digits) {
    BigInteger index = new BigInteger(digits);
    return index.bitLength() > INT_BITS ? -1 : index.intValue();
  }

  @Override
  public String toString() {
    return text;
  }
}

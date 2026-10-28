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
import de.civitascore.nifi.http.ApiCall.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sends the cursor of each response with the next request, until a response has none. Each response
 * is one record. A cause names a page by its request number, never by its cursor, because the
 * cursor is data of the API host.
 */
record CursorPagination(
    JsonPath cursorPath, String cursorParameter, ParameterLocation cursorIn, Optional<Limit> limit)
    implements Pagination {

  private static final String REQUEST = "request";

  @Override
  public Page first() {
    return page(0, Optional.empty());
  }

  @Override
  public ResponseShape shape() {
    return new ResponseShape.RecordObject(List.of(cursorPath));
  }

  /** The cursor comes from the API, so the request after it must exist. */
  @Override
  public boolean isPastTheEnd(Page page, int status) {
    return false;
  }

  @Override
  public Progress next(Page page, PageContent content) {
    JsonNode value = content.values().get(cursorPath);
    if (value.isMissingNode() || value.isNull() || "".equals(value.textValue())) {
      return new Progress.Accepted(content.records(), Optional.empty());
    }
    if (!value.isTextual() && !value.isNumber()) {
      return new Progress.Rejected(
          "the value at "
              + cursorPath
              + " is a JSON "
              + typeName(value)
              + ", not a string or a number");
    }
    String cursor = value.isNumber() ? content.numberTexts().get(cursorPath) : value.textValue();
    if (cursorIn == ParameterLocation.HEADER && !isHeaderValue(cursor)) {
      return new Progress.Rejected(
          "the value at " + cursorPath + " holds a character that a header value must not hold");
    }
    return new Progress.Accepted(
        content.records(), Optional.of(page(page.index() + 1, Optional.of(cursor))));
  }

  /** The type of a value that is neither a string, a number nor null. */
  private static String typeName(JsonNode value) {
    if (value.isObject()) {
      return "object";
    }
    return value.isArray() ? "array" : "boolean";
  }

  /**
   * Stricter than a configured header value: a cursor is data of the API host, so no control
   * character reaches a header, and the HTTP/1.1 client sends a character outside ASCII as {@code
   * ?}, which gives the API a cursor that it never issued.
   */
  private static boolean isHeaderValue(String cursor) {
    return cursor.chars().allMatch(c -> c >= ' ' && c <= '~');
  }

  private Page page(long index, Optional<String> cursor) {
    List<Parameter> query = new ArrayList<>();
    limit.ifPresent(size -> query.add(size.asParameter()));
    List<Parameter> headers = new ArrayList<>();
    cursor.ifPresent(
        value ->
            (cursorIn == ParameterLocation.HEADER ? headers : query)
                .add(new Parameter(cursorParameter, value)));
    return new Page(index, Optional.of(new Page.Position(REQUEST, index + 1)), query, headers);
  }
}

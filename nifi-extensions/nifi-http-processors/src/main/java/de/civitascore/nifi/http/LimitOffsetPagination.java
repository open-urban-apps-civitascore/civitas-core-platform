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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Sends the offset, and with a limit the limit, as query parameters. With a limit, the offset grows
 * by the limit and a response with fewer records ends the run; a response with more records shows
 * that the API ignores the limit, and the next offset would read some of its records again. Without
 * a limit, the offset grows by the record count and a response without records ends the run. A
 * cause names a page by its offset, so that a Data Steward can send the same request again; without
 * a limit, the offset thus carries the record counts of the earlier responses.
 *
 * @param countPath where a response that is one record holds its record count; empty when each
 *     response is an array of records
 */
record LimitOffsetPagination(
    String offsetParameter, int startOffset, Optional<Limit> limit, Optional<JsonPath> countPath)
    implements Pagination {

  LimitOffsetPagination {
    if (startOffset < 0) {
      throw new IllegalArgumentException("startOffset must be at least 0");
    }
  }

  @Override
  public Page first() {
    return page(0, startOffset);
  }

  @Override
  public ResponseShape shape() {
    return countPath
        .<ResponseShape>map(path -> new ResponseShape.RecordObject(List.of(path)))
        .orElseGet(ResponseShape.RecordArray::new);
  }

  @Override
  public boolean isPastTheEnd(Page page, int status) {
    return Pagination.isPastTheLastPage(page, status);
  }

  @Override
  public Progress next(Page page, URI requestUrl, PageContent content) {
    OptionalLong count = recordCount(content);
    if (count.isEmpty()) {
      return new Progress.Rejected(
          "the value at " + countPath.orElseThrow() + " is not a record count");
    }
    long records = count.getAsLong();
    if (limit.isPresent() && records > limit.get().size()) {
      return new Progress.Rejected("the response holds more records than the limit");
    }
    boolean last = limit.isPresent() ? records < limit.get().size() : records == 0;
    if (last) {
      return new Progress.Accepted(records, Optional.empty());
    }
    long step = limit.isPresent() ? limit.get().size() : records;
    long offset = page.position().orElseThrow().number();
    if (offset > Long.MAX_VALUE - step) {
      return new Progress.Rejected("the next offset would be larger than " + Long.MAX_VALUE);
    }
    return new Progress.Accepted(records, Optional.of(page(page.index() + 1, offset + step)));
  }

  private OptionalLong recordCount(PageContent content) {
    if (countPath.isEmpty()) {
      return OptionalLong.of(content.records());
    }
    JsonNode value = content.values().get(countPath.get());
    if (value.isArray()) {
      return OptionalLong.of(value.size());
    }
    boolean wholeLong =
        value.isNumber() && value.canConvertToExactIntegral() && value.canConvertToLong();
    return wholeLong && value.longValue() >= 0
        ? OptionalLong.of(value.longValue())
        : OptionalLong.empty();
  }

  @Override
  public List<String> names(ParameterLocation location) {
    List<String> names = new ArrayList<>();
    if (location == ParameterLocation.QUERY) {
      names.add(offsetParameter);
      limit.ifPresent(present -> names.add(present.parameter()));
    }
    return names;
  }

  private Page page(long index, long offset) {
    List<Parameter> query = new ArrayList<>();
    limit.ifPresent(size -> query.add(size.asParameter()));
    query.add(new Parameter(offsetParameter, Long.toString(offset)));
    return new Page(index, Optional.of(new Page.Position("offset", offset)), query, List.of());
  }
}

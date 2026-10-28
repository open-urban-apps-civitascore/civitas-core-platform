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

import de.civitascore.nifi.http.ApiCall.Parameter;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Sends the page number and the page size as query parameters. */
record PageNumberPagination(
    String pageParameter,
    String pageSizeParameter,
    int pageSize,
    int startPage,
    OptionalInt endPage)
    implements Pagination {

  PageNumberPagination {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be at least 1");
    }
    if (startPage < 0) {
      throw new IllegalArgumentException("startPage must be at least 0");
    }
    if (endPage.isPresent() && endPage.getAsInt() < startPage) {
      throw new IllegalArgumentException("endPage must be at least startPage");
    }
  }

  @Override
  public Page first() {
    return page(0);
  }

  @Override
  public ResponseShape shape() {
    return new ResponseShape.RecordArray();
  }

  @Override
  public boolean isPastTheEnd(Page page, int status) {
    return Pagination.isPastTheLastPage(page, status);
  }

  @Override
  public Progress next(Page page, PageContent content) {
    boolean endPageDone = endPage.isPresent() && number(page.index()) >= endPage.getAsInt();
    if (endPageDone || content.records() < pageSize) {
      return new Progress.Accepted(content.records(), Optional.empty());
    }
    return new Progress.Accepted(content.records(), Optional.of(page(page.index() + 1)));
  }

  @Override
  public List<String> names(ParameterLocation location) {
    return location == ParameterLocation.QUERY
        ? List.of(pageParameter, pageSizeParameter)
        : List.of();
  }

  private long number(long index) {
    return startPage + index;
  }

  private Page page(long index) {
    long number = number(index);
    return new Page(
        index,
        Optional.of(new Page.Position("page", number)),
        List.of(
            new Parameter(pageParameter, Long.toString(number)),
            new Parameter(pageSizeParameter, Integer.toString(pageSize))),
        List.of());
  }
}

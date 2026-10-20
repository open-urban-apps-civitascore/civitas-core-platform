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

/**
 * Sends the page number and the page size as query parameters.
 *
 * @param pageSize at least 1
 * @param startPage at least 0
 * @param endPage at least {@code startPage}, empty for no end page
 */
record PageNumberPagination(
    String pageParameter,
    String pageSizeParameter,
    int pageSize,
    int startPage,
    OptionalInt endPage)
    implements Pagination {

  @Override
  public Page first() {
    return page(0);
  }

  @Override
  public boolean pagesAreArrays() {
    return true;
  }

  @Override
  public Optional<Page> next(Page page, int records) {
    boolean endPageDone = endPage.isPresent() && number(page.index()) >= endPage.getAsInt();
    if (endPageDone || records < pageSize) {
      return Optional.empty();
    }
    return Optional.of(page(page.index() + 1));
  }

  private long number(long index) {
    return startPage + index;
  }

  private Page page(long index) {
    long number = number(index);
    return new Page(
        index,
        Optional.of(Long.toString(number)),
        List.of(
            new Parameter(pageParameter, Long.toString(number)),
            new Parameter(pageSizeParameter, Integer.toString(pageSize))));
  }
}

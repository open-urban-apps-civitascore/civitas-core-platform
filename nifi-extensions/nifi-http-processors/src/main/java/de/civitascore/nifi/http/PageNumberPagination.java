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
import java.util.OptionalLong;

/**
 * Sends the page number and the page size as query parameters. The page number starts at {@code
 * startPage} and increases by 1. The run ends after the end page, after a page without records, and
 * after a page with fewer records than the page size.
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
    return page(startPage);
  }

  @Override
  public Optional<Page> next(Page page, int records) {
    long number = page.number().orElseThrow();
    boolean endPageDone = endPage.isPresent() && number >= endPage.getAsInt();
    if (endPageDone || records < pageSize) {
      return Optional.empty();
    }
    return Optional.of(page(number + 1));
  }

  private Page page(long number) {
    return new Page(
        OptionalLong.of(number),
        List.of(
            new Parameter(pageParameter, Long.toString(number)),
            new Parameter(pageSizeParameter, Integer.toString(pageSize))));
  }
}

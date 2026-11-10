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

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** How the requests of one run of an API call follow each other. */
sealed interface Pagination
    permits NextLinkPagination,
        CursorPagination,
        LimitOffsetPagination,
        PageNumberPagination,
        SinglePage {

  /** The page that each run starts with. */
  Page first();

  /**
   * What each 2xx body of the run must be without a path to entries, and the JSON paths that {@link
   * #next} reads. {@link ApiCall#shape} gives the shape of a run.
   */
  ResponseShape shape();

  /**
   * Whether an error status of {@code page} says that the page lies past the last page, so that the
   * run ends with the pages before it.
   */
  boolean isPastTheEnd(Page page, int status);

  /**
   * What the pagination makes of {@code page}, whose records agree with the Record Schema.
   *
   * @param requestUrl the URL that the request of {@code page} went to, without authentication
   *     query parameters
   */
  Progress next(Page page, URI requestUrl, PageContent content);

  /** The names that the pages send at {@code location}. */
  List<String> names(ParameterLocation location);

  /**
   * Some APIs answer a page past the last page with 400 or 404 instead of an empty page; on the
   * first page the same status is a configuration error.
   */
  static boolean isPastTheLastPage(Page page, int status) {
    return page.index() > 0 && (status == 400 || status == 404);
  }

  /** What a page means for its run. */
  sealed interface Progress {

    /**
     * @param records the record count of the page; a page without records gives no FlowFile, except
     *     in an API call without pagination
     * @param next empty when the run ends with the page
     */
    record Accepted(long records, Optional<Page> next) implements Progress {}

    /**
     * @param reason names the JSON path, never a value of the response
     */
    record Rejected(String reason) implements Progress {}
  }
}

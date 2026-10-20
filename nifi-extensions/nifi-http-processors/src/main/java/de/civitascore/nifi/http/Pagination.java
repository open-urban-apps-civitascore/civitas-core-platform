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

import java.util.Optional;

/** How the requests of one run of an API call follow each other. */
sealed interface Pagination permits PageNumberPagination, SinglePage {

  /** The page that each run starts with. */
  Page first();

  /**
   * Whether each page must be a JSON array, and a page without records ends the run without a
   * FlowFile. Otherwise a JSON object is one record, and {@code []} gives a FlowFile.
   */
  boolean pagesAreArrays();

  /**
   * The page after {@code page}, or empty when the run ends with {@code page}.
   *
   * @param records the number of records of {@code page}
   */
  Optional<Page> next(Page page, int records);
}

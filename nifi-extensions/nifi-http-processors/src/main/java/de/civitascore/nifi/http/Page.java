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

/**
 * One request of a run of an API call.
 *
 * @param index the position in the run, 0 for the first page
 * @param name what causes and {@code http.page} show for the page; empty when the run has no named
 *     pages
 * @param queryParameters what the page adds after the query parameters of the API call
 */
record Page(long index, Optional<String> name, List<Parameter> queryParameters) {

  Page {
    queryParameters = List.copyOf(queryParameters);
  }
}

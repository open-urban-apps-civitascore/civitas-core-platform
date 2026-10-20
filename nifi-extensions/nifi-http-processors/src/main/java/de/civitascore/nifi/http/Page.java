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
import java.util.OptionalLong;

/**
 * One request of a run of an API call.
 *
 * @param number the page number of a paginated run, empty for an API call without pagination
 * @param queryParameters what the page adds after the query parameters of the API call
 */
record Page(OptionalLong number, List<Parameter> queryParameters) {

  Page {
    queryParameters = List.copyOf(queryParameters);
  }
}

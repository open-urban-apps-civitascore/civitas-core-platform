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

import java.util.List;

/**
 * One API call of an HTTP/REST Data source, read from the {@code API Call} property and checked.
 *
 * @param queryParameters in the order the request sends them, repeated names included
 * @param headers in the order the request sends them
 */
record ApiCall(
    String name,
    HttpMethod method,
    String path,
    List<Parameter> queryParameters,
    List<Parameter> headers) {

  ApiCall {
    queryParameters = List.copyOf(queryParameters);
    headers = List.copyOf(headers);
  }

  /** A query parameter or a header. The value is never null. */
  record Parameter(String name, String value) {}
}

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
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * Sends the token value under its name, as a header or as a query parameter. A class, not a record,
 * so that no generated {@code toString} names the value.
 */
final class ApiTokenAuthentication implements Authentication {

  private final String name;
  private final String value;
  private final ParameterLocation location;

  ApiTokenAuthentication(String name, String value, ParameterLocation location) {
    this.name = name;
    this.value = value;
    this.location = location;
  }

  @Override
  public String method() {
    return "API Token";
  }

  @Override
  public List<Parameter> queryParameters() {
    return location == ParameterLocation.QUERY ? List.of(new Parameter(name, value)) : List.of();
  }

  @Override
  public HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request) {
    return location == ParameterLocation.HEADER ? request.header(name, value) : request;
  }
}

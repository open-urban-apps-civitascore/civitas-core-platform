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

import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * Sends the token as {@code Authorization: Bearer}. A class, not a record, so that no generated
 * {@code toString} names the token.
 */
final class BearerTokenAuthentication implements Authentication {

  private final String token;

  BearerTokenAuthentication(String token) {
    this.token = token;
  }

  @Override
  public String method() {
    return "Bearer Token";
  }

  @Override
  public HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request) {
    return request.header("Authorization", "Bearer " + token);
  }
}

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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * Sends the credentials as {@code Authorization: Basic}. A class, not a record, so that no
 * generated {@code toString} names the password.
 */
final class BasicAuthentication implements Authentication {

  private final String username;
  private final String password;

  BasicAuthentication(String username, String password) {
    this.username = username;
    this.password = password;
  }

  @Override
  public String method() {
    return "Basic Auth";
  }

  @Override
  public HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request) {
    String credential = username + ":" + password;
    return request.header(
        "Authorization",
        "Basic " + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
  }
}

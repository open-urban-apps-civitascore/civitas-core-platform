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
import org.apache.nifi.web.client.api.HttpRequestHeadersSpec;

/**
 * The authentication method of a Data source. {@link ApiCallClient} applies it to each request of a
 * run. One instance lives from the start of the processor to its stop, and runs one at a time.
 */
interface Authentication {

  /** The name of the method in a cause, for example {@code Bearer Token}. */
  String method();

  /** Called once at the start of each run, before its first request. */
  default void startRun() {}

  /**
   * Gets what the next request needs, for example an access token.
   *
   * @return empty when the request can go, else the failure that ends the run
   */
  default Optional<ApiCallOutcome.Failure> prepare(ApiCall call) {
    return Optional.empty();
  }

  /**
   * Sent after the query parameters of the API call and of the page, percent-encoded as they are.
   */
  default List<Parameter> queryParameters() {
    return List.of();
  }

  /** Adds the credential headers after the headers of the API call. */
  HttpRequestHeadersSpec headers(HttpRequestHeadersSpec request);

  /**
   * Called after a 401. Drops a kept credential.
   *
   * @return whether one more attempt with a new credential is worthwhile
   */
  default boolean renewAfterUnauthorized() {
    return false;
  }

  /**
   * The failure of a request that got no response, when this method makes it fatal.
   *
   * @return empty for the transient failure of any other transport error
   */
  default Optional<ApiCallOutcome.Failure> transportFailure(String request, Throwable cause) {
    return Optional.empty();
  }
}

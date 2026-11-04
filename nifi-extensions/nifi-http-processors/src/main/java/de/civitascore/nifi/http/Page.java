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
import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * One request of a run of an API call.
 *
 * @param index the position in the run, 0 for the first page
 * @param position how causes and {@code http.page} name the page; empty when the run has one page
 * @param queryParameters what the page adds after the query parameters of the API call
 * @param headers what the page adds after the headers of the API call
 * @param url the URL of this request, or empty to use the API call URL and query parameters
 */
record Page(
    long index,
    Optional<Position> position,
    List<Parameter> queryParameters,
    List<Parameter> headers,
    Optional<URI> url) {

  Page {
    queryParameters = List.copyOf(queryParameters);
    headers = List.copyOf(headers);
  }

  Page(
      long index,
      Optional<Position> position,
      List<Parameter> queryParameters,
      List<Parameter> headers) {
    this(index, position, queryParameters, headers, Optional.empty());
  }

  Optional<String> title() {
    return position.map(Position::title);
  }

  /**
   * @param kind for example {@code page}, {@code request} or {@code offset}
   * @param number the page number, the position of the request in the run from 1, or the offset
   */
  record Position(String kind, long number) {

    String title() {
      return kind + " " + number;
    }
  }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The names that a pagination sends, which must differ from each other and from the names of the
 * API call, because a request must never send one name with two values. HTTP query parameter names
 * are case-sensitive and header names are not, so the comparison follows where a name goes.
 */
final class PaginationNames {

  private final List<SentName> callNames = new ArrayList<>();
  private final List<SentName> sent = new ArrayList<>();

  PaginationNames(List<Parameter> queryParameters, List<Parameter> headers) {
    addCallNames(queryParameters, ParameterLocation.QUERY);
    addCallNames(headers, ParameterLocation.HEADER);
  }

  /**
   * @param keyPath the key path of the name, which each violation starts with
   */
  void add(String keyPath, String name, ParameterLocation location) {
    sent.add(new SentName(keyPath, name, location));
  }

  /** The names of {@code call} and the names that its pagination sends. */
  static PaginationNames sentBy(ApiCall call) {
    PaginationNames names = new PaginationNames(call.queryParameters(), call.headers());
    for (ParameterLocation location : ParameterLocation.values()) {
      for (String name : call.pagination().names(location)) {
        names.add("", name, location);
      }
    }
    return names;
  }

  /** Whether a request already sends {@code name} at {@code location}. */
  boolean sends(String name, ParameterLocation location) {
    SentName candidate = new SentName("", name, location);
    return Stream.concat(callNames.stream(), sent.stream()).anyMatch(candidate::collidesWith);
  }

  /** Adds one violation for each name that collides. */
  void check(List<String> violations) {
    for (int i = 0; i < sent.size(); i++) {
      SentName name = sent.get(i);
      if (callNames.stream().anyMatch(name::collidesWith)) {
        violations.add(name.keyPath() + " must differ from the name of each " + name.kind());
      }
      for (SentName earlier : sent.subList(0, i)) {
        if (name.collidesWith(earlier)) {
          violations.add(name.keyPath() + " must differ from " + earlier.keyPath());
        }
      }
    }
  }

  private void addCallNames(List<Parameter> parameters, ParameterLocation location) {
    for (Parameter parameter : parameters) {
      if (parameter.name() != null) {
        callNames.add(new SentName("", parameter.name(), location));
      }
    }
  }

  private record SentName(String keyPath, String name, ParameterLocation location) {

    boolean collidesWith(SentName other) {
      return location == other.location
          && (location == ParameterLocation.HEADER
              ? name.equalsIgnoreCase(other.name)
              : name.equals(other.name));
    }

    String kind() {
      return location == ParameterLocation.HEADER ? "header" : "query parameter";
    }
  }
}

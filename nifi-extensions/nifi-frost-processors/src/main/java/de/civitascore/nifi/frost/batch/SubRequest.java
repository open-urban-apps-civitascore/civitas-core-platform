/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.nifi.frost.batch;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * One request inside a batch document.
 *
 * @param id the record-scoped identifier; a lookup and the create it guards share one
 * @param group the atomicity group, which is the record
 * @param role what the response division does with the answer
 * @param entity the SensorThings entity this request acts on, reported on a failure
 * @param method the HTTP method
 * @param url the service-relative URL, already encoded
 * @param condition the {@code if} expression, or null when the request always runs
 * @param body the request body, or null for a GET
 */
public record SubRequest(
    String id,
    String group,
    SubRequestRole role,
    String entity,
    BatchMethod method,
    String url,
    String condition,
    JsonNode body) {

  public SubRequest {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(group, "group");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(method, "method");
    Objects.requireNonNull(url, "url");
    if ((method == BatchMethod.GET) == (body != null)) {
      // A GET with a body, or a write without one, is a builder defect. FROST answers both with a
      // status that says nothing about the cause.
      throw new IllegalArgumentException("method " + method + " does not match the body given");
    }
  }

  /** The {@code if} expression that runs a request when the named request resolved. */
  public static String ifResolved(String id) {
    return "$" + id;
  }

  /** The {@code if} expression that runs a request when the named request did not resolve. */
  public static String ifMissing(String id) {
    return "not $" + id;
  }

  /** A back-reference usable where the identifier of an entity is required. */
  public static String reference(String id) {
    return "$" + id;
  }
}

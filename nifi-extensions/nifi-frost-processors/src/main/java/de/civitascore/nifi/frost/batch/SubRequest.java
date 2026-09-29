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
 * @param missReason what a parent lookup reports when it finds nothing, or null for the generic "no
 *     entity matches the reference"; ignored for every other role
 */
public record SubRequest(
    String id,
    String group,
    SubRequestRole role,
    String entity,
    BatchMethod method,
    String url,
    String condition,
    JsonNode body,
    String missReason) {

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

  /**
   * A back-reference to the entity a request resolved.
   *
   * <p>It reaches FROST in one of two places. In a URL it must stand at the <b>start</b>: FROST
   * anchors the pattern there and replaces the reference with the self link of the entity, so
   * {@code $r0-thing} becomes {@code /Things(5)} and {@code $r0-ds/Sensor} becomes {@code
   * /Datastreams(7)/Sensor}. Written anywhere else in a URL — {@code Things($r0-thing)} — it stays
   * as it is and FROST looks for an entity whose identifier is the text of the reference. In a body
   * it is a JSON string of its own, {@code "$r0-thing"}, which FROST replaces with the identifier
   * value.
   */
  public static String reference(String id) {
    return "$" + id;
  }
}

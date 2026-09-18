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

/**
 * One answer inside a batch response.
 *
 * @param id the identifier of the sub-request FROST answers; it is not unique in a document
 * @param status the HTTP status of that sub-request
 * @param body the answer body, or null when the sub-request answered without one
 */
public record SubResponse(String id, int status, JsonNode body) {

  /** Whether the status says the sub-request did what it was asked to do. */
  public boolean successful() {
    return status >= 200 && status < 300;
  }

  /**
   * Whether the answer to a lookup names an entity. A collection lookup answers {@code
   * {"value":[…]}} and a single-valued navigation lookup answers the entity itself, so both shapes
   * are read here rather than at each call site.
   */
  public boolean resolvedEntity() {
    if (!successful() || body == null) {
      return false;
    }
    JsonNode collection = body.get("value");
    if (collection != null) {
      return collection.isArray() && !collection.isEmpty();
    }
    return body.hasNonNull("@iot.id");
  }
}

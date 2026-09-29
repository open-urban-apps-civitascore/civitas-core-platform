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
import java.util.ArrayList;
import java.util.List;

/** The answers to one batch document, in the order FROST returns them. */
public record BatchResponse(List<SubResponse> responses) {

  public BatchResponse {
    responses = List.copyOf(responses);
  }

  /**
   * Reads a batch response body.
   *
   * <p>An answer without an identifier is kept. FROST omits the identifier for the sub-requests it
   * skips after a failure inside an atomicity group, and for the one answer it writes when it
   * cannot read the document at all. Rejecting the whole document over those would throw away every
   * answer that does carry its reason.
   *
   * @throws IllegalArgumentException when the body is not a batch response. A FROST server without
   *     the JSON batch extension answers the same URL with an entity document, and reading that as
   *     an empty response list would report every record as written.
   */
  public static BatchResponse of(JsonNode body) {
    JsonNode answers = body == null ? null : body.get("responses");
    if (answers == null || !answers.isArray()) {
      throw new IllegalArgumentException("the FROST answer carries no 'responses' array");
    }
    List<SubResponse> responses = new ArrayList<>(answers.size());
    for (JsonNode answer : answers) {
      JsonNode status = answer.get("status");
      if (status == null || !status.isNumber()) {
        throw new IllegalArgumentException("a batch answer carries no status");
      }
      JsonNode id = answer.get("id");
      String identifier = id == null || id.isNull() ? null : id.asText();
      responses.add(new SubResponse(identifier, status.asInt(), answer.get("body")));
    }
    return new BatchResponse(responses);
  }
}

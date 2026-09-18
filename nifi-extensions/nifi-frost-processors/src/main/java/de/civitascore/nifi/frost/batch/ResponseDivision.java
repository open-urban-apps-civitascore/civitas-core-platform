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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Divides a batch response over the records that produced it.
 *
 * <p>The assignment runs on the sub-request identifier and on document order together. An
 * identifier is not unique — the lookup and the create it guards share one — and a request whose
 * {@code if} did not hold produces no answer at all. So the answers are consumed in order and an
 * answer whose identifier does not match the request in hand means the requests before it did not
 * run.
 */
public final class ResponseDivision {

  private ResponseDivision() {}

  /** The outcome of every record of the document, keyed by record index. */
  public static Map<Integer, RecordOutcome> divide(BatchDocument document, BatchResponse response) {
    // The answers are consumed as they are bound, so the plans must be walked in document order.
    List<SubResponse> remaining = new ArrayList<>(response.responses());
    Map<Integer, RecordOutcome> outcomes = new LinkedHashMap<>();
    for (RecordPlan plan : document.plans()) {
      outcomes.put(plan.recordIndex(), outcomeOf(plan, remaining));
    }
    return outcomes;
  }

  private static RecordOutcome outcomeOf(RecordPlan plan, List<SubResponse> remaining) {
    Set<String> written = new HashSet<>();
    for (Answered pair : align(plan, remaining)) {
      RecordOutcome failure = failureOf(plan.recordIndex(), pair);
      if (failure != null) {
        return failure;
      }
      if (pair.request().role() == SubRequestRole.WRITE) {
        written.add(pair.request().entity());
      }
    }
    return unwritten(plan, written);
  }

  /** The outcome the answer forces, or null when the record may continue. */
  private static RecordOutcome failureOf(int recordIndex, Answered pair) {
    SubRequest request = pair.request();
    SubResponse answer = pair.answer();
    if (request.role() == SubRequestRole.PARENT_LOOKUP) {
      if (answer.resolvedEntity()) {
        return null;
      }
      // The port creates only its own entity. A reference that names no entity is a defect of the
      // data, and creating the parent here would attach the record to an entity the modeller never
      // described.
      return RecordOutcome.failed(
          recordIndex,
          request.entity(),
          answer.successful() ? 404 : answer.status(),
          "no " + request.entity() + " matches the reference of the record");
    }
    if (answer.successful()) {
      return null;
    }
    // A miss on the record's own reference is the usual condition of an upsert and leaves a
    // successful status; only a lookup or a write that broke arrives here.
    return RecordOutcome.failed(recordIndex, request.entity(), answer.status(), reasonOf(answer));
  }

  /** The outcome when an entity of the record saw no write at all. */
  private static RecordOutcome unwritten(RecordPlan plan, Set<String> written) {
    for (String entity : plan.entitiesRequiringWrite()) {
      if (!written.contains(entity)) {
        // Both halves of the upsert were skipped, or the answer never arrived. Reporting the record
        // as written here would lose it without a trace.
        return RecordOutcome.failed(
            plan.recordIndex(), entity, 0, "no write ran for the " + entity + " of the record");
      }
    }
    return RecordOutcome.written(plan.recordIndex());
  }

  /**
   * Binds the answers of one record to its requests. Consumes the answers it binds, so a later
   * record does not see them.
   */
  private static List<Answered> align(RecordPlan plan, List<SubResponse> remaining) {
    List<Answered> answered = new ArrayList<>();
    for (SubRequest request : plan.requests()) {
      int index = indexOfNext(remaining, request.id());
      if (index < 0) {
        continue;
      }
      answered.add(new Answered(request, remaining.remove(index)));
    }
    return answered;
  }

  /**
   * The position of the next answer carrying the identifier, searched from the front. Answers of
   * earlier records are consumed already, and a request that did not run leaves none, so the first
   * match belongs to this request.
   */
  private static int indexOfNext(List<SubResponse> remaining, String id) {
    for (int index = 0; index < remaining.size(); index++) {
      if (remaining.get(index).id().equals(id)) {
        return index;
      }
    }
    return -1;
  }

  /** The reason FROST gave for a failing sub-request, in the shortest readable form. */
  private static String reasonOf(SubResponse answer) {
    JsonNode body = answer.body();
    if (body == null) {
      return "FROST answered " + answer.status() + " without a body";
    }
    if (body.isTextual()) {
      return body.asText();
    }
    JsonNode message = body.get("message");
    if (message != null && message.isTextual()) {
      return message.asText();
    }
    return body.toString();
  }

  /** A request and the answer bound to it. */
  private record Answered(SubRequest request, SubResponse answer) {}
}

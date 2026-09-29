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
 * Divides a batch response over the records that produced it, and decides for each record whether
 * it was written, may be retried, or failed for good.
 *
 * <p>The assignment runs on the sub-request identifier and on document order together. An
 * identifier is not unique — the lookup and the create it guards share one — so the answers are
 * consumed in order and an answer whose identifier does not match the request in hand means the
 * requests before it did not run.
 *
 * <p>Not every answer names a request. FROST leaves the identifier out for the requests it skips
 * after a failure inside an atomicity group, and for the one answer it writes when it cannot read
 * the document. Those answers bind to nothing; they are held back and carry the reason for a record
 * that saw no write of its own.
 */
public final class ResponseDivision {

  private ResponseDivision() {}

  /** The outcome of every record of the document, keyed by record index. */
  public static Map<Integer, RecordOutcome> divide(BatchDocument document, BatchResponse response) {
    // The answers are consumed as they are bound, so the plans must be walked in document order.
    List<SubResponse> remaining = new ArrayList<>();
    List<SubResponse> unnamed = new ArrayList<>();
    for (SubResponse answer : response.responses()) {
      (answer.identified() ? remaining : unnamed).add(answer);
    }
    Map<Integer, RecordOutcome> outcomes = new LinkedHashMap<>();
    for (RecordPlan plan : document.plans()) {
      outcomes.put(plan.recordIndex(), outcomeOf(plan, remaining, unnamed));
    }
    return outcomes;
  }

  private static RecordOutcome outcomeOf(
      RecordPlan plan, List<SubResponse> remaining, List<SubResponse> unnamed) {
    Set<String> written = new HashSet<>();
    for (Answered pair : align(plan, remaining)) {
      RecordOutcome failure = failureOf(plan.recordIndex(), pair);
      if (failure != null) {
        return failure;
      }
      if (pair.request().role() == SubRequestRole.WRITE && !pair.answer().skippedByCondition()) {
        written.add(pair.request().entity());
      }
    }
    return unwritten(plan, written, unnamed);
  }

  /**
   * The outcome the answer forces, or null when the record may continue.
   *
   * <p>Two kinds of failure are retried rather than sent to the error sink. A parent that is not
   * there yet — the Pipeline that writes it may simply not have run — and a server error, which
   * says nothing about the record. Everything else is the record's own defect and gives the same
   * answer however often it comes.
   */
  private static RecordOutcome failureOf(int recordIndex, Answered pair) {
    SubRequest request = pair.request();
    SubResponse answer = pair.answer();
    if (request.role() == SubRequestRole.PARENT_LOOKUP) {
      if (answer.resolvedEntity() || answer.skippedByCondition()) {
        // A lookup whose condition did not hold did not run; the request it depends on has
        // already reported the reason.
        return null;
      }
      if (!answer.successful()) {
        return temporary(answer)
            ? RecordOutcome.retry(recordIndex, request.entity(), answer.status(), reasonOf(answer))
            : RecordOutcome.failed(
                recordIndex, request.entity(), answer.status(), reasonOf(answer));
      }
      // The port creates only its own entity, so it does not write the parent either. But a
      // parent another Pipeline writes may simply not be there yet, and the record is retried —
      // for a bounded time, after which a reference that still names nothing is a defect of the
      // data and goes to the error sink.
      return RecordOutcome.retry(
          recordIndex,
          request.entity(),
          404,
          request.missReason() != null
              ? request.missReason()
              : "no " + request.entity() + " matches the reference of the record");
    }
    if (answer.successful()) {
      return null;
    }
    // A miss on the record's own reference is the usual condition of an upsert and leaves a
    // successful status; only a lookup or a write that broke arrives here.
    return temporary(answer)
        ? RecordOutcome.retry(recordIndex, request.entity(), answer.status(), reasonOf(answer))
        : RecordOutcome.failed(recordIndex, request.entity(), answer.status(), reasonOf(answer));
  }

  /**
   * Whether the same request may succeed later: the server failed, timed out, or asked for less
   * load. The batch answer is read the same way.
   */
  private static boolean temporary(SubResponse answer) {
    int status = answer.status();
    return status >= 500 || status == 408 || status == 429;
  }

  /** The outcome when an entity of the record saw no write at all. */
  private static RecordOutcome unwritten(
      RecordPlan plan, Set<String> written, List<SubResponse> unnamed) {
    for (String entity : plan.entitiesRequiringWrite()) {
      if (!written.contains(entity)) {
        // Both halves of the upsert were skipped, or the answer never arrived. Reporting the record
        // as written here would lose it without a trace.
        SubResponse unbound = firstFailure(unnamed);
        if (unbound != null) {
          // An answer that names no request holds the only reason there is, and it applies to
          // every record that stayed unwritten.
          return RecordOutcome.failed(
              plan.recordIndex(), entity, unbound.status(), reasonOf(unbound));
        }
        return RecordOutcome.failed(
            plan.recordIndex(), entity, 0, "no write ran for the " + entity + " of the record");
      }
    }
    return RecordOutcome.written(plan.recordIndex());
  }

  /** The first answer that names no request and reports a failure, or null when there is none. */
  private static SubResponse firstFailure(List<SubResponse> unnamed) {
    for (SubResponse answer : unnamed) {
      if (!answer.successful()) {
        return answer;
      }
    }
    return null;
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
      if (id.equals(remaining.get(index).id())) {
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

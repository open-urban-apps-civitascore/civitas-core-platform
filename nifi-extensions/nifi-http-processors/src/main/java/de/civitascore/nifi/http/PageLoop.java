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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.FlowFileAccessException;

/**
 * One run of an API call: the pages, each through {@link ApiCallClient} as a request of its own,
 * and the FlowFiles of the run. An API call without pagination is a run of one page.
 *
 * <p>A page of a paginated run must be a JSON array, and a page without records ends the run
 * without a FlowFile. An API call without pagination keeps the rules of one response: a JSON object
 * is one record, and {@code []} is delivered.
 */
final class PageLoop {

  private static final String JSON = "application/json";

  private final ApiCall apiCall;
  private final ApiCallClient client;
  private final RecordSchema recordSchema;
  private final int maxPages;
  private final ComponentLog log;

  /**
   * @param maxPages at least 1
   */
  PageLoop(
      ApiCall apiCall,
      ApiCallClient client,
      RecordSchema recordSchema,
      int maxPages,
      ComponentLog log) {
    this.apiCall = apiCall;
    this.client = client;
    this.recordSchema = recordSchema;
    this.maxPages = maxPages;
    this.log = log;
  }

  /**
   * Transfers one FlowFile for each page with records to success, or, when an error ends the run,
   * removes them and transfers one FlowFile that describes the error. The caller commits the
   * session.
   */
  void run(ProcessSession session, Authentication authentication) {
    List<FlowFile> pages = new ArrayList<>();
    Optional<ApiCallOutcome.Failure> failure = readPages(session, authentication, pages);
    if (failure.isPresent()) {
      session.remove(pages);
      transferFailure(session, failure.get());
    } else {
      session.transfer(pages, GetHttpApiCall.SUCCESS);
    }
  }

  /**
   * @return the failure that ends the run, or empty when the run is complete
   */
  private Optional<ApiCallOutcome.Failure> readPages(
      ProcessSession session, Authentication authentication, List<FlowFile> pages) {
    Pagination pagination = apiCall.pagination();
    Page page = pagination.first();
    for (int read = 1; ; read++) {
      ApiCallOutcome outcome = client.send(apiCall, page, authentication);
      int records;
      switch (accept(session, page, outcome, pages)) {
        case PageRead.Failed failed -> {
          return Optional.of(failed.failure());
        }
        case PageRead.Records accepted -> {
          records = accepted.count();
        }
      }
      Optional<Page> next = pagination.next(page, records);
      if (next.isEmpty()) {
        return Optional.empty();
      }
      if (read == maxPages) {
        return Optional.of(
            ApiCallOutcome.Failure.ofPageLimit(apiCall.label(), status(outcome), maxPages));
      }
      page = next.get();
    }
  }

  /** Adds the FlowFile of a page with records, or gives the failure that ends the run. */
  private PageRead accept(
      ProcessSession session, Page page, ApiCallOutcome outcome, List<FlowFile> pages) {
    return switch (outcome) {
      case ApiCallOutcome.Failure failure -> new PageRead.Failed(failure);
      case ApiCallOutcome.NoBody noBody -> {
        logNoBody(page, noBody.status());
        yield new PageRead.Records(0);
      }
      case ApiCallOutcome.Body body -> {
        try (body) {
          yield acceptBody(session, page, body, pages);
        }
      }
    };
  }

  /** Validates the body from its FlowFile, so that only one record at a time is in memory. */
  private PageRead acceptBody(
      ProcessSession session, Page page, ApiCallOutcome.Body body, List<FlowFile> pages) {
    FlowFile flowFile = session.create();
    try {
      flowFile = session.importFrom(body.content(), flowFile);
    } catch (FlowFileAccessException e) {
      session.remove(flowFile);
      return new PageRead.Failed(ApiCallOutcome.Failure.ofTransport(apiCall.describe(page), e));
    }
    boolean paginated = page.number().isPresent();
    return switch (recordSchema.verdict(session, flowFile)) {
      case RecordSchema.Verdict.Blank() -> {
        session.remove(flowFile);
        logNoBody(page, body.status());
        yield new PageRead.Records(0);
      }
      case RecordSchema.Verdict.Rejected(String reason) -> {
        session.remove(flowFile);
        yield new PageRead.Failed(
            ApiCallOutcome.Failure.ofRejectedBody(apiCall.label(page), body.status(), reason));
      }
      case RecordSchema.Verdict.Agrees(int records, boolean array) -> {
        if (paginated && !array) {
          session.remove(flowFile);
          yield new PageRead.Failed(
              ApiCallOutcome.Failure.ofRejectedBody(
                  apiCall.label(),
                  body.status(),
                  "page " + page.number().getAsLong() + " is a JSON object, not an array"));
        }
        if (paginated && records == 0) {
          session.remove(flowFile);
        } else {
          pages.add(session.putAllAttributes(flowFile, attributes(page, body.status())));
        }
        yield new PageRead.Records(records);
      }
    };
  }

  private Map<String, String> attributes(Page page, int status) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(CoreAttributes.MIME_TYPE.key(), JSON);
    attributes.put(GetHttpApiCall.STATUS_CODE, Integer.toString(status));
    attributes.put(GetHttpApiCall.REQUEST_PATH, apiCall.path());
    page.number().ifPresent(number -> attributes.put(GetHttpApiCall.PAGE, Long.toString(number)));
    return attributes;
  }

  private void logNoBody(Page page, int status) {
    log.debug(
        "{} {}{} returned {} without a body",
        apiCall.method().name(),
        apiCall.path(),
        page.number().isPresent() ? " page " + page.number().getAsLong() : "",
        status);
  }

  /** Logs the error, which raises the bulletin, and sends an empty FlowFile that describes it. */
  private void transferFailure(ProcessSession session, ApiCallOutcome.Failure failure) {
    Relationship relationship;
    if (failure.fatal()) {
      log.error(failure.message());
      relationship = GetHttpApiCall.STOP;
    } else {
      log.warn(failure.message());
      relationship = GetHttpApiCall.FAILURE;
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(GetHttpApiCall.REQUEST_PATH, apiCall.path());
    attributes.put(GetHttpApiCall.ERROR_STATUS, Integer.toString(failure.status()));
    attributes.put(GetHttpApiCall.ERROR_MESSAGE, failure.message());
    session.transfer(session.putAllAttributes(session.create(), attributes), relationship);
  }

  private static int status(ApiCallOutcome outcome) {
    return switch (outcome) {
      case ApiCallOutcome.Body body -> body.status();
      case ApiCallOutcome.NoBody noBody -> noBody.status();
      case ApiCallOutcome.Failure failure -> failure.status();
    };
  }

  /** What one page gave: its record count, or the failure that ends the run. */
  private sealed interface PageRead {

    record Records(int count) implements PageRead {}

    record Failed(ApiCallOutcome.Failure failure) implements PageRead {}
  }
}

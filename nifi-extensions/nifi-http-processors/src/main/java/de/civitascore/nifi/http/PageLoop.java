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
import java.util.function.BooleanSupplier;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.flowfile.attributes.CoreAttributes;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.FlowFileAccessException;

/**
 * One run of an API call: the pages, each through {@link ApiCallClient} as a request of its own,
 * and the FlowFiles of the run. An API call without pagination is a run of one page.
 */
final class PageLoop {

  private static final String JSON = "application/json";

  private final ApiCall apiCall;
  private final ApiCallClient client;
  private final RecordSchema recordSchema;
  private final int maxPages;
  private final BooleanSupplier scheduled;
  private final ComponentLog log;

  /**
   * @param scheduled whether NiFi still schedules the processor; NiFi does not interrupt a running
   *     trigger when it stops the processor, so the loop asks before each further page
   */
  PageLoop(
      ApiCall apiCall,
      ApiCallClient client,
      RecordSchema recordSchema,
      int maxPages,
      BooleanSupplier scheduled,
      ComponentLog log) {
    if (maxPages <= 0) {
      throw new IllegalArgumentException("maxPages must be at least 1");
    }
    this.apiCall = apiCall;
    this.client = client;
    this.recordSchema = recordSchema;
    this.maxPages = maxPages;
    this.scheduled = scheduled;
    this.log = log;
  }

  /**
   * Transfers one FlowFile for each page with records to success, or, when an error ends the run,
   * removes them and transfers one FlowFile that describes the error. The caller commits the
   * session.
   */
  void run(ProcessSession session, Authentication authentication) {
    authentication.startRun();
    switch (readPages(session, authentication)) {
      case RunResult.Complete complete ->
          session.transfer(complete.pages(), GetHttpApiCall.SUCCESS);
      case RunResult.Failed failed -> {
        session.remove(failed.pages());
        transferFailure(session, failed.failure());
      }
    }
  }

  private RunResult readPages(ProcessSession session, Authentication authentication) {
    List<FlowFile> pages = new ArrayList<>();
    Page page = withRequestUrl(apiCall.pagination().first(), new NoAuthentication());
    PageRepeats repeats = new PageRepeats(page);
    for (int read = 1; ; read++) {
      ApiCallOutcome outcome = client.send(apiCall, page, authentication);
      Optional<Page> next;
      switch (accept(session, withRequestUrl(page, authentication), outcome, pages, repeats)) {
        case PageRead.Failed failed -> {
          return new RunResult.Failed(pages, failed.failure());
        }
        case PageRead.Read pageRead -> {
          next = pageRead.next();
        }
      }
      if (next.isEmpty()) {
        return new RunResult.Complete(pages);
      }
      Optional<Page> earlier = repeats.earlierWithSameRequest(next.get());
      if (earlier.isPresent()) {
        return new RunResult.Failed(
            pages,
            ApiCallOutcome.Failure.ofRepeatedRequest(
                apiCall.label(), outcome.status(), title(next.get()), title(earlier.get())));
      }
      if (read == maxPages) {
        return new RunResult.Failed(
            pages, ApiCallOutcome.Failure.ofPageLimit(apiCall.label(), outcome.status(), maxPages));
      }
      page = next.get();
      if (!scheduled.getAsBoolean()) {
        return new RunResult.Failed(
            pages, ApiCallOutcome.Failure.ofStopped(apiCall.describe(page)));
      }
    }
  }

  private Page withRequestUrl(Page page, Authentication authentication) {
    return apiCall.pagination() instanceof NextLinkPagination
        ? page.withUrl(client.uri(apiCall, page, authentication))
        : page;
  }

  /**
   * Adds the FlowFile of a page with records, or gives the failure that ends the run. A page
   * without a body, or past the last page, ends the run.
   */
  private PageRead accept(
      ProcessSession session,
      Page page,
      ApiCallOutcome outcome,
      List<FlowFile> pages,
      PageRepeats repeats) {
    return switch (outcome) {
      case ApiCallOutcome.Failure failure -> {
        if (apiCall.pagination().isPastTheEnd(page, failure.status())) {
          log.debug(
              "{} {}{} returned {}: past the last page",
              apiCall.method().name(),
              apiCall.path(),
              page.title().map(title -> " " + title).orElse(""),
              failure.status());
          yield new PageRead.Read(Optional.empty());
        }
        yield new PageRead.Failed(failure);
      }
      case ApiCallOutcome.NoBody noBody -> {
        logNoBody(page, noBody.status());
        yield new PageRead.Read(Optional.empty());
      }
      case ApiCallOutcome.Body body -> {
        try (body) {
          yield acceptBody(session, page, body, pages, repeats);
        }
      }
    };
  }

  /** Validates the body from its FlowFile, so that only one record at a time is in memory. */
  private PageRead acceptBody(
      ProcessSession session,
      Page page,
      ApiCallOutcome.Body body,
      List<FlowFile> pages,
      PageRepeats repeats) {
    FlowFile flowFile = session.create();
    try {
      flowFile = session.importFrom(repeats.hashing(body.content()), flowFile);
    } catch (FlowFileAccessException e) {
      session.remove(flowFile);
      return new PageRead.Failed(ApiCallOutcome.Failure.ofTransport(apiCall.describe(page), e));
    }
    Optional<Page> earlier = repeats.earlierWithSameBody(page);
    if (earlier.isPresent()) {
      session.remove(flowFile);
      return new PageRead.Failed(
          ApiCallOutcome.Failure.ofRepeatedPage(
              apiCall.label(), body.status(), title(page), title(earlier.get())));
    }
    Pagination pagination = apiCall.pagination();
    return switch (recordSchema.verdict(session, flowFile, pagination.shape())) {
      case RecordSchema.Verdict.Blank() -> {
        session.remove(flowFile);
        logNoBody(page, body.status());
        yield new PageRead.Read(Optional.empty());
      }
      case RecordSchema.Verdict.Rejected(String reason) -> {
        session.remove(flowFile);
        yield new PageRead.Failed(
            ApiCallOutcome.Failure.ofRejectedBody(apiCall.label(page), body.status(), reason));
      }
      case RecordSchema.Verdict.WrongType wrongType -> {
        session.remove(flowFile);
        yield new PageRead.Failed(
            ApiCallOutcome.Failure.ofRejectedBody(
                apiCall.label(),
                body.status(),
                wrongType.reason(page.title().orElse("the response"))));
      }
      case RecordSchema.Verdict.Agrees agrees ->
          switch (pagination.next(page, agrees.content())) {
            case Pagination.Progress.Rejected(String reason) -> {
              session.remove(flowFile);
              yield new PageRead.Failed(
                  ApiCallOutcome.Failure.ofRejectedBody(
                      apiCall.label(page), body.status(), reason));
            }
            case Pagination.Progress.Accepted(long records, Optional<Page> next) -> {
              if (records > 0 || pagination.shape() instanceof ResponseShape.ObjectOrArray) {
                pages.add(session.putAllAttributes(flowFile, attributes(page, body.status())));
              } else {
                session.remove(flowFile);
              }
              yield new PageRead.Read(next);
            }
          };
    };
  }

  /** The title of the page, or its position in a run whose pages have no names. */
  private static String title(Page page) {
    return page.title().orElse("request " + (page.index() + 1));
  }

  private Map<String, String> attributes(Page page, int status) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put(CoreAttributes.MIME_TYPE.key(), JSON);
    attributes.put(GetHttpApiCall.STATUS_CODE, Integer.toString(status));
    attributes.put(GetHttpApiCall.REQUEST_PATH, apiCall.path());
    page.position()
        .ifPresent(
            position ->
                attributes.put(
                    GetHttpApiCall.PAGE,
                    apiCall.pagination() instanceof NextLinkPagination
                        ? position.title()
                        : Long.toString(position.number())));
    return attributes;
  }

  private void logNoBody(Page page, int status) {
    log.debug(
        "{} {}{} returned {} without a body",
        apiCall.method().name(),
        apiCall.path(),
        page.title().map(title -> " " + title).orElse(""),
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

  private sealed interface RunResult {

    record Complete(List<FlowFile> pages) implements RunResult {

      public Complete {
        pages = List.copyOf(pages);
      }
    }

    record Failed(List<FlowFile> pages, ApiCallOutcome.Failure failure) implements RunResult {

      public Failed {
        pages = List.copyOf(pages);
      }
    }
  }

  /** What one page gave: the next page, or the failure that ends the run. */
  private sealed interface PageRead {

    /**
     * @param next empty when the run ends with the page
     */
    record Read(Optional<Page> next) implements PageRead {}

    record Failed(ApiCallOutcome.Failure failure) implements PageRead {}
  }
}

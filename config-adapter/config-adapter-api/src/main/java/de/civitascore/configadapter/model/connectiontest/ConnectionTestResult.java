/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.connectiontest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The answer to a {@link ConnectionTestRequest}. A source that does not answer as configured is a
 * {@link Outcome#FAILURE} with a {@link Reason}, not an error of the transport.
 *
 * @param requestId the id of the request
 * @param outcome whether the source answered as configured
 * @param reason why the test failed, null on success
 * @param message what happened, at most 500 characters; never a secret or a platform internal
 * @param preview what the source returned: rows as CSV for SQL, the body of a successful HTTP call;
 *     null otherwise
 * @param previewTruncated whether the preview was cut at its size limit
 * @param rowCount the number of rows in the preview, SQL only
 * @param httpStatus the HTTP status code, when the source answered an HTTP call with one
 * @param durationMillis how long the test took in config-adapter
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectionTestResult(
    String requestId,
    Outcome outcome,
    Reason reason,
    String message,
    String preview,
    boolean previewTruncated,
    Integer rowCount,
    Integer httpStatus,
    long durationMillis) {

  /** Whether the source answered as configured. */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /** Why a test failed. Derived from where the test stopped, never from message text. */
  public enum Reason {
    /** The connector values do not make a runnable source. */
    INVALID_CONNECTOR("The connector values do not make a data source that can run."),
    /** The source answered with an error, or could not be reached. */
    SOURCE_ERROR("The data source answered with an error."),
    /** The source did not answer within the time limit of the test. */
    TIMEOUT("The data source did not answer within the time limit of the test."),
    /** Too many tests run at the moment; nothing was attempted. */
    BUSY("Too many connection tests are running. Try again shortly."),
    /** The test could not be run, for a reason on the platform's side. */
    UNAVAILABLE("The connection test could not be run.");

    private final String defaultMessage;

    Reason(String defaultMessage) {
      this.defaultMessage = defaultMessage;
    }

    /** The message of a failure that has no more specific one. */
    public String defaultMessage() {
      return defaultMessage;
    }
  }

  /** A failure that carries no preview; a null message stands for the reason's default. */
  public static ConnectionTestResult failure(
      String requestId, Reason reason, String message, long durationMillis) {
    return new ConnectionTestResult(
        requestId,
        Outcome.FAILURE,
        reason,
        message == null ? reason.defaultMessage() : message,
        null,
        false,
        null,
        null,
        durationMillis);
  }
}

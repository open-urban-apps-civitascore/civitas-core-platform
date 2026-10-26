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
 * @param preview what the source returned: rows as CSV for SQL, the body of an HTTP call for any
 *     status; null otherwise
 * @param previewTruncated whether the preview was cut at its size limit
 * @param rowCount the number of rows read, SQL only; a cut preview shows fewer
 * @param httpStatus the HTTP status code, when the source answered an HTTP call with one
 * @param durationMillis how long the test took in the connection test service
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
    INVALID_CONNECTOR,
    /** The source answered with an error, or could not be reached. */
    SOURCE_ERROR,
    /** The source did not answer within the time limit of the test. */
    TIMEOUT,
    /** Too many tests run at the moment; nothing was attempted. */
    BUSY,
    /** The test could not be run, for a reason on the platform's side. */
    UNAVAILABLE
  }

  /** A failure that carries no preview. */
  public static ConnectionTestResult failure(
      String requestId, Reason reason, String message, long durationMillis) {
    return new ConnectionTestResult(
        requestId, Outcome.FAILURE, reason, message, null, false, null, null, durationMillis);
  }

  /** Keeps the preview, which holds data of the source, and the message out of logs. */
  @Override
  public String toString() {
    return "ConnectionTestResult[requestId="
        + requestId
        + ", outcome="
        + outcome
        + ", reason="
        + reason
        + "]";
  }
}

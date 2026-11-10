/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.model.dashboardexport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * The answer to a {@link DashboardExportRequest}.
 *
 * @param requestId the id of the request
 * @param outcome whether the dashboard was exported
 * @param reason why the export failed, null on success
 * @param message what happened, null on success; never a secret or a platform internal
 * @param document the cleaned export on success, null otherwise: {@code tool} and {@code files},
 *     each file path without the export's root folder mapped to the parsed file
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DashboardExportResult(
    String requestId,
    Outcome outcome,
    Reason reason,
    String message,
    Map<String, Object> document) {

  /** Whether the dashboard was exported. */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /** Why an export failed. */
  public enum Reason {
    /** The dashboard tool does not know the dashboard. */
    NOT_FOUND,
    /** The dashboard holds what must not be shared; the message says what. */
    REFUSED,
    /** The export could not be run, for a reason on the platform's side. */
    UNAVAILABLE
  }

  public static DashboardExportResult success(String requestId, Map<String, Object> document) {
    return new DashboardExportResult(requestId, Outcome.SUCCESS, null, null, document);
  }

  public static DashboardExportResult failure(String requestId, Reason reason, String message) {
    return new DashboardExportResult(requestId, Outcome.FAILURE, reason, message, null);
  }

  /** Keeps the document and the message out of logs. */
  @Override
  public String toString() {
    return "DashboardExportResult[requestId="
        + requestId
        + ", outcome="
        + outcome
        + ", reason="
        + reason
        + "]";
  }
}

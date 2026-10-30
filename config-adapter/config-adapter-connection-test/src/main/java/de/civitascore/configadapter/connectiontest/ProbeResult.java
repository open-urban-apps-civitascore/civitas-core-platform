/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2026 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.connectiontest;

/**
 * What a source answered.
 *
 * @param success whether the answer is the one a working source gives
 * @param message what happened
 * @param preview what the source returned, or null
 * @param truncated whether the source returned more than the preview
 * @param rowCount the number of rows read, for SQL; null for other source types
 * @param httpStatus the HTTP status code, or null for other source types
 */
record ProbeResult(
    boolean success,
    String message,
    String preview,
    boolean truncated,
    Integer rowCount,
    Integer httpStatus) {

  static ProbeResult success(String message, String preview) {
    return new ProbeResult(true, message, preview, false, null, null);
  }
}

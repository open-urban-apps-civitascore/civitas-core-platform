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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

/**
 * Asks the connection test service to test whether a data source answers with the given connector
 * values. The test moves no data into the platform and changes nothing.
 *
 * @param requestId correlates the {@link ConnectionTestResult}
 * @param expiresAt when the requester stops waiting; a request received later is not run
 * @param connector the connector as the registry stores it: {@code connectionType} in lower case,
 *     every secret as {@code ENC(...)}
 * @param apiCall the name of the API call to run for an HTTP connector, otherwise null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectionTestRequest(
    String requestId,
    @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expiresAt,
    Map<String, Object> connector,
    String apiCall) {

  /** Keeps the connector, and with it the encrypted secrets, out of logs. */
  @Override
  public String toString() {
    return "ConnectionTestRequest[requestId=" + requestId + ", expiresAt=" + expiresAt + "]";
  }
}

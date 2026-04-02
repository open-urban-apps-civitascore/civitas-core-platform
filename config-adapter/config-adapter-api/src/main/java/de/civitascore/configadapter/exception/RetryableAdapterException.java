/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package de.civitascore.configadapter.exception;

import de.civitascore.configadapter.model.AdapterErrorCode;

/**
 * Exception for transient/retryable errors such as:
 *
 * <ul>
 *   <li>Network timeouts
 *   <li>Service unavailable (HTTP 5xx)
 *   <li>Connection failures
 *   <li>Rate limiting
 * </ul>
 *
 * <p>These errors trigger the blocking retry loop in the Kafka event handler. The event will be
 * retried with exponential backoff until either:
 *
 * <ul>
 *   <li>Processing succeeds
 *   <li>Max retry attempts are exceeded (then sent to DLQ)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *     keycloakClient.createUser(user);
 * } catch (ProcessingException e) {
 *     throw new RetryableAdapterException(
 *         AdapterErrorCode.NETWORK_ERROR, e, "keycloak", e.getMessage());
 * }
 * }</pre>
 */
public class RetryableAdapterException extends AdapterException {

  /** serialVersionUID */
  private static final long serialVersionUID = 4666417867349267452L;

  /**
   * Creates a retryable adapter exception with the specified error code and message arguments.
   *
   * @param errorCode the error code (should have isRetryable() == true)
   * @param messageArgs arguments for the internal log message template
   */
  public RetryableAdapterException(AdapterErrorCode errorCode, Object... messageArgs) {
    super(errorCode, messageArgs);
  }

  /**
   * Creates a retryable adapter exception with the specified error code, cause, and message
   * arguments.
   *
   * @param errorCode the error code (should have isRetryable() == true)
   * @param cause the underlying cause
   * @param messageArgs arguments for the internal log message template
   */
  public RetryableAdapterException(
      AdapterErrorCode errorCode, Throwable cause, Object... messageArgs) {
    super(errorCode, cause, messageArgs);
  }

  @Override
  public boolean isRetryable() {
    return true;
  }
}

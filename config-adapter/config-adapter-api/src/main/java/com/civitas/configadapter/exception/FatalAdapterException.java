/**
 * <p>This work and the accompanying materials are made available under the terms of the European Union Public License (EU-PL) 1.2 which is available at https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * <p>SPDX-License-Identifier: EUPL-1.2
 *
 * <p>This project doesn't require a CLA (Contributor License Agreement). The copyright belongs to all the individual contributors:
 * Copyright (c) 2012-2025 Civitas Connect e. V. and others.
 *
 */
package com.civitas.configadapter.exception;

import com.civitas.configadapter.model.AdapterErrorCode;

/**
 * Exception for permanent/fatal errors such as:
 *
 * <ul>
 *   <li>Invalid payload (validation failures)
 *   <li>Resource not found
 *   <li>Missing configuration
 *   <li>Unsupported operations
 *   <li>HTTP 4xx client errors
 *   <li>Conflict errors (HTTP 409)
 * </ul>
 *
 * <p>These errors bypass the retry loop and are sent directly to the Dead Letter Queue (DLQ).
 * Retrying these errors would not succeed and would only waste resources.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try {
 *     keycloakClient.updateUser(userId, user);
 * } catch (NotFoundException e) {
 *     throw new FatalAdapterException(
 *         AdapterErrorCode.RESOURCE_NOT_FOUND, e, "User " + maskUserId(userId));
 * } catch (WebApplicationException e) {
 *     if (e.getResponse().getStatus() == 409) {
 *         throw new FatalAdapterException(
 *             AdapterErrorCode.KEYCLOAK_CONFLICT, e, "User already exists");
 *     }
 *     throw new FatalAdapterException(
 *         AdapterErrorCode.KEYCLOAK_ERROR, e, "HTTP " + e.getResponse().getStatus());
 * }
 * }</pre>
 */
public class FatalAdapterException extends AdapterException {

  /** serialVersionUID */
  private static final long serialVersionUID = 5657530422661119481L;

  /**
   * Creates a fatal adapter exception with the specified error code and message arguments.
   *
   * @param errorCode the error code (should have isRetryable() == false)
   * @param messageArgs arguments for the internal log message template
   */
  public FatalAdapterException(AdapterErrorCode errorCode, Object... messageArgs) {
    super(errorCode, messageArgs);
  }

  /**
   * Creates a fatal adapter exception with the specified error code, cause, and message arguments.
   *
   * @param errorCode the error code (should have isRetryable() == false)
   * @param cause the underlying cause
   * @param messageArgs arguments for the internal log message template
   */
  public FatalAdapterException(AdapterErrorCode errorCode, Throwable cause, Object... messageArgs) {
    super(errorCode, cause, messageArgs);
  }

  @Override
  public boolean isRetryable() {
    return false;
  }
}

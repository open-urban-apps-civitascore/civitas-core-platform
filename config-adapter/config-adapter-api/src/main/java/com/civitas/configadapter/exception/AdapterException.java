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
 * Abstract base exception for all adapter-related errors. Provides:
 *
 * <ul>
 *   <li>Structured error codes via {@link AdapterErrorCode}
 *   <li>Safe external messages (no PII, no stack traces)
 *   <li>Detailed internal messages for logging
 *   <li>Retryability indication for error handling strategies
 * </ul>
 *
 * <p>Subclasses:
 *
 * <ul>
 *   <li>{@link RetryableAdapterException} - for transient errors that should be retried
 *   <li>{@link FatalAdapterException} - for permanent errors sent directly to DLQ
 * </ul>
 *
 * <p>This is an unchecked exception (extends RuntimeException) to allow propagation through the
 * adapter layer without requiring explicit throws declarations.
 */
public abstract class AdapterException extends Exception {

  /** serialVersionUID */
  private static final long serialVersionUID = 4733953678711945607L;

  private final AdapterErrorCode errorCode;
  private final String internalMessage;

  /**
   * Creates an adapter exception with the specified error code and message arguments.
   *
   * @param errorCode the error code
   * @param messageArgs arguments for the internal log message template
   */
  protected AdapterException(AdapterErrorCode errorCode, Object... messageArgs) {
    super(errorCode.formatInternalMessage(messageArgs));
    this.errorCode = errorCode;
    this.internalMessage = errorCode.formatInternalMessage(messageArgs);
  }

  /**
   * Creates an adapter exception with the specified error code, cause, and message arguments.
   *
   * @param errorCode the error code
   * @param cause the underlying cause
   * @param messageArgs arguments for the internal log message template
   */
  protected AdapterException(AdapterErrorCode errorCode, Throwable cause, Object... messageArgs) {
    super(errorCode.formatInternalMessage(messageArgs), cause);
    this.errorCode = errorCode;
    this.internalMessage = errorCode.formatInternalMessage(messageArgs);
  }

  /**
   * Returns the error code for this exception.
   *
   * @return the error code
   */
  public AdapterErrorCode getErrorCode() {
    return errorCode;
  }

  /**
   * Returns the numeric error code.
   *
   * @return the numeric code
   */
  public int getNumericCode() {
    return errorCode.getCode();
  }

  /**
   * Returns whether this error is retryable.
   *
   * @return true if retryable
   */
  public abstract boolean isRetryable();

  /**
   * Returns the detailed internal message for logging. This may contain sensitive information and
   * should NOT be exposed to external consumers.
   *
   * @return the internal message
   */
  public String getInternalMessage() {
    return internalMessage;
  }

  /**
   * Returns the safe external message suitable for external consumers. This message:
   *
   * <ul>
   *   <li>Contains no PII
   *   <li>Contains no stack traces
   *   <li>Contains no internal system details
   * </ul>
   *
   * @return the safe external message
   */
  public String getSafeExternalMessage() {
    return errorCode.getExternalUserMessage();
  }

  /**
   * Returns the full error identifier including the error code name and numeric code.
   *
   * @return the full error identifier
   */
  public String getFullErrorIdentifier() {
    return errorCode.getFullIdentifier();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "["
        + errorCode.getFullIdentifier()
        + "]: "
        + internalMessage;
  }
}

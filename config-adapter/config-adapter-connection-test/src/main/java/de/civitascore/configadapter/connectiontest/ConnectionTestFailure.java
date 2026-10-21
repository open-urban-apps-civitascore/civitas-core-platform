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

import de.civitascore.configadapter.model.connectiontest.ConnectionTestResult.Reason;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;

/**
 * A connection test that ended without the answer of a working source. The message says why and is
 * safe to show the user; the cause is kept for the log only.
 */
final class ConnectionTestFailure extends Exception {

  private static final long serialVersionUID = 1L;

  private final Reason reason;
  private final boolean destinationRefused;

  /** The source answered with an error or could not be reached. */
  ConnectionTestFailure(String message) {
    this(Reason.SOURCE_ERROR, message, null);
  }

  /** The source answered with an error or could not be reached. */
  ConnectionTestFailure(String message, Throwable cause) {
    this(Reason.SOURCE_ERROR, message, cause);
  }

  ConnectionTestFailure(Reason reason, String message, Throwable cause) {
    this(reason, message, cause, false);
  }

  private ConnectionTestFailure(
      Reason reason, String message, Throwable cause, boolean destinationRefused) {
    super(message, cause);
    this.reason = reason;
    this.destinationRefused = destinationRefused;
  }

  /** The connector values do not make a source the test can run. */
  static ConnectionTestFailure invalid(String message) {
    return new ConnectionTestFailure(Reason.INVALID_CONNECTOR, message, null);
  }

  /** The connector values do not make a source the test can run. */
  static ConnectionTestFailure invalid(String message, Throwable cause) {
    return new ConnectionTestFailure(Reason.INVALID_CONNECTOR, message, cause);
  }

  /** The destination check refused the host; a security event, not a typo. */
  static ConnectionTestFailure refused(String message) {
    return new ConnectionTestFailure(Reason.INVALID_CONNECTOR, message, null, true);
  }

  /** The source did not answer within the time the test allows. */
  static ConnectionTestFailure timeout(String message, Throwable cause) {
    return new ConnectionTestFailure(Reason.TIMEOUT, message, cause);
  }

  Reason reason() {
    return reason;
  }

  boolean destinationRefused() {
    return destinationRefused;
  }

  /** The type and stack of {@code e} without its text, which may quote connector values. */
  static Throwable withoutText(Throwable e) {
    Throwable copy = new Throwable(e.getClass().getName());
    copy.setStackTrace(e.getStackTrace());
    return copy;
  }

  /**
   * A client exception's type and text, after a plain hint when the server's certificate was
   * refused.
   */
  static String describe(Throwable cause) {
    String message = cause.getMessage();
    if ((message == null || message.isBlank()) && cause instanceof ConnectException) {
      // The JDK's HTTP client reports both without any text; only the deepest cause differs.
      message =
          inChain(cause, UnresolvedAddressException.class)
              ? "The host name could not be resolved"
              : "Connection refused";
    }
    String text =
        message == null || message.isBlank()
            ? cause.getClass().getSimpleName()
            : cause.getClass().getSimpleName() + ": " + message;
    return trustHint(cause) + text;
  }

  /** Read from the exception types, never from their text, which the JDK may word differently. */
  private static String trustHint(Throwable cause) {
    // An expired certificate also fails path validation, so expiry is checked first.
    if (inChain(cause, CertificateExpiredException.class)) {
      return "The server's certificate has expired. ";
    }
    if (inChain(cause, CertPathBuilderException.class)
        || inChain(cause, CertPathValidatorException.class)) {
      return "The server's certificate is not issued by a trusted certificate authority. ";
    }
    return "";
  }

  private static boolean inChain(Throwable cause, Class<? extends Throwable> type) {
    for (Throwable t = cause; t != null; t = next(t)) {
      if (type.isInstance(t)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a socket timed out somewhere below {@code cause}. */
  static boolean timedOut(Throwable cause) {
    return inChain(cause, SocketTimeoutException.class);
  }

  /** The cause of {@code t}, or null at the end of the chain. */
  private static Throwable next(Throwable t) {
    Throwable cause = t.getCause();
    return cause == null || cause.equals(t) ? null : cause;
  }
}

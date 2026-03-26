package de.civitascore.portal.util;

/** Thrown when an external system does not respond within the configured timeout. */
public class ExternalSystemTimeoutException extends RuntimeException {
  public ExternalSystemTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}

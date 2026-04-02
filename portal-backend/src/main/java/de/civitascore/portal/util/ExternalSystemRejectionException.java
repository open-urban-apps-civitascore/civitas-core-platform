package de.civitascore.portal.util;

/** Thrown when an external system (e.g., Keycloak, APISIX) rejects a request. */
public class ExternalSystemRejectionException extends RuntimeException {
  public ExternalSystemRejectionException(String message) {
    super(message);
  }

  public ExternalSystemRejectionException(String message, Throwable cause) {
    super(message, cause);
  }
}

package de.civitascore.portal.util;

public class ExternalSystemRejectionException extends RuntimeException {
  public ExternalSystemRejectionException(String message) {
    super(message);
  }

  public ExternalSystemRejectionException(String message, Throwable cause) {
    super(message, cause);
  }
}

package de.civitascore.portal.util;

/** Thrown when the user already runs a connection test, or config-adapter runs its maximum. */
public class ConnectionTestBusyException extends RuntimeException {

  public ConnectionTestBusyException(String message) {
    super(message);
  }
}

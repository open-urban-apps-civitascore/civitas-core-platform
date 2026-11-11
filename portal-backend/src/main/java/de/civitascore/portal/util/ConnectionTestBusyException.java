package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/** Thrown when the user already runs a connection test, or too many tests run. */
public class ConnectionTestBusyException extends ProblemException {

  public ConnectionTestBusyException(String message) {
    super(ProblemType.TOO_MANY_REQUESTS, message);
  }
}

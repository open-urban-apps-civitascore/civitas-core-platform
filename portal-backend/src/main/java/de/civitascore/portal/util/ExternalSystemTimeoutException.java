package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/** Thrown when an external system does not respond within the configured timeout. */
public class ExternalSystemTimeoutException extends ProblemException {
  public ExternalSystemTimeoutException(String message, Throwable cause) {
    super(ProblemType.EXTERNAL_SYSTEM_TIMEOUT, message, cause);
  }
}

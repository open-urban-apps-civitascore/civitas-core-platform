package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;

/** Thrown when an external system (e.g., Keycloak, APISIX) rejects a request. */
public class ExternalSystemRejectionException extends ProblemException {
  public ExternalSystemRejectionException(String message) {
    super(ProblemType.EXTERNAL_SYSTEM_ERROR, message);
  }
}

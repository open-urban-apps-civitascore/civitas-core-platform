package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.UUID;

/** Thrown when the current user lacks permission to access or modify a specific resource. */
public class ForbiddenException extends ProblemException {

  private final String resourceType;
  private final UUID resourceId;

  public ForbiddenException(String resourceType, UUID resourceId, String message) {
    super(ProblemType.FORBIDDEN, message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  @Override
  public String toString() {
    return "ForbiddenException{"
        + "resourceType='"
        + resourceType
        + '\''
        + (resourceId != null ? ", resourceId='" + resourceId + '\'' : "")
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}

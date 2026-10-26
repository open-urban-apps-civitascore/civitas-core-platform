package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when request input fails business-rule validation. The message is the detail of the
 * answer, so it is built by our own validation code and never reflects database or framework
 * internals (TR-03187 W-18/W-19).
 */
@Getter
public class InvalidInputException extends ProblemException {

  private final String resourceType;
  private final UUID resourceId;
  private final String resourceInfo;

  public InvalidInputException(String resourceType, UUID resourceId, String message) {
    super(ProblemType.INVALID_INPUT, message, message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceInfo = null;
  }

  public InvalidInputException(String resourceType, String resourceInfo, String message) {
    super(ProblemType.INVALID_INPUT, message, message);
    this.resourceType = resourceType;
    this.resourceId = null;
    this.resourceInfo = resourceInfo;
  }

  @Override
  public String toString() {
    return "InvalidInputException{"
        + "resourceType='"
        + resourceType
        + '\''
        + (resourceId != null ? ", resourceId='" + resourceId + '\'' : "")
        + (resourceInfo != null ? ", resourceInfo='" + resourceInfo + '\'' : "")
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}

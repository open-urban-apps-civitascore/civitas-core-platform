package de.civitascore.portal.util;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when the current user lacks permission to access or modify a specific resource. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;
  private final String resourceInfo;

  public ForbiddenException(String resourceType, UUID resourceId, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceInfo = null;
  }

  public ForbiddenException(String resourceType, String resourceInfo, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = null;
    this.resourceInfo = resourceInfo;
  }

  @Override
  public String toString() {
    return "ForbiddenException{"
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

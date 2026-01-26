package de.civitascore.portal.util;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;
  private final String ressourceInfo;

  public ForbiddenException(String resourceType, UUID resourceId, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.ressourceInfo = null;
  }

  public ForbiddenException(String resourceType, String ressourceInfo, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = null;
    this.ressourceInfo = ressourceInfo;
  }

  @Override
  public String toString() {
    return "ForbiddenException{"
        + "resourceType='"
        + resourceType
        + '\''
        + (resourceId != null ? ", resourceId='" + resourceId + '\'' : "")
        + (ressourceInfo != null ? ", ressourceInfo='" + ressourceInfo + '\'' : "")
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}

package de.civitascore.portal.util;

import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when request input fails business-rule validation. */
@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInputException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;
  private final String resourceInfo;

  public InvalidInputException(String resourceType, UUID resourceId, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceInfo = null;
  }

  public InvalidInputException(String resourceType, String resourceInfo, String message) {
    super(message);
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

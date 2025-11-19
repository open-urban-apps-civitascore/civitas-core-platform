package de.civitascore.portal.util;

import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInputException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;
  private final String ressourceInfo;

  public InvalidInputException(String resourceType, UUID resourceId, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.ressourceInfo = null;
  }

  public InvalidInputException(String resourceType, String ressourceInfo, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = null;
    this.ressourceInfo = ressourceInfo;
  }

  @Override
  public String toString() {
    return "InvalidInputException{"
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

package de.civitascore.portal.util;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInputException extends RuntimeException {

  private final String resourceType;
  private final String resourceId;

  public InvalidInputException(String resourceType, String resourceId, String message) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  @Override
  public String toString() {
    return "InvalidInputException{"
        + "resourceType='"
        + resourceType
        + '\''
        + ", resourceId='"
        + resourceId
        + '\''
        + ", message='"
        + getMessage()
        + '\''
        + '}';
  }
}

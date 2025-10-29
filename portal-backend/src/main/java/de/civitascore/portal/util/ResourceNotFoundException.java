package de.civitascore.portal.util;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

  private final String resourceType;
  private final String resourceId;

  public ResourceNotFoundException(String resourceType, String resourceId) {
    super(String.format("%s with id '%s' not found", resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }
}

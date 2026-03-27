package de.civitascore.portal.util;

import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a requested resource cannot be found by its identifier. */
@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;

  public ResourceNotFoundException(String resourceType, UUID resourceId) {
    super(String.format("%s with id '%s' not found", resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }
}

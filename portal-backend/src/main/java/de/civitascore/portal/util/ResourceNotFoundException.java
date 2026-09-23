package de.civitascore.portal.util;

import java.util.Collection;
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
  private final Collection<UUID> resourceIds;

  public ResourceNotFoundException(String resourceType, UUID resourceId) {
    super("%s with id '%s' not found".formatted(resourceType, resourceId));
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceIds = null;
  }

  /** For a resource addressed by a CORE URN rather than a UUID. */
  public ResourceNotFoundException(String resourceType, String resourceUrn) {
    super("%s '%s' not found".formatted(resourceType, resourceUrn));
    this.resourceType = resourceType;
    this.resourceId = null;
    this.resourceIds = null;
  }

  public ResourceNotFoundException(String resourceType, Collection<UUID> resourceIds) {
    super("%s with ids %s not found".formatted(resourceType, resourceIds));
    this.resourceType = resourceType;
    this.resourceId = null;
    this.resourceIds = resourceIds;
  }
}

package de.civitascore.portal.util;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a resource cannot be deleted or modified because it is still referenced. */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class ResourceInUseException extends RuntimeException {

  private final String resourceType;
  private final UUID resourceId;

  /** What still references the resource. Logged on refusal, never returned to the caller. */
  private final transient List<String> blockedBy;

  public ResourceInUseException(String resourceType, UUID resourceId, String message) {
    this(resourceType, resourceId, message, List.of());
  }

  public ResourceInUseException(
      String resourceType, UUID resourceId, String message, List<String> blockedBy) {
    super(message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
  }
}

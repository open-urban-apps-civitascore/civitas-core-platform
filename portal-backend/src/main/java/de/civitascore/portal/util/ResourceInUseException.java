package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** Thrown when a resource cannot be deleted or modified because it is still referenced. */
@Getter
public class ResourceInUseException extends ProblemException {

  private final String resourceType;
  private final UUID resourceId;

  /** What still references the resource. Logged on refusal, never returned to the caller. */
  private final transient List<String> blockedBy;

  public ResourceInUseException(String resourceType, UUID resourceId, String message) {
    this(resourceType, resourceId, message, List.of());
  }

  public ResourceInUseException(
      String resourceType, UUID resourceId, String message, List<String> blockedBy) {
    super(ProblemType.RESOURCE_IN_USE, message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.blockedBy = blockedBy == null ? List.of() : List.copyOf(blockedBy);
  }

  @Override
  public String toString() {
    return super.toString() + " blockedBy=" + blockedBy;
  }
}

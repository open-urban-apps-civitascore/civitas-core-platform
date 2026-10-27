package de.civitascore.portal.util;

import de.civitascore.portal.problem.ProblemException;
import de.civitascore.portal.problem.ProblemType;
import java.util.Collection;
import java.util.UUID;
import lombok.Getter;

/**
 * Thrown when a requested resource cannot be found by its identifier. The message is the detail of
 * the answer: it echoes an id the client sent, and ids are UUIDs that cannot be enumerated
 * (TR-03187 W-18/W-19).
 */
@Getter
public class ResourceNotFoundException extends ProblemException {

  private final String resourceType;
  private final UUID resourceId;
  private final Collection<UUID> resourceIds;

  public ResourceNotFoundException(String resourceType, UUID resourceId) {
    this(
        "%s with id '%s' not found".formatted(resourceType, resourceId),
        resourceType,
        resourceId,
        null);
  }

  /** For a resource addressed by a CORE URN rather than a UUID. */
  public ResourceNotFoundException(String resourceType, String resourceUrn) {
    this("%s '%s' not found".formatted(resourceType, resourceUrn), resourceType, null, null);
  }

  public ResourceNotFoundException(String resourceType, Collection<UUID> resourceIds) {
    this(
        "%s with ids %s not found".formatted(resourceType, resourceIds),
        resourceType,
        null,
        resourceIds);
  }

  private ResourceNotFoundException(
      String message, String resourceType, UUID resourceId, Collection<UUID> resourceIds) {
    super(ProblemType.NOT_FOUND, message);
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.resourceIds = resourceIds;
  }
}

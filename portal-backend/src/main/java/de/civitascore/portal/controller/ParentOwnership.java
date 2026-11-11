package de.civitascore.portal.controller;

import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;

/**
 * Checks that a nested resource belongs to the parent its path names. OPA authorizes nested routes
 * against the parent in the path only, so a resource of another parent must look like an unknown
 * one and cannot reveal the other parent's state.
 */
final class ParentOwnership {

  private ParentOwnership() {}

  /**
   * @throws ResourceNotFoundException if {@code actualParentId} is not {@code pathParentId}
   */
  static void requireSameParent(
      UUID pathParentId, UUID actualParentId, Class<?> entityType, UUID id) {
    if (!pathParentId.equals(actualParentId)) {
      throw new ResourceNotFoundException(entityType.getSimpleName(), id);
    }
  }
}

package de.civitascore.portal.model.embedded;

/**
 * Categorizes permissions by domain: system administration or data operations.
 *
 * @see de.civitascore.portal.model.entity.Permission
 */
public enum PermissionType {
  /** System administration. */
  SYSTEM,

  /** Data access and management operations. */
  DATA
}

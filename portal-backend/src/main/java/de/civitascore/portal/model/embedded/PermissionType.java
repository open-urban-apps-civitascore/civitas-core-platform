package de.civitascore.portal.model.embedded;

/**
 * Categorizes permissions by domain: system administration, data operations, or governance.
 *
 * @see de.civitascore.portal.model.entity.Permission
 */
public enum PermissionType {
  /** System administration and tenant-wide operations. */
  SYSTEM,

  /** Data access and management operations. */
  DATA,

  /** Data governance, policies, and compliance operations. */
  GOVERNANCE
}

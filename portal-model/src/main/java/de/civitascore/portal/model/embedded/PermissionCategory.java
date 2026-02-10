package de.civitascore.portal.model.embedded;

/**
 * Categorizes permissions by domain: tenant-administration or data operations.
 *
 * @see de.civitascore.portal.model.entity.Permission
 */
public enum PermissionCategory {
  /** Tenant administration permissions. */
  TENANT_ADMINISTRATION,

  /** Data access and management permissions. */
  DATA,
}

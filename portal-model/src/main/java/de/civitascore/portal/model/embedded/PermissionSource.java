package de.civitascore.portal.model.embedded;

/**
 * An enumeration representing the source of a permission. This can be used to differentiate between
 * permissions that are defined internally within the application and those that are imported from
 * external sources.
 *
 * @see de.civitascore.portal.model.entity.Permission
 */
public enum PermissionSource {
  /** Internal permissions defined within the application */
  INTERNAL,

  /** Permissions sourced from the dashboard engine */
  DATASET_DASHBOARD
}

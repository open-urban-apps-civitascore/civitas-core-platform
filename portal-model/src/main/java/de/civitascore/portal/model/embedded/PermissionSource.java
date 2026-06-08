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

  /**
   * Permissions sourced from the dashboard engine. These abstract the dashboard engine's native
   * rights down to a small managed set rather than mirroring them one-to-one. The concrete engine
   * is an implementation detail and may change without affecting this permission model.
   */
  DASHBOARD
}

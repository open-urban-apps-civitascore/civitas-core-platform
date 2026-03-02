package de.civitascore.portal.model.embedded;

/** Defines the lifecycle status for data sources. */
public enum DataSourceStatus {
  /** Initial state. Configuration may be incomplete. */
  DRAFT,

  /** Published state. Configuration has been validated and is complete. */
  AVAILABLE,
}

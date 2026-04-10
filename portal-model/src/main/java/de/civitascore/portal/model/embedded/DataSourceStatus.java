package de.civitascore.portal.model.embedded;

/** Defines the lifecycle status for data sources. */
public enum DataSourceStatus implements ReleasableStatus {
  /** Initial state. Configuration may be incomplete. */
  DRAFT,

  /** Published state. Configuration has been validated and is complete. */
  AVAILABLE;

  @Override
  public boolean isDraft() {
    return this == DRAFT;
  }

  @Override
  public boolean isAvailable() {
    return this == AVAILABLE;
  }
}

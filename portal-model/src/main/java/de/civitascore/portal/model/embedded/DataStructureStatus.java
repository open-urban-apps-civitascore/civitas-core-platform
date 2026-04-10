package de.civitascore.portal.model.embedded;

/** Lifecycle status of a data structure (DRAFT or AVAILABLE). */
public enum DataStructureStatus implements ReleasableStatus {
  DRAFT,
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

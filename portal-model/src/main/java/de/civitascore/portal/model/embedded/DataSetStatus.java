package de.civitascore.portal.model.embedded;

/**
 * Status of a DataSet in its lifecycle.
 *
 * <ul>
 *   <li>DRAFT: Dataset is being created/edited
 *   <li>READY: Dataset has been published and validated
 *   <li>AVAILABLE: Dataset is available for consumption
 * </ul>
 */
public enum DataSetStatus implements ReleasableStatus {
  DRAFT,
  READY,
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

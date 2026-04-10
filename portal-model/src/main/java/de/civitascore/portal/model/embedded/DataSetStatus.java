package de.civitascore.portal.model.embedded;

/**
 * Status of a DataSet in its lifecycle.
 *
 * <ul>
 *   <li>DRAFT: Dataset is being created/edited
 *   <li>READY: Dataset has been validated and marked as ready
 *   <li>AVAILABLE: Dataset is available for consumption
 * </ul>
 */
public enum DataSetStatus implements ReleasableStatus {
  DRAFT,
  READY,
  AVAILABLE
}

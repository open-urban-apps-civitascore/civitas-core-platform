package de.civitascore.portal.model.embedded;

/**
 * Represents the type of a pending saga operation on a DataSet.
 *
 * <ul>
 *   <li>CREATE: Infrastructure provisioning saga in progress
 *   <li>UPDATE: Infrastructure update saga in progress
 *   <li>DELETE: Infrastructure teardown saga in progress
 * </ul>
 */
public enum PendingSagaType {
  CREATE,
  UPDATE,
  DELETE
}

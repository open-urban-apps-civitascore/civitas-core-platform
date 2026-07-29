package de.civitascore.portal.model.embedded;

/**
 * Represents the type of a pending saga operation on a DataSet.
 *
 * <ul>
 *   <li>CREATE: Infrastructure provisioning saga in progress
 *   <li>UPDATE: Infrastructure update saga in progress
 *   <li>UNRELEASE: Ingest/consumer-access teardown saga in progress (pipeline + route); the
 *       data-holding sink (PostGIS table, FROST project) is deliberately kept
 *   <li>DELETE: Full infrastructure teardown saga in progress (including the data-holding sink)
 * </ul>
 */
public enum PendingSagaType {
  CREATE,
  UPDATE,
  UNRELEASE,
  DELETE
}

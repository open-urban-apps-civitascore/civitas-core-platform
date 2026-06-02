package de.civitascore.portal.model.embedded;

/**
 * Discriminator for a {@link de.civitascore.portal.model.entity.DataSink}, determining which
 * configuration shape is stored in the {@code configuration} JSONB column.
 *
 * <ul>
 *   <li>POSTGIS – writes to a PostGIS table; requires {@code tableName} and {@code
 *       dataStructureVersionId} in the configuration.
 *   <li>FROST – writes to a FROST SensorThings server; requires an empty configuration.
 * </ul>
 */
public enum DataSinkType {
  FROST,
  POSTGIS
}

package de.civitascore.portal.model.embedded;

/**
 * API standard exposed by a named API on a dataset.
 *
 * <ul>
 *   <li>{@code WFS} — OGC Web Feature Service
 *   <li>{@code WMS} — OGC Web Map Service
 *   <li>{@code STA} — OGC SensorThings API
 *   <li>{@code CUSTOM} — free-form, non-standard API
 * </ul>
 */
public enum ApiStandard {
  WFS,
  WMS,
  STA,
  CUSTOM
}

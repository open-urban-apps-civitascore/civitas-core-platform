package de.civitascore.portal.model.embedded;

/**
 * API standard exposed by a named API on a dataset.
 *
 * <ul>
 *   <li>{@code OWS} — OGC Service
 *   <li>{@code STA} — OGC SensorThings API
 *   <li>{@code CUSTOM} — free-form, non-standard API
 * </ul>
 */
public enum ApiStandard {
  OWS,
  STA,
  CUSTOM
}

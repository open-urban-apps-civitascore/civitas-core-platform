package de.civitascore.portal.model.embedded;

/**
 * Lifecycle state of a {@link de.civitascore.portal.model.entity.DataSink}.
 *
 * <ul>
 *   <li>{@code ACTIVE}: the sink belongs to the Dataset; a release provisions it.
 *   <li>{@code PENDING_UNPROVISION}: the sink was removed after it was provisioned. Its row stays
 *       until the next release removes its storage.
 * </ul>
 */
public enum DataSinkState {
  ACTIVE,
  PENDING_UNPROVISION
}

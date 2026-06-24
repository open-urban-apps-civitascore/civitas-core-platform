package de.civitascore.portal.model.embedded;

/**
 * Defines the scope of Datapool access for a DataSource.
 *
 * <p>{@code ALL} / {@code SPECIFIC} realise the boolean (all / specific) scope of ticket #1590;
 * {@code NONE} ("usable in no pipeline") is a deliberate product extension beyond #1590 / concept
 * #1392 — keep those tickets in sync with this enum (review finding F5).
 */
public enum DatapoolScopeType {
  /** DataSource may be used in any pipeline. */
  ALL,

  /** DataSource may not be used in any pipeline. */
  NONE,

  /**
   * DataSource may only be used in pipelines whose Dataset belongs to one of the scoped Datapools.
   */
  SPECIFIC
}

package de.civitascore.portal.model.embedded;

/** Defines the scope of Datapool access for a DataSource. */
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

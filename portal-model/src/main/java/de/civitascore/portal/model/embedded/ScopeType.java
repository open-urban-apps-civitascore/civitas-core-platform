package de.civitascore.portal.model.embedded;

/**
 * Defines the scope level for assignments. TENANT requires no scope ID; all other scope types
 * require a scope ID identifying the target entity.
 *
 * @see de.civitascore.portal.model.entity.Assignment
 * @see de.civitascore.portal.model.entity.base.AssignableEntity
 */
public enum ScopeType {
  /** Tenant-wide scope for data roles. Requires scope ID to be null. */
  TENANT,

  /** Data structure scope for data roles. Requires scope ID. */
  DATASTRUCTURE,

  /** Data source scope for data roles. Requires scope ID. */
  DATASOURCE,

  /** Dataset scope for data roles. Requires scope ID. */
  DATASET,

  /** Datacatalogue scope for data roles. Requires scope ID. */
  CATALOG,

  /** DataPool scope for data roles. Requires scope ID. */
  DATAPOOL,
}

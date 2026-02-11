package de.civitascore.portal.model.embedded;

/**
 * Defines the hierarchical scope level for assignments: DATASPACE → DATASET.
 *
 * <p>DATASPACE and DATASET scopes require a scope ID to identify the target entity.
 *
 * @see de.civitascore.portal.model.entity.Assignment
 * @see de.civitascore.portal.model.entity.base.ScopedEntity
 */
public enum ScopeType {
  /** Tenant-wide scope for data roles. Requires scope ID to be null. */
  TENANT,

  /** Data structure scope for data/governance roles. Requires scope ID. */
  DATASTRUCTURE,

  /** Data source scope for data/governance roles. Requires scope ID. */
  DATASOURCE,

  /** Dataset scope for data/governance roles. Requires scope ID. */
  DATASET,

  /** Dataspace scope for data/governance roles. Requires scope ID. */
  DATASPACE,

  /** Datacatalogue scope for data/governance roles. Requires scope ID. */
  CATALOG,
}

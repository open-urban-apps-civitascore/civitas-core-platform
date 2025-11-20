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
  /** Tenant-wide scope for system roles. */
  TENANT,

  /** Dataspace scope for data/governance roles. Requires scope ID. */
  DATASPACE,

  /** Dataset scope for data/governance roles. Requires scope ID. */
  DATASET
}

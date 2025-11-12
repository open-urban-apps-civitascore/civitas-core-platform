package de.civitascore.portal.model.embedded;

/**
 * Assignment type derived from role type.
 *
 * <p>BINARY: System roles (group + role). TERNARY: Data/Governance roles (group + role + scope).
 * Automatically computed, not stored.
 *
 * @see de.civitascore.portal.model.entity.Assignment#getAssignmentType()
 * @see RoleType
 */
public enum AssignmentType {
  /** Binary assignment for system roles at tenant level. */
  BINARY,

  /** Ternary assignment for data/governance roles with specific scope. */
  TERNARY
}

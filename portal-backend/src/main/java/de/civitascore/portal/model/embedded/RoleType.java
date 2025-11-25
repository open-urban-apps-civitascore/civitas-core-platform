package de.civitascore.portal.model.embedded;

/**
 * Categorizes roles and determines assignment type.
 *
 * <p>SYSTEM roles → binary assignments. DATA/GOVERNANCE roles → ternary assignments (require
 * scope).
 *
 * @see de.civitascore.portal.model.entity.Role
 * @see AssignmentType
 */
public enum RoleType {
  /** System administration roles. */
  SYSTEM,

  /** Data operation roles. Requires dataspace/dataset scope. */
  DATA,

  /** Data governance roles. Requires dataspace/dataset scope. */
  GOVERNANCE
}

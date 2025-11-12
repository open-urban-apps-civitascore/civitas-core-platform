package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.AssignmentType;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.base.ScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "assignments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_assignment_group_role_scope",
            columnNames = {"group_id", "role_id", "scope_type", "scope_id", "tenant_id"}),
    indexes = {
      @Index(name = "idx_assignment_group", columnList = "group_id"),
      @Index(name = "idx_assignment_role", columnList = "role_id"),
      @Index(name = "idx_assignment_scope", columnList = "scope_type, scope_id")
    })
@Getter
@Setter
public class Assignment extends ScopedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private Group group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Column(name = "is_inherited", nullable = false)
  private Boolean isInherited = false;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_assignment_id")
  private Assignment parentAssignment;

  /**
   * Derives the assignment type based on the role type. Binary assignments: System roles assigned
   * to user groups (tenant scope) Ternary assignments: Data/Governance roles assigned to
   * datasets/dataspaces
   *
   * @return BINARY for system roles, TERNARY for data and governance roles
   */
  public AssignmentType getAssignmentType() {
    if (role == null || role.getRoleType() == null) {
      return null;
    }
    return role.getRoleType() == RoleType.SYSTEM ? AssignmentType.BINARY : AssignmentType.TERNARY;
  }

  @PrePersist
  protected void validateBeforePersist() {
    validateScope();
    validateAssignment();
  }

  @Override
  protected void validateOnUpdate() {
    super.validateOnUpdate();
    validateAssignment();
  }

  private void validateAssignment() {
    if (role.getRoleType() == RoleType.SYSTEM) {
      // Binary assignment: System roles must use TENANT scope
      if (getScopeType() != ScopeType.TENANT) {
        throw new IllegalStateException(role.getRoleType() + " roles must use TENANT scope");
      }
    } else {
      // Ternary assignment: Data/Governance roles require explicit scope
      if (getScopeType() == null) {
        throw new IllegalStateException(role.getRoleType() + " roles require explicit scope");
      }
    }
  }
}

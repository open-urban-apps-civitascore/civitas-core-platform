package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.AssignmentType;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.base.ScopedEntity;
import jakarta.persistence.*;
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
      @Index(name = "idx_assignment_scope", columnList = "scope_type, scope_id"),
      @Index(name = "idx_assignment_type", columnList = "assignment_type")
    })
@Getter
@Setter
public class Assignment extends ScopedEntity<String> {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private Group group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_type", nullable = false)
  private AssignmentType assignmentType;

  @Column(name = "is_inherited", nullable = false)
  private Boolean isInherited = false;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_assignment_id")
  private Assignment parentAssignment;

  @Column(name = "metadata", columnDefinition = "TEXT")
  private String metadata;

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
      assignmentType = AssignmentType.BINARY;
      if (getScopeType() != ScopeType.TENANT) {
        throw new IllegalStateException("SYSTEM roles must use TENANT scope");
      }
    } else {
      assignmentType = AssignmentType.TERNARY;
      if (getScopeType() == null) {
        throw new IllegalStateException("DATA/GOVERNANCE roles require explicit scope");
      }
    }
  }
}

package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import de.civitascore.portal.model.entity.base.NamedEntity;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "assignments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_assignment_group_role_scope",
            columnNames = {
              "group_id",
              "role_id",
              "scope_type",
              "data_structure_id",
              "data_source_id",
              "dataset_id",
              "data_space_id",
              "catalog_id"
            }),
    indexes = {
      @Index(name = "idx_assignment_group", columnList = "group_id"),
      @Index(name = "idx_assignment_role", columnList = "role_id"),
      @Index(name = "idx_assignment_datastructure", columnList = "data_structure_id"),
      @Index(name = "idx_assignment_datasource", columnList = "data_source_id"),
      @Index(name = "idx_assignment_dataset", columnList = "dataset_id"),
      @Index(name = "idx_assignment_dataspace", columnList = "data_space_id"),
      @Index(name = "idx_assignment_catalog", columnList = "catalog_id"),
      @Index(
          name = "idx_assignment_scope",
          columnList =
              "scope_type, data_structure_id, data_source_id, dataset_id, data_space_id,"
                  + " catalog_id")
    })
@Getter
@Setter
public class Assignment extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "group_id", nullable = false)
  private Group group;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "scope_type")
  private ScopeType scopeType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_structure_id")
  private DataStructure dataStructure;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_source_id")
  private DataSource dataSource;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id")
  private DataSet dataset;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "data_space_id")
  private DataSpace dataSpace;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "catalog_id")
  private Catalog catalog;

  public NamedEntity getScope() {
    return Stream.of(dataStructure, dataSource, dataset, dataSpace, catalog)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @PrePersist
  private void validateBeforePersist() {
    validateScope();
    validateRoleType();
  }

  protected void validateScope() {
    if (scopeType == null || scopeType == ScopeType.TENANT) {
      // no scope entity should be set
      if (dataStructure != null
          || dataSource != null
          || dataset != null
          || dataSpace != null
          || catalog != null) {
        throw new IllegalStateException("No scope entity should be set for SYSTEM or TENANT scope");
      }
    } else {
      // implicit check if more than one scope entity is set
      if (dataStructure != null && scopeType != ScopeType.DATASTRUCTURE) {
        throw new IllegalStateException("DataStructure requires DATASTRUCTURE scope");
      }
      if (dataSource != null && scopeType != ScopeType.DATASOURCE) {
        throw new IllegalStateException("DataSource requires DATASOURCE scope");
      }
      if (dataset != null && scopeType != ScopeType.DATASET) {
        throw new IllegalStateException("DataSet requires DATASET scope");
      }
      if (dataSpace != null && scopeType != ScopeType.DATASPACE) {
        throw new IllegalStateException("DataSpace requires DATASPACE scope");
      }
      if (catalog != null && scopeType != ScopeType.CATALOG) {
        throw new IllegalStateException("Catalog requires DATACATALOGUE scope");
      }
    }
  }

  private void validateRoleType() {
    if (scopeType == null && role.getRoleType() != RoleType.SYSTEM) {
      throw new IllegalStateException("Only SYSTEM roles can have null scope");
    }
    if (scopeType != null && role.getRoleType() == RoleType.SYSTEM) {
      throw new IllegalStateException("SYSTEM roles cannot have scope");
    }
  }
}

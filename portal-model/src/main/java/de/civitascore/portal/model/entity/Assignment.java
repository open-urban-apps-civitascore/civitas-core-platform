package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.base.BaseEntity;
import de.civitascore.portal.model.entity.base.NamedEntity;
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
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;

/**
 * Links a {@link Group} to a {@link Role} with an optional {@link ScopeType scope}, forming the
 * core of the authorization model. System roles have no scope; data/governance roles are scoped to
 * a specific entity such as a {@link DataSet} or {@link DataSource}.
 */
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

  /**
   * Returns the scoped entity (e.g. {@link DataStructure}, {@link DataSource}, {@link DataSet},
   * {@link DataSpace}, or {@link Catalog}) associated with this assignment, or {@code null} for
   * unscoped (system/tenant) assignments.
   *
   * @return the scope entity, or {@code null}
   */
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

  /**
   * Validates that the scope entity is consistent with the {@link #scopeType}. For example, a
   * {@link ScopeType#TENANT} assignment must not reference any scope entity.
   *
   * @throws IllegalStateException if the scope configuration is invalid
   */
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

  // Convenience setters used by BaseDataEntity.linkAssignment implementations.
  // Each overload sets both the FK reference and the scopeType atomically.

  /**
   * Sets the scope to the given {@link DataStructure} and updates the {@link #scopeType}
   * accordingly.
   *
   * @param scope the data structure to scope this assignment to
   */
  public void setScope(DataStructure scope) {
    this.dataStructure = scope;
    this.scopeType = ScopeType.DATASTRUCTURE;
  }

  /**
   * Sets the scope to the given {@link DataSet} and updates the {@link #scopeType} accordingly.
   *
   * @param scope the dataset to scope this assignment to
   */
  public void setScope(DataSet scope) {
    this.dataset = scope;
    this.scopeType = ScopeType.DATASET;
  }

  /**
   * Sets the scope to the given {@link DataSource} and updates the {@link #scopeType} accordingly.
   *
   * @param scope the data source to scope this assignment to
   */
  public void setScope(DataSource scope) {
    this.dataSource = scope;
    this.scopeType = ScopeType.DATASOURCE;
  }

  /**
   * Sets the scope to the given {@link DataSpace} and updates the {@link #scopeType} accordingly.
   *
   * @param scope the dataspace to scope this assignment to
   */
  public void setScope(DataSpace scope) {
    this.dataSpace = scope;
    this.scopeType = ScopeType.DATASPACE;
  }

  /**
   * Sets the scope to the given {@link Catalog} and updates the {@link #scopeType} accordingly.
   *
   * @param scope the catalog to scope this assignment to
   */
  public void setScope(Catalog scope) {
    this.catalog = scope;
    this.scopeType = ScopeType.CATALOG;
  }
}

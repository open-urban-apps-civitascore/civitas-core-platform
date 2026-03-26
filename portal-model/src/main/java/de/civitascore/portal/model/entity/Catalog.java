package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a data catalog that organizes {@link DataSet DataSets} into a hierarchical structure
 * of parent and child catalogs.
 */
@Entity
@Table(name = "catalogs")
@Getter
@Setter
public class Catalog extends NamedEntity {

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "catalog_children",
      joinColumns = @JoinColumn(name = "parent_catalog_id"),
      inverseJoinColumns = @JoinColumn(name = "child_catalog_id"),
      indexes = {
        @Index(name = "idx_catalog_children_parent", columnList = "parent_catalog_id"),
        @Index(name = "idx_catalog_children_child", columnList = "child_catalog_id")
      })
  private Set<Catalog> childCatalogs = new HashSet<>();

  @ManyToMany(mappedBy = "childCatalogs", fetch = FetchType.LAZY)
  private Set<Catalog> parentCatalogs = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "catalog_datasets",
      joinColumns = @JoinColumn(name = "catalog_id"),
      inverseJoinColumns = @JoinColumn(name = "dataset_id"),
      indexes = {
        @Index(name = "idx_catalog_datasets_catalog", columnList = "catalog_id"),
        @Index(name = "idx_catalog_datasets_dataset", columnList = "dataset_id")
      })
  private Set<DataSet> dataSets = new HashSet<>();

  @OneToMany(mappedBy = "catalog", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();
}

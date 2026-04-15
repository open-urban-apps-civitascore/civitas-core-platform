package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a dataspace that acts as a logical container for {@link DataSet DataSets}. DataSpaces
 * can be organized hierarchically through parent-child relationships.
 */
@Entity
@Table(
    name = "data_spaces",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_dataspace_name",
            columnNames = {"name"}),
    indexes = {@Index(name = "idx_dataspace_owner", columnList = "owner_user_id")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataSpace extends NamedEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User owner;

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "dataSpaces")
  @Builder.Default
  private Set<DataSet> dataSets = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_dataspace_id")
  private DataSpace parentDataSpace;

  @OneToMany(
      mappedBy = "parentDataSpace",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE) // Custom setter needed for orphanRemoval
  @Builder.Default
  private Set<DataSpace> childDataSpaces = new HashSet<>();

  /**
   * Custom setter for childDataSpaces to properly handle orphanRemoval. Hibernate requires that the
   * collection instance remains the same, while only its contents are modified.
   */
  public void setChildDataSpaces(Collection<DataSpace> childDataSpaces) {
    this.childDataSpaces.clear();
    if (childDataSpaces != null) {
      this.childDataSpaces.addAll(childDataSpaces);
    }
  }

  @Column(name = "external_id")
  private String externalId;

  @OneToMany(mappedBy = "dataSpace", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();
}

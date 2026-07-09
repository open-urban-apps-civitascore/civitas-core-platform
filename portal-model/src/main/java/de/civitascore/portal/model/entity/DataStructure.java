package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a data structure definition that contains one or more {@link DataStructureVersion
 * versions}. Tracks its own lifecycle status and whether it was auto-created from a data source.
 *
 * @see DataStructureStatus
 */
@Entity
@Table(name = "data_structures")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataStructure extends BaseDataEntity {

  /**
   * Custom builder impl: routes {@code dataStructureVersions} through {@link
   * #setDataStructureVersions(Set)} so that back-references are set on each version.
   */
  static final class DataStructureBuilderImpl
      extends DataStructureBuilder<DataStructure, DataStructureBuilderImpl> {

    @Override
    public DataStructure build() {
      DataStructure instance = this.buildInternal();
      if (instance.dataStructureVersions != null && !instance.dataStructureVersions.isEmpty()) {
        Set<DataStructureVersion> versions = new HashSet<>(instance.dataStructureVersions);
        instance.dataStructureVersions.clear();
        instance.setDataStructureVersions(versions);
      }
      return instance;
    }

    private DataStructure buildInternal() {
      return new DataStructure(this);
    }
  }

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_status", nullable = false)
  @Builder.Default
  private DataStructureStatus dataStructureStatus = DataStructureStatus.DRAFT;

  @Column(name = "created_from_data_source", nullable = false)
  @Builder.Default
  private Boolean createdFromDataSource = false;

  @Setter(AccessLevel.NONE)
  @OneToMany(
      mappedBy = "dataStructure",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();

  @OneToMany(
      mappedBy = "dataStructure",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  private Set<DataStructureVersion> dataStructureVersions = new HashSet<>();

  /** {@inheritDoc} Links the assignment to this data structure by setting its scope. */
  @Override
  protected void linkAssignment(Assignment assignment) {
    assignment.setScope(this);
  }

  /**
   * Replaces the current versions with the provided set and sets the back-reference on each
   * version. Clears then re-adds to satisfy Hibernate orphan-removal semantics.
   *
   * @param dataStructureVersions the new set of versions, or {@code null} to clear
   */
  public void setDataStructureVersions(Set<DataStructureVersion> dataStructureVersions) {
    this.dataStructureVersions.clear();
    if (dataStructureVersions != null) {
      this.dataStructureVersions.addAll(dataStructureVersions);
      for (DataStructureVersion dataStructureVersion : dataStructureVersions) {
        dataStructureVersion.setDataStructure(this);
      }
    }
  }
}

package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "data_structures")
@Getter
@Setter
public class DataStructure extends BaseDataEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_status", nullable = false)
  private DataStructureStatus dataStructureStatus;

  @Column(name = "created_from_data_source", nullable = false)
  private Boolean createdFromDataSource = false;

  @Setter(AccessLevel.NONE)
  @OneToMany(
      mappedBy = "dataStructure",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<Assignment> assignments = new HashSet<>();

  @OneToMany(
      mappedBy = "dataStructure",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<DataStructureVersion> dataStructureVersions = new HashSet<>();

  @Override
  protected void linkAssignment(Assignment assignment) {
    assignment.setScope(this);
  }

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

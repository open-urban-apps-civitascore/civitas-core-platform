package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "data_structures")
@Getter
@Setter
public class DataStructure extends NamedEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "data_structure_status", nullable = false)
  private DataStructureStatus dataStructureStatus;

  @Column(name = "created_from_data_source", nullable = false)
  private Boolean createdFromDataSource = false;

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

  public void setAssignments(Set<Assignment> assignments) {
    this.assignments.clear();
    if (assignments != null) {
      this.assignments.addAll(assignments);
      for (Assignment assignment : assignments) {
        assignment.setDataStructure(this);
      }
    }
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

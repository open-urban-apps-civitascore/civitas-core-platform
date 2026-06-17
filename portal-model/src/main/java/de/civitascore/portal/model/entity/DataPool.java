package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.BaseDataEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Represents a data pool that groups related {@link DataSet DataSets} under a common governance
 * boundary.
 */
@Entity
@Table(
    name = "datapools",
    indexes = {@Index(name = "idx_datapool_contact_person", columnList = "contact_person_user_id")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class DataPool extends BaseDataEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "contact_person_user_id")
  private User contactPerson;

  @OneToMany(
      mappedBy = "dataPool",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();

  @Override
  protected void linkAssignment(final Assignment assignment) {
    assignment.setScope(this);
  }
}

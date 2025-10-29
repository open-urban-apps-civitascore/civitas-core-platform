package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "datasets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_dataset_name_tenant",
            columnNames = {"title", "tenant_id"}),
    indexes = {
      @Index(name = "idx_dataset_owner", columnList = "owner_user_id"),
      @Index(name = "idx_dataset_external_id", columnList = "external_id")
    })
@Getter
@Setter
public class DataSet extends NamedEntity<String> {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User owner;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dataset_dataspaces",
      joinColumns = @JoinColumn(name = "dataset_id"),
      inverseJoinColumns = @JoinColumn(name = "dataspace_id"))
  private Set<DataSpace> dataSpaces = new HashSet<>();

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "format")
  private String format;
}

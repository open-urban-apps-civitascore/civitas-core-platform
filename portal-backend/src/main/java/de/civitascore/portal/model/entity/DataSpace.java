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
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "data_spaces",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_dataspace_name_tenant",
            columnNames = {"title", "tenant_id"}),
    indexes = {@Index(name = "idx_dataspace_owner", columnList = "owner_user_id")})
@Getter
@Setter
public class DataSpace extends NamedEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User owner;

  @ManyToMany(fetch = FetchType.LAZY, mappedBy = "dataSpaces")
  private Set<DataSet> dataSets = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_dataspace_id")
  private DataSpace parentDataSpace;

  @OneToMany(
      mappedBy = "parentDataSpace",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<DataSpace> childDataSpaces = new HashSet<>();

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "metadata", columnDefinition = "TEXT")
  private String metadata;
}

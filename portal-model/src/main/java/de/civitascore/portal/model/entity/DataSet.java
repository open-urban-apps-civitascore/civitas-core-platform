package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "datasets",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_dataset_name",
            columnNames = {"name"}),
    indexes = {
      @Index(name = "idx_dataset_owner", columnList = "owner_user_id"),
      @Index(name = "idx_dataset_external_id", columnList = "external_id"),
      @Index(name = "idx_dataset_series", columnList = "dataset_series_id")
    })
@Getter
@Setter
public class DataSet extends NamedEntity {

  /** Status of the dataset in its lifecycle. Default is DRAFT. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DataSetStatus dataSetStatus = DataSetStatus.DRAFT;

  /** Master persistence ID (FROST ID). Required for publishing the dataset. */
  @Column(name = "persistence_id")
  private Long persistenceId;

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<Pipeline> pipelines = new HashSet<>();

  @Column(name = "identifier")
  private String identifier;

  @Column(name = "version")
  private String version;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_series_id")
  private DataSetSeries dataSetSeries;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dataset_dataspaces",
      joinColumns = @JoinColumn(name = "dataset_id"),
      inverseJoinColumns = @JoinColumn(name = "dataspace_id"),
      indexes = {
        @Index(name = "idx_dataset_dataspaces_dataset", columnList = "dataset_id"),
        @Index(name = "idx_dataset_dataspaces_dataspace", columnList = "dataspace_id")
      })
  private Set<DataSpace> dataSpaces = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "dataset_agents",
      joinColumns = @JoinColumn(name = "dataset_id"),
      inverseJoinColumns = @JoinColumn(name = "agent_id"),
      indexes = {
        @Index(name = "idx_dataset_agents_dataset", columnList = "dataset_id"),
        @Index(name = "idx_dataset_agents_agent", columnList = "agent_id")
      })
  private Set<Agent> agents = new HashSet<>();

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Set<Distribution> distributions = new HashSet<>();

  @ManyToMany(mappedBy = "dataSets", fetch = FetchType.LAZY)
  private Set<Catalog> catalogs = new HashSet<>();

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "format")
  private String format;

  @Column(name = "open_data_access", nullable = false)
  private Boolean openDataAccess = false;

  @OneToMany(mappedBy = "dataset", fetch = FetchType.LAZY)
  private Set<Assignment> assignments = new HashSet<>();

  public void setDistributions(Collection<Distribution> newDistributions) {
    this.distributions.clear();
    Optional.ofNullable(newDistributions).ifPresent(distributions::addAll);
  }

  public void setPipelines(Collection<Pipeline> newPipelines) {
    this.pipelines.clear();
    Optional.ofNullable(newPipelines).ifPresent(pipelines::addAll);
  }
}

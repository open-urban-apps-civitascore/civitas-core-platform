package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
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
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a dataset with its lifecycle status, infrastructure references, and relationships to
 * {@link Pipeline Pipelines}, {@link Distribution Distributions}, and {@link DataSpace DataSpaces}.
 *
 * @see DataSetStatus
 */
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
@SuperBuilder
@NoArgsConstructor
public class DataSet extends BaseDataEntity {

  /** Status of the dataset in its lifecycle. Default is DRAFT. */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private DataSetStatus dataSetStatus = DataSetStatus.DRAFT;

  /** Master persistence ID (FROST ID). Required for publishing the dataset. */
  @Column(name = "persistence_id")
  private Long persistenceId;

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
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
  @Builder.Default
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
  @Builder.Default
  private Set<Agent> agents = new HashSet<>();

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  private Set<Distribution> distributions = new HashSet<>();

  @ManyToMany(mappedBy = "dataSets", fetch = FetchType.LAZY)
  @Builder.Default
  private Set<Catalog> catalogs = new HashSet<>();

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "format")
  private String format;

  @Column(name = "open_data_access", nullable = false)
  @Builder.Default
  private Boolean openDataAccess = false;

  @Column(name = "project_id")
  private String projectId;

  @Column(name = "frost_base_url", length = 500)
  private String frostBaseUrl;

  @Column(name = "route_id")
  private String routeId;

  @Column(name = "service_id")
  private String serviceId;

  @Column(name = "public_url", length = 500)
  private String publicUrl;

  @Column(name = "pipeline_ids", columnDefinition = "text[]")
  private List<String> pipelineIds;

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_saga_type", length = 30)
  private PendingSagaType pendingSagaType;

  @OneToMany(
      mappedBy = "dataset",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Assignment> assignments = new HashSet<>();

  /** {@inheritDoc} Links the assignment to this dataset by setting its scope. */
  @Override
  protected void linkAssignment(Assignment assignment) {
    assignment.setScope(this);
  }

  /**
   * Replaces the current distributions with the provided collection, clearing then re-adding to
   * satisfy Hibernate orphan-removal semantics.
   *
   * @param newDistributions the new distributions, or {@code null} to clear
   */
  public void setDistributions(Collection<Distribution> newDistributions) {
    this.distributions.clear();
    if (newDistributions != null) {
      this.distributions.addAll(newDistributions);
    }
  }

  /**
   * Replaces the current pipelines with the provided collection, clearing then re-adding to satisfy
   * Hibernate orphan-removal semantics.
   *
   * @param newPipelines the new pipelines, or {@code null} to clear
   */
  public void setPipelines(Collection<Pipeline> newPipelines) {
    this.pipelines.clear();
    if (newPipelines != null) {
      this.pipelines.addAll(newPipelines);
    }
  }

  /**
   * Resets all infrastructure-related fields (projectId, frostBaseUrl, routeId, serviceId,
   * publicUrl, pipelineIds) to {@code null}, typically called during unpublish.
   */
  public void clearInfrastructureFields() {
    this.projectId = null;
    this.frostBaseUrl = null;
    this.routeId = null;
    this.serviceId = null;
    this.publicUrl = null;
    this.pipelineIds = null;
  }
}

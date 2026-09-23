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
 * {@link Pipeline Pipelines} and {@link Distribution Distributions}.
 *
 * @see DataSetStatus
 */
@Entity
@Table(
    name = "datasets",
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

  /**
   * Logical CORE URN of this DataSet's manifest artifact in Model Forge — the manifest its member
   * artifacts (Pipelines and, transitively, their sources/sinks/mappings/structures) are linked
   * into. Null until the manifest is created on dataset creation.
   */
  @Column(name = "manifest_logical_urn")
  private String manifestLogicalUrn;

  /** Versioned CORE URN pin of the DataSet manifest's current version. */
  @Column(name = "manifest_urn")
  private String manifestUrn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "datapool_id")
  private DataPool dataPool;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_series_id")
  private DataSetSeries dataSetSeries;

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

  /**
   * Distribution rows linked to this dataset. Currently has no production writer — the entity is
   * reserved for the deferred DCAT distribution work. Existing rows are preserved by cascade
   * delete; the dormancy is documented on {@link Distribution} itself.
   */
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

  @Column(name = "service_id")
  private String serviceId;

  @Column(name = "public_url", length = 500)
  private String publicUrl;

  @Column(name = "pipeline_ids", columnDefinition = "text[]")
  private List<String> pipelineIds;

  /**
   * Named API endpoints exposed by this dataset. Each entry produces one APISIX route after
   * release. Slug uniqueness within the dataset is enforced by a DB unique constraint.
   */
  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  private Set<NamedApi> namedApis = new HashSet<>();

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Layer> layers = new HashSet<>();

  @OneToMany(
      mappedBy = "dataSet",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  @Builder.Default
  private Set<Style> styles = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "pending_saga_type", length = 30)
  private PendingSagaType pendingSagaType;

  /**
   * Whether a provisioning saga has completed for this dataset, so whatever sinks it carried at
   * release physically exist (PostGIS table / FROST project). Set on any provisioning saga's
   * completion regardless of which sinks the dataset has, and left untouched by unrelease — the
   * sinks survive it. (There is no reset path: the row is removed on DELETE-saga completion.)
   * Distinguishes "never released, nothing provisioned yet" from "infrastructure exists, holds
   * data" so a destructive sink edit only warns once data is actually at risk, and so a delete
   * knows a teardown saga is required.
   */
  @Column(name = "provisioned", nullable = false)
  private boolean provisioned = false;

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
   * Replaces the current named APIs with the provided collection, clearing then re-adding to
   * satisfy Hibernate orphan-removal semantics. Each entry's {@link NamedApi#getDataSet()} is set
   * to this dataset.
   *
   * @param newNamedApis the new named APIs, or {@code null} to clear
   */
  public void setNamedApis(Collection<NamedApi> newNamedApis) {
    this.namedApis.clear();
    if (newNamedApis != null) {
      newNamedApis.forEach(api -> api.setDataSet(this));
      this.namedApis.addAll(newNamedApis);
    }
  }

  /**
   * Replaces the current layers with the provided collection, clearing then re-adding to satisfy
   * Hibernate orphan-removal semantics.
   *
   * @param newLayers the new layers, or {@code null} to clear
   */
  public void setLayers(Collection<Layer> newLayers) {
    this.layers.clear();
    if (newLayers != null) {
      this.layers.addAll(newLayers);
    }
  }

  /**
   * Replaces the current styles with the provided collection, clearing then re-adding to satisfy
   * Hibernate orphan-removal semantics.
   *
   * @param newStyles the new styles, or {@code null} to clear
   */
  public void setStyles(Collection<Style> newStyles) {
    this.styles.clear();
    if (newStyles != null) {
      this.styles.addAll(newStyles);
    }
  }

  /**
   * Clears the ingest and consumer-access infrastructure torn down by an unrelease: the pipeline
   * ids, the per-named-API {@code routeId}, and the APISIX access fields {@code serviceId} (the
   * per-dataset upstream, deleted by {@code DELETE_ROUTE}) and {@code publicUrl} (the gateway
   * endpoint, unreachable once the route is gone). The two data-holding sink references {@code
   * projectId} and {@code frostBaseUrl} are deliberately left intact — the FROST project survives
   * an unrelease and is reused on re-release. The named-API entries themselves are preserved (they
   * are user-authored). The {@code namedApis} collection must be initialized before this is called.
   */
  public void clearRouteAndPipelineInfrastructure() {
    this.pipelineIds = null;
    this.serviceId = null;
    this.publicUrl = null;
    this.namedApis.forEach(api -> api.setRouteId(null));
  }
}

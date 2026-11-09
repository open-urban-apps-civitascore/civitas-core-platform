package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Represents the output destination of a {@link Pipeline}. A DataSink describes where the pipeline
 * writes its processed data.
 *
 * <p>The type-specific configuration document lives in the Model Forge registry, pinned by {@link
 * #configurationUrn}. Its required shape depends on {@code dataSinkType}:
 *
 * <ul>
 *   <li>{@link DataSinkType#POSTGIS}: {@code tableName} and {@code element} are both required.
 *   <li>{@link DataSinkType#FROST}: the configuration is either empty (passthrough) or carries
 *       {@code element} alone.
 * </ul>
 *
 * <p>A DataSink belongs directly to a {@link DataSet}. Its association with {@link Pipeline}s is
 * optional and only set once a Pipeline declares the DataSink in its {@code dataSinkIds} list.
 *
 * @see DataSinkType
 */
@Entity
@Table(name = "data_sinks")
@Getter
@Setter
public class DataSink extends DataSetOwnedEntity {

  /**
   * A list response reads the Pipelines of each DataSink; the batch size loads them for many
   * DataSinks in one query.
   */
  @ManyToMany(fetch = FetchType.LAZY)
  @BatchSize(size = 100)
  @JoinTable(
      name = "pipeline_data_sinks",
      joinColumns = @JoinColumn(name = "data_sink_id"),
      inverseJoinColumns = @JoinColumn(name = "pipeline_id"))
  @Setter(AccessLevel.NONE)
  private Set<Pipeline> pipelines = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "data_sink_type", nullable = false, length = 20)
  @NotNull private DataSinkType dataSinkType;

  /**
   * Logical CORE URN of this sink's configuration artifact in Model Forge. Null until a
   * configuration is stored.
   */
  @Column(name = "configuration_logical_urn")
  private String configurationLogicalUrn;

  /**
   * Versioned CORE URN pinning the current configuration document in Model Forge. Null while no
   * configuration is stored.
   */
  @Column(name = "configuration_urn")
  private String configurationUrn;

  /**
   * Set by a completed provisioning saga and never reset: the resource survives an unrelease, and
   * the row goes when the sink or its dataset is deleted.
   */
  @Column(name = "provisioned", nullable = false)
  private boolean provisioned = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 32)
  @Setter(AccessLevel.NONE)
  @NotNull private DataSinkState state = DataSinkState.ACTIVE;

  /** The PostGIS table the next release drops; set only while the sink waits for it. */
  @Column(name = "unprovision_table_name", length = 63)
  @Setter(AccessLevel.NONE)
  private String unprovisionTableName;

  /** A Layer's {@code datasink_id} is non-null, so layers cannot outlive their sink. */
  @OneToMany(
      mappedBy = "dataSink",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  private Set<Layer> layers = new HashSet<>();

  public boolean isActive() {
    return state == DataSinkState.ACTIVE;
  }

  /**
   * Keeps only what the next release needs to remove the storage, and drops the link to the
   * configuration in Model Forge: that configuration names the sink's Data structure and would keep
   * it in use. The caller deletes the configuration itself.
   *
   * @param tableName the PostGIS table the release drops; {@code null} for a sink without a table
   */
  public void markPendingUnprovision(String tableName) {
    state = DataSinkState.PENDING_UNPROVISION;
    unprovisionTableName = tableName;
    configurationUrn = null;
    configurationLogicalUrn = null;
    pipelines.clear();
  }
}

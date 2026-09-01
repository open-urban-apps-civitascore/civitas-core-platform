package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.base.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents the output destination of a {@link Pipeline}. A DataSink describes where the pipeline
 * writes its processed data.
 *
 * <p>The type-specific configuration document lives in the Model Forge registry, pinned by {@link
 * #configurationUrn}. Its required shape depends on {@code dataSinkType}:
 *
 * <ul>
 *   <li>{@link DataSinkType#POSTGIS}: {@code tableName} and {@code dataStructureVersionId} are
 *       required.
 *   <li>{@link DataSinkType#FROST}: the configuration must be absent or empty — nothing is stored
 *       in the registry and both URN columns stay null.
 * </ul>
 *
 * <p>A DataSink belongs directly to a {@link DataSet}. Its association with a {@link Pipeline} is
 * optional and only set once a Pipeline declares the DataSink in its {@code dataSinkIds} list.
 *
 * @see DataSinkType
 */
@Entity
@Table(name = "data_sinks")
@Getter
@Setter
public class DataSink extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  @NotNull private DataSet dataSet;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "pipeline_id")
  private Pipeline pipeline;

  @Enumerated(EnumType.STRING)
  @Column(name = "data_sink_type", nullable = false, length = 20)
  @NotNull private DataSinkType dataSinkType;

  /**
   * Stable logical CORE URN of this sink's configuration artifact in Model Forge, minted once on
   * the first store and reused for every following version. Null until a configuration is stored
   * (always null for FROST sinks).
   */
  @Column(name = "configuration_logical_urn")
  private String configurationLogicalUrn;

  /**
   * Versioned CORE URN pinning the current configuration document in Model Forge. Null while no
   * configuration is stored (always null for FROST sinks).
   */
  @Column(name = "configuration_urn")
  private String configurationUrn;

  /** A Layer's {@code datasink_id} is non-null, so layers cannot outlive their sink. */
  @OneToMany(
      mappedBy = "dataSink",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Setter(AccessLevel.NONE)
  private Set<Layer> layers = new HashSet<>();
}

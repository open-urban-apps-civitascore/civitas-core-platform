package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a data ingestion pipeline belonging to a {@link DataSet}. The pipeline is a thin
 * shell: name, dataset FK and datasource links live here, while the editor-built pipeline
 * definition (including the React Flow layout under the {@code x-ui-styles} keyword) lives in the
 * Model Forge registry, pinned by {@link #modelUrn}.
 */
@Entity
@Table(
    name = "pipelines",
    indexes = {@Index(name = "idx_pipeline_dataset", columnList = "dataset_id")})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Pipeline extends DataSetOwnedEntity {

  @NotBlank @Column(nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  /**
   * Stable logical CORE URN of this pipeline's definition artifact in Model Forge, minted once on
   * the first store and reused for every following version. Null until a definition is stored.
   */
  @Column(name = "model_logical_urn")
  private String modelLogicalUrn;

  /**
   * Versioned CORE URN pinning the current pipeline definition (editor-built graph plus the React
   * Flow layout under {@code x-ui-styles}) in Model Forge. Null while no definition is stored.
   */
  @Column(name = "model_urn")
  private String modelUrn;

  /** Data sources associated with this pipeline. */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "pipeline_data_sources",
      joinColumns = @JoinColumn(name = "pipeline_id"),
      inverseJoinColumns = @JoinColumn(name = "data_source_id"))
  @Builder.Default
  private Set<DataSource> dataSources = new HashSet<>();

  /** Auto-incremented on each update, passed through to saga triggers. */
  @Column(name = "version", nullable = false)
  @Builder.Default
  private long version = 1L;

  // Eager by necessity: the parent side of a @OneToOne can only be lazy with bytecode
  // enhancement, which this project does not run — declaring LAZY here would be misleading. The
  // dataset overview fetches it via an @EntityGraph anyway.
  @OneToOne(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true)
  private PipelineRuntimeStatus runtimeStatus;
}

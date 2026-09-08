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
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Represents a data ingestion pipeline belonging to a {@link DataSet}. Holds the editor-built
 * pipeline definition and the associated {@link DataSource DataSources}.
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

  /** React Flow visual layout stored as JSON (nodes/edges/viewport). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "styles", columnDefinition = "jsonb")
  private Map<String, Object> styles;

  /** Data sources associated with this pipeline. */
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "pipeline_data_sources",
      joinColumns = @JoinColumn(name = "pipeline_id"),
      inverseJoinColumns = @JoinColumn(name = "data_source_id"))
  @Builder.Default
  private Set<DataSource> dataSources = new HashSet<>();

  /** Editor-built pipeline definition (opaque JSON); forwarded to the config-adapter as-is. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model", columnDefinition = "jsonb")
  private Map<String, Object> model;

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

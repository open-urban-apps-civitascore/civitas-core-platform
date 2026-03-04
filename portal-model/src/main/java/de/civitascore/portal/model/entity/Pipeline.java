package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "pipelines",
    indexes = {@Index(name = "idx_pipeline_dataset", columnList = "dataset_id")})
@Getter
@Setter
public class Pipeline extends NamedEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  private DataSet dataSet;

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
  private Set<DataSource> dataSources = new HashSet<>();

  /** Array of API paths (e.g., ["/api/v1/traffic"]). Used to auto-generate Distribution entries. */
  @Column(name = "apis", columnDefinition = "text[]")
  private List<String> apis;

  /** Array of persistence IDs. Must only contain the Master ID (persistenceId from DataSet). */
  @Column(name = "persistences", columnDefinition = "bigint[]")
  private List<Long> persistences;

  /** Executable RedpandaConnect configuration in JSON/YAML format. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "model", columnDefinition = "jsonb")
  private Map<String, Object> model;
}

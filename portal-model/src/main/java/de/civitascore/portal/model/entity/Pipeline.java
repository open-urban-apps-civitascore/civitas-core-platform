package de.civitascore.portal.model.entity;

import de.civitascore.portal.model.entity.base.NamedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(
    name = "pipelines",
    indexes = {
      @Index(name = "idx_pipeline_dataset", columnList = "dataset_id")
    })
@Getter
@Setter
public class Pipeline extends NamedEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "dataset_id", nullable = false)
  private DataSet dataSet;

  /**
   * React Flow visual layout stored as JSON (nodes/edges/viewport).
   */
  @Column(name = "styles", columnDefinition = "TEXT")
  private String styles;

  /**
   * Array of data source IDs from DataSource nodes.
   */
  @Column(name = "data_sources", columnDefinition = "bigint[]")
  private List<Long> dataSources;

  /**
   * Array of API paths (e.g., ["/api/v1/traffic"]).
   * Used to auto-generate Distribution entries.
   */
  @Column(name = "apis", columnDefinition = "text[]")
  private List<String> apis;

  /**
   * Array of persistence IDs. Must only contain the Master ID (persistenceId from DataSet).
   */
  @Column(name = "persistences", columnDefinition = "bigint[]")
  private List<Long> persistences;

  /**
   * Executable RedpandaConnect configuration in JSON/YAML format.
   */
  @Column(name = "model", columnDefinition = "TEXT")
  private String model;
}


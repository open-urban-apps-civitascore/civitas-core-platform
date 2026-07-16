package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Pipeline;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Pipeline} entities. */
@Repository
public interface PipelineRepository extends NamedEntityRepository<Pipeline, UUID> {

  /**
   * Find a pipeline by ID with related entities eagerly fetched. This prevents N+1 query problems
   * when loading pipelines with their relationships.
   *
   * @param id the pipeline ID
   * @return the pipeline with eagerly fetched dataSet and dataSources
   */
  @EntityGraph(attributePaths = {"dataSet", "dataSources"})
  @Override
  @NonNull Optional<Pipeline> findById(@NonNull UUID id);

  /**
   * Find an entity by name and dataset ID.
   *
   * @param name the entity name
   * @param datasetId the dataset ID
   * @return the entity if found
   */
  Set<Pipeline> findAllByNameAndDataSetId(String name, UUID datasetId);

  /**
   * Check if any pipeline references the given datasource ID.
   *
   * @param dataSourceId the datasource ID
   * @return true if any pipeline references the datasource
   */
  boolean existsByDataSourcesId(UUID dataSourceId);

  /**
   * Find every pipeline referencing the given datasource, with each pipeline's parent dataset, that
   * dataset's datapool, and the datasource set eagerly fetched — so the DataPool scope rule can be
   * re-validated across all datasets a datasource feeds when the datasource's own scope is
   * narrowed, without a lazy-load per pipeline.
   *
   * @param dataSourceId the datasource ID
   * @return the referencing pipelines
   */
  @EntityGraph(attributePaths = {"dataSet", "dataSet.dataPool", "dataSources"})
  List<Pipeline> findByDataSourcesId(UUID dataSourceId);
}

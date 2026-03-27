package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSet} entities. */
@Repository
public interface DataSetRepository extends NamedEntityRepository<DataSet, UUID> {

  /**
   * Find a dataset by ID with related entities eagerly fetched. This prevents N+1 query problems
   * when loading datasets with their relationships.
   *
   * @param id the dataset ID
   * @return the dataset with eagerly fetched owner
   */
  @EntityGraph(attributePaths = {"owner", "pipelines", "distributions"})
  @Query("SELECT d FROM DataSet d WHERE d.id = :id")
  Optional<DataSet> findByIdWithRelations(@Param("id") UUID id);

  /**
   * Check if any dataset with the given statuses references the specified data source via its
   * pipelines.
   *
   * @param dataSourceId the data source ID to check
   * @param statuses the dataset statuses to include in the check
   * @return true if at least one matching dataset exists
   */
  boolean existsByPipelinesDataSourcesIdAndDataSetStatusIn(
      UUID dataSourceId, Collection<DataSetStatus> statuses);

  /**
   * Find a dataset by ID with pipelines and their data sources eagerly fetched. Used for saga
   * trigger publishing where the full pipeline-datasource graph is needed.
   *
   * @param id the dataset ID
   * @return the dataset with eagerly fetched owner, pipelines, pipeline data sources, and
   *     distributions
   */
  @EntityGraph(attributePaths = {"owner", "pipelines", "pipelines.dataSources", "distributions"})
  @Query("SELECT d FROM DataSet d WHERE d.id = :id")
  Optional<DataSet> findByIdWithPipelineDataSources(@Param("id") UUID id);
}

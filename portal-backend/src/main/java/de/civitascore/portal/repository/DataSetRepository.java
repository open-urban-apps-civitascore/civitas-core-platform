package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSet} entities. */
@Repository
public interface DataSetRepository extends NamedEntityRepository<DataSet, UUID> {

  // pipelines.dataSources is fetched because every dataset write re-asserts the DataSource→DataPool
  // scope rule across the existing pipelines; without it that check costs one lazy select per
  // pipeline.
  @EntityGraph(
      attributePaths = {
        "owner",
        "pipelines",
        "pipelines.runtimeStatus",
        "pipelines.dataSources",
        "distributions",
        "namedApis"
      })
  @Override
  @NonNull Optional<DataSet> findById(@NonNull UUID id);

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

  /** Variant for saga trigger publishing: also fetches {@code pipelines.dataSources}. */
  @EntityGraph(
      attributePaths = {
        "owner",
        "pipelines",
        "pipelines.dataSources",
        "distributions",
        "namedApis"
      })
  @Query("SELECT d FROM DataSet d WHERE d.id = :id")
  Optional<DataSet> findByIdWithPipelineDataSources(@Param("id") UUID id);

  /**
   * Find all datasets assigned to a specific datapool.
   *
   * @param dataPoolId the datapool ID
   * @return all datasets assigned to the datapool
   */
  List<DataSet> findAllByDataPoolId(UUID dataPoolId);

  /**
   * Check if any dataset is assigned to the given datapool.
   *
   * @param dataPoolId the datapool ID
   * @return true if at least one dataset is assigned
   */
  boolean existsByDataPoolId(UUID dataPoolId);

  /** Whether each dataset has the given status, keyed by its manifest's logical URN. */
  @Query(
      "SELECT new de.civitascore.portal.repository.ReferrerReleaseState(d.manifestLogicalUrn,"
          + " CASE WHEN d.dataSetStatus = :status THEN true ELSE false END)"
          + " FROM DataSet d WHERE d.manifestLogicalUrn IN :urns")
  List<ReferrerReleaseState> findReleaseStatesByManifestLogicalUrnIn(
      @Param("urns") Collection<String> urns, @Param("status") DataSetStatus status);
}

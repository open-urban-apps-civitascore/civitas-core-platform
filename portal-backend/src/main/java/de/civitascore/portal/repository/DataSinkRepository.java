package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSink;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSink} entities. */
@Repository
public interface DataSinkRepository extends BaseRepository<DataSink, UUID> {

  /**
   * Find a DataSink by ID with related entities eagerly fetched, preventing N+1 queries.
   *
   * @param id the DataSink ID
   * @return the DataSink with eagerly fetched dataSet and pipeline
   */
  @EntityGraph(attributePaths = {"dataSet", "pipeline"})
  @Query("SELECT s FROM DataSink s WHERE s.id = :id")
  Optional<DataSink> findByIdWithRelations(UUID id);

  List<DataSink> findByPipelineId(UUID pipelineId);

  List<DataSink> findByDataSetId(UUID dataSetId);

  /**
   * Check if any data sink references the given data structure version. Sinks (POSTGIS and FROST)
   * store the reference under the {@code dataStructureVersionId} key of their JSONB configuration.
   *
   * @param versionId the data structure version ID to check
   * @return true if at least one data sink references this version
   */
  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM data_sinks"
              + " WHERE (configuration ->> 'dataStructureVersionId')::uuid = :versionId)",
      nativeQuery = true)
  boolean existsByDataStructureVersionId(@Param("versionId") UUID versionId);

  /**
   * Check if any data sink references any of the given data structure version IDs.
   *
   * @param versionIds the collection of data structure version IDs to check; must be non-empty
   * @return true if at least one data sink references any of the given versions
   */
  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM data_sinks"
              + " WHERE (configuration ->> 'dataStructureVersionId')::uuid IN (:versionIds))",
      nativeQuery = true)
  boolean existsByDataStructureVersionIdIn(@Param("versionIds") Collection<UUID> versionIds);
}

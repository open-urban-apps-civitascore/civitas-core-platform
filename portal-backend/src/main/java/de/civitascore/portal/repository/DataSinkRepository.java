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
   * Checks whether any DataSink references the given DataStructureVersion. A sink stores the
   * reference under the shared {@code dataStructureVersionId} key of its JSONB {@code
   * configuration} — POSTGIS sinks require it, FROST sinks may carry it — so the check spans every
   * sink type. The write path validates the id, so a stored value that is not a valid UUID is
   * corruption and surfaces as a query error rather than being silently treated as no reference.
   *
   * @param versionId the DataStructureVersion ID to check
   * @return true if at least one DataSink references this version
   */
  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM data_sinks"
              + " WHERE (configuration ->> 'dataStructureVersionId')::uuid = :versionId)",
      nativeQuery = true)
  boolean existsByDataStructureVersionId(@Param("versionId") UUID versionId);

  /**
   * Checks whether any DataSink references any of the given DataStructureVersion IDs. See {@link
   * #existsByDataStructureVersionId(UUID)} for how the reference is stored.
   *
   * @param versionIds the DataStructureVersion IDs to check; must be non-empty
   * @return true if at least one DataSink references any of the given versions
   */
  @Query(
      value =
          "SELECT EXISTS(SELECT 1 FROM data_sinks"
              + " WHERE (configuration ->> 'dataStructureVersionId')::uuid IN (:versionIds))",
      nativeQuery = true)
  boolean existsByDataStructureVersionIdIn(@Param("versionIds") Collection<UUID> versionIds);
}

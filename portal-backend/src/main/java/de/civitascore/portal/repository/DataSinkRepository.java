package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
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

  boolean existsByDataSetIdAndDataSinkType(UUID dataSetId, DataSinkType dataSinkType);

  /** Whether each sink's Dataset has the given status, keyed by the sink's logical URN. */
  @Query(
      "SELECT new de.civitascore.portal.repository.ReferrerReleaseState(s.configurationLogicalUrn,"
          + " CASE WHEN s.dataSet.dataSetStatus = :status THEN true ELSE false END)"
          + " FROM DataSink s WHERE s.configurationLogicalUrn IN :urns")
  List<ReferrerReleaseState> findReleaseStatesByConfigurationLogicalUrnIn(
      @Param("urns") Collection<String> urns, @Param("status") DataSetStatus status);
}

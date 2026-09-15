package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
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

  // Sink -> DataStructureVersion references are no longer a relational column: a sink carries the
  // version's model URN in its registry-stored configuration ("element" field). The in-use guard
  // therefore asks Model Forge for dependents of the model URN
  // (ModelRegistryGateway#isReferenced) instead of querying this table.
}

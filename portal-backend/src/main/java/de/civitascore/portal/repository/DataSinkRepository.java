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

  // A sink names the version's model in its registry-stored configuration ("element" field) rather
  // than in a relational column, so the in-use guard asks Model Forge what would refuse the model's
  // deletion (ModelRegistryGateway#isReferenced) instead of querying this table.
}

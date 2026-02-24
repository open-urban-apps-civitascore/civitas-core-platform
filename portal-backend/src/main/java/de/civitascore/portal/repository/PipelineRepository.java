package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Pipeline;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Pipeline entity operations. */
@Repository
public interface PipelineRepository extends NamedEntityRepository<Pipeline, UUID> {

  @EntityGraph(attributePaths = {"dataSet", "dataSources"})
  @Query("SELECT p FROM Pipeline p WHERE p.id = :id")
  Optional<Pipeline> findByIdWithRelations(UUID id);

  /**
   * Find an entity by name and dataset ID.
   *
   * @param name the entity name
   * @param datasetId the dataset ID
   * @return the entity if found
   */
  Set<Pipeline> findAllByNameAndDataSetId(String name, UUID datasetId);
}

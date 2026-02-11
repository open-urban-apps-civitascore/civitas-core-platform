package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Pipeline;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Repository for Pipeline entity operations. */
@Repository
public interface PipelineRepository extends NamedEntityRepository<Pipeline, UUID> {

  @EntityGraph(attributePaths = {"dataSet"})
  @Query("SELECT p FROM Pipeline p WHERE p.id = :id")
  Optional<Pipeline> findByIdWithDataSet(UUID id);
}

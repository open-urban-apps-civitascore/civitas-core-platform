package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Activity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityRepository extends NamedEntityRepository<Activity, UUID> {

  /**
   * Find an activity by ID with related entities eagerly fetched. This prevents N+1 query problems
   * when loading activities with their relationships.
   *
   * @param id the activity ID
   * @return the activity with eagerly fetched agents, distributions, and resources
   */
  @EntityGraph(attributePaths = {"agents", "distributions", "resources"})
  @Query("SELECT a FROM Activity a WHERE a.id = :id")
  Optional<Activity> findByIdWithRelations(@Param("id") UUID id);
}

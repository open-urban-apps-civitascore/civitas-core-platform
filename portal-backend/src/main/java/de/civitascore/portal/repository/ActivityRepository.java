package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Activity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Activity} entities. */
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
  @Override
  @NonNull Optional<Activity> findById(@NonNull UUID id);
}

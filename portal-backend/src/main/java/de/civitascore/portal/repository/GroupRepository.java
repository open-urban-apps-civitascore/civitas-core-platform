package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Group} entities. */
@Repository
public interface GroupRepository extends NamedEntityRepository<Group, UUID> {

  /**
   * Find all groups that have not yet been synced to Keycloak, with {@code members} eagerly
   * fetched. The Keycloak catch-up sync reads members (to sync memberships) outside any surrounding
   * transaction, so they must be loaded up front to avoid a {@link
   * org.hibernate.LazyInitializationException}.
   *
   * @return groups without an externalId
   */
  @EntityGraph(attributePaths = {"members"})
  List<Group> findByExternalIdIsNull();

  /**
   * Find all groups that have already been synced to Keycloak, with {@code members} eagerly
   * fetched. Used by the one-shot group-member backfill, which builds each {@code GROUP_UPDATED}
   * payload outside any surrounding transaction; {@code GroupService#buildGroupConfig} reads the
   * members, so fetching them up front avoids a {@link org.hibernate.LazyInitializationException}
   * and a per-group lazy-load.
   *
   * @return groups that already have an externalId
   */
  @EntityGraph(attributePaths = {"members"})
  List<Group> findByExternalIdIsNotNull();

  /**
   * Find a group by ID with related entities eagerly fetched.
   *
   * @param id the group ID
   * @return the group with eagerly fetched contactUser, assignments, and assignment roles
   */
  @EntityGraph(attributePaths = {"contactUser", "assignments", "assignments.role"})
  @Override
  @NonNull Optional<Group> findById(@NonNull UUID id);
}

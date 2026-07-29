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
   * Find all groups that have not yet been synced to Keycloak, with {@code parentGroup} and {@code
   * members} eagerly fetched. The Keycloak catch-up sync traverses the parent chain (for depth
   * ordering and to read the parent's externalId) and reads members (to sync memberships) outside
   * any surrounding transaction, so both associations must be loaded up front to avoid a {@link
   * org.hibernate.LazyInitializationException}.
   *
   * @return groups without an externalId
   */
  @EntityGraph(attributePaths = {"parentGroup", "members"})
  List<Group> findByExternalIdIsNull();

  /**
   * Find a group by ID with related entities eagerly fetched.
   *
   * @param id the group ID
   * @return the group with eagerly fetched contactUser, parentGroup, assignments, and assignment
   *     roles
   */
  @EntityGraph(attributePaths = {"contactUser", "parentGroup", "assignments", "assignments.role"})
  @Override
  @NonNull Optional<Group> findById(@NonNull UUID id);
}

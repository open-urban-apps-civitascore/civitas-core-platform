package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link User} entities. */
@Repository
public interface UserRepository extends BaseRepository<User, UUID> {

  /** Load current group memberships for the authenticated creator's external identity. */
  @EntityGraph(attributePaths = "groups")
  Optional<User> findWithGroupsByExternalId(String externalId);

  /**
   * Find a user by email address.
   *
   * @param email the email address to search for
   * @return the user if found
   */
  Optional<User> findByEmail(String email);

  /**
   * Find a user by their external identity provider ID (e.g., Keycloak subject).
   *
   * @param externalId the external identity provider ID
   * @return the user if found
   */
  Optional<User> findByExternalId(String externalId);
}

package de.civitascore.authz.repository.data;

import de.civitascore.portal.model.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository for user authorization context lookups.
 *
 * <p>Uses a single JOIN FETCH query to eagerly load the full entity graph (User → Groups →
 * Assignments → Roles → Permissions) needed for OPA policy evaluation.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  @Query(
      """
      SELECT DISTINCT u FROM User u
      LEFT JOIN FETCH u.groups g
      LEFT JOIN FETCH g.assignments a
      LEFT JOIN FETCH a.role r
      LEFT JOIN FETCH r.permissions
      WHERE u.externalId = :externalId
      """)
  Optional<User> findByExternalIdWithContext(@Param("externalId") String externalId);
}

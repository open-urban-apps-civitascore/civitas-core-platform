package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends NamedEntityRepository<Role, String> {

  /**
   * Find a role by ID with permissions eagerly fetched. This prevents N+1 query problems when
   * loading roles with their permissions.
   *
   * @param id the role ID
   * @return the role with eagerly fetched permissions
   */
  @EntityGraph(attributePaths = {"permissions"})
  @Query("SELECT r FROM Role r WHERE r.id = :id")
  Optional<Role> findByIdWithRelations(@Param("id") String id);
}

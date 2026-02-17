package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Role;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends NamedEntityRepository<Role, UUID> {

  /**
   * Find a role by ID with permissions eagerly fetched. This prevents N+1 query problems when
   * loading roles with their permissions.
   *
   * @param id the role ID
   * @return the role with eagerly fetched permissions
   */
  @EntityGraph(attributePaths = {"permissions"})
  @Query("SELECT r FROM Role r WHERE r.id = :id")
  Optional<Role> findByIdWithRelations(@Param("id") UUID id);

  /**
   * Find all roles with permissions eagerly fetched. This prevents LazyInitializationException when
   * converting to DTOs outside the transaction boundary.
   */
  @EntityGraph(attributePaths = {"permissions"})
  @Override
  Page<Role> findAll(Specification<Role> spec, Pageable pageable);
}

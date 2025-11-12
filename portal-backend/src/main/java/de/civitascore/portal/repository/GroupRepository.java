package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends NamedEntityRepository<Group, String> {

  /**
   * Find a group by ID and tenant ID with related entities eagerly fetched.
   *
   * @param id the group ID
   * @param tenantId the tenant ID
   * @return the group with eagerly fetched contactUser and parentGroup
   */
  @EntityGraph(attributePaths = {"contactUser", "parentGroup"})
  @Query("SELECT g FROM Group g WHERE g.id = :id AND g.tenantId = :tenantId")
  Optional<Group> findByIdAndTenantIdWithRelations(
      @Param("id") String id, @Param("tenantId") String tenantId);
}

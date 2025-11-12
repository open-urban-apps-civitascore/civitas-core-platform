package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends TenantAwareRepository<Assignment, String> {
  List<Assignment> findByGroupIdAndScopeTypeAndScopeIdAndTenantId(
      String groupId, ScopeType scopeType, String scopeId, String tenantId);

  /**
   * Find an assignment by ID and tenant ID with all related entities eagerly fetched in a single
   * JOIN query. This prevents N+1 query problems when loading assignments with their relationships.
   * Use this for read operations (GET/UPDATE) instead of regular findById.
   *
   * @param id the assignment ID
   * @param tenantId the tenant ID
   * @return the assignment with eagerly fetched group, role, and parent assignment
   */
  @EntityGraph(attributePaths = {"group", "role", "parentAssignment"})
  @Query("SELECT a FROM Assignment a WHERE a.id = :id AND a.tenantId = :tenantId")
  Optional<Assignment> findByIdAndTenantIdWithRelations(
      @Param("id") String id, @Param("tenantId") String tenantId);
}

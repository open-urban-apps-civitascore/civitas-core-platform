package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.AssignmentType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends TenantAwareRepository<Assignment, String> {

  List<Assignment> findByGroupIdAndTenantId(String groupId, String tenantId);

  List<Assignment> findByScopeTypeAndScopeIdAndTenantId(
      ScopeType scopeType, String scopeId, String tenantId);

  List<Assignment> findByGroupIdAndScopeTypeAndScopeIdAndTenantId(
      String groupId, ScopeType scopeType, String scopeId, String tenantId);

  List<Assignment> findByAssignmentTypeAndTenantId(AssignmentType assignmentType, String tenantId);

  List<Assignment> findByIsInheritedTrueAndTenantId(String tenantId);

  List<Assignment> findByRoleIdAndTenantId(String roleId, String tenantId);
}

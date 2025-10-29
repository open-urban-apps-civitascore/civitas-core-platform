package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends TenantAwareRepository<Assignment, String> {
  List<Assignment> findByGroupIdAndScopeTypeAndScopeIdAndTenantId(
      String groupId, ScopeType scopeType, String scopeId, String tenantId);
}

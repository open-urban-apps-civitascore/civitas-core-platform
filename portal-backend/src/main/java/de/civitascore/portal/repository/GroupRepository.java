package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Group;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends TenantAwareRepository<Group, String> {
  Optional<Group> findByTitleAndTenantId(String title, String tenantId);
}

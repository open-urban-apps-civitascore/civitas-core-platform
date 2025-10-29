package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Role;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends TenantAwareRepository<Role, String> {
  Optional<Role> findByTitleAndTenantId(String title, String tenantId);
}

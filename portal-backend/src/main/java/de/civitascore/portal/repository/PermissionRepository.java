package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Permission;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends TenantAwareRepository<Permission, String> {
  Optional<Permission> findByTitleAndTenantId(String title, String tenantId);
}

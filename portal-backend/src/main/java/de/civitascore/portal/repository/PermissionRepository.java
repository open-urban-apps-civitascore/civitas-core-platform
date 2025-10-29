package de.civitascore.portal.repository;

import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends TenantAwareRepository<Permission, String> {
  Optional<Permission> findByTitleAndTenantId(String title, String tenantId);

  List<Permission> findByPermissionTypeAndTenantId(PermissionType permissionType, String tenantId);

  List<Permission> findByIsDefaultTrueAndTenantId(String tenantId);

  List<Permission> findByUserModifiableTrueAndTenantId(String tenantId);
}

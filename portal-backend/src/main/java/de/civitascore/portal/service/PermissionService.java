package de.civitascore.portal.service;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService extends TenantAwareService<Permission, String, PermissionInputDTO> {

  private final PermissionRepository permissionRepository;
  private final PermissionMapper permissionMapper;

  @Override
  protected TenantAwareRepository<Permission, String> getRepository() {
    return permissionRepository;
  }

  @Override
  protected PermissionMapper getMapper() {
    return permissionMapper;
  }

  @Override
  protected String getEntityName() {
    return "Permission";
  }

  public Optional<Permission> findByTitle(String name, String tenantId) {
    return permissionRepository.findByTitleAndTenantId(name, tenantId);
  }

  public List<Permission> findByType(PermissionType type, String tenantId) {
    return permissionRepository.findByPermissionTypeAndTenantId(type, tenantId);
  }

  public List<Permission> findDefaultPermissions(String tenantId) {
    return permissionRepository.findByIsDefaultTrueAndTenantId(tenantId);
  }

  public List<Permission> findUserModifiablePermissions(String tenantId) {
    return permissionRepository.findByUserModifiableTrueAndTenantId(tenantId);
  }

  @Override
  protected Permission preSave(Permission entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(Permission entity) {
    permissionRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "Permission", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }
}

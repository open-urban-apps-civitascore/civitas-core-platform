package de.civitascore.portal.service;

import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
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

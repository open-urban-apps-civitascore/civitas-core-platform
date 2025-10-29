package de.civitascore.portal.service;

import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService extends TenantAwareService<Role, String, RoleInputDTO> {

  private final RoleRepository roleRepository;
  private final RoleMapper roleMapper;
  private final PermissionService permissionService;

  @Override
  protected TenantAwareRepository<Role, String> getRepository() {
    return roleRepository;
  }

  @Override
  protected RoleMapper getMapper() {
    return roleMapper;
  }

  @Override
  protected String getEntityName() {
    return "Role";
  }

  public Optional<Role> findByTitle(String name, String tenantId) {
    return roleRepository.findByTitleAndTenantId(name, tenantId);
  }

  public List<Role> findByType(RoleType type, String tenantId) {
    return roleRepository.findByRoleTypeAndTenantId(type, tenantId);
  }

  public List<Role> findDefaultRoles(String tenantId) {
    return roleRepository.findByIsDefaultTrueAndTenantId(tenantId);
  }

  @Override
  protected Role postConvertToEntity(Role entity, RoleInputDTO input) {
    if (Objects.nonNull(input.getPermissionIds())) {
      entity.setPermissions(new HashSet<>());
      if (!input.getPermissionIds().isEmpty()) {
        input
            .getPermissionIds()
            .forEach(
                permissionId ->
                    permissionService
                        .getRepository()
                        .findById(permissionId)
                        .ifPresent(entity.getPermissions()::add));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected Role preSave(Role entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(Role entity) {
    roleRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "Role", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }
}

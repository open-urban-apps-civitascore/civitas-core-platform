package de.civitascore.portal.service;

import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.ForbiddenException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService extends BaseService<Role, RoleInputDTO> {

  private final RoleRepository roleRepository;
  private final RoleMapper roleMapper;
  private final PermissionService permissionService;

  @Override
  protected RoleRepository getRepository() {
    return roleRepository;
  }

  @Override
  protected RoleMapper getMapper() {
    return roleMapper;
  }

  @Override
  protected String getEntityName() {
    return Role.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of permissions. This fetches the
   * Role along with all Permissions in a single JOIN query, preventing N+1 query problems.
   */
  @Override
  public Optional<Role> findById(UUID id) {
    Optional<Role> entity = roleRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected Role postConvertToEntity(Role entity, RoleInputDTO input) {
    // Use findAllById for efficient batch loading of permissions instead of N+1 queries
    if (Objects.nonNull(input.getPermissionIds())) {
      entity.setPermissions(new HashSet<>());
      if (!input.getPermissionIds().isEmpty()) {
        entity.setPermissions(
            new HashSet<>(permissionService.getRepository().findAllById(input.getPermissionIds())));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected RoleInputDTO preProcessUpdateInput(RoleInputDTO input, Role existingEntity) {
    if (existingEntity.isReadonly()) {
      throw new ForbiddenException(
          "role", existingEntity.getId(), "Readonly roles cannot be modified");
    }

    return super.preProcessUpdateInput(input, existingEntity);
  }

  @Override
  protected Role preProcessDelete(UUID id) {
    Role existingEntity = super.preProcessDelete(id);

    if (existingEntity != null && existingEntity.isReadonly()) {
      throw new ForbiddenException(
          "role", existingEntity.getId(), "Readonly roles cannot be deleted");
    }

    return existingEntity;
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.embedded.PermissionType;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.ForbiddenException;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Role} entities. Resolves permission references during entity
 * conversion and enforces readonly protection on system-managed roles.
 */
@Service
@RequiredArgsConstructor
public class RoleService extends BaseService<Role, RoleInputDTO> {

  private static final Map<RoleType, PermissionType> ROLE_TO_PERMISSION_TYPE =
      Map.of(RoleType.SYSTEM, PermissionType.SYSTEM, RoleType.DATA, PermissionType.DATA);

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

  private void validatePermissionTypes(RoleType roleType, Set<Permission> permissions) {
    PermissionType expectedType = ROLE_TO_PERMISSION_TYPE.get(roleType);
    if (expectedType == null) {
      throw new IllegalStateException("No permission type mapping for role type " + roleType);
    }

    boolean hasMismatch = permissions.stream().anyMatch(p -> p.getPermissionType() != expectedType);
    if (hasMismatch) {
      throw new InvalidInputException(
          "permissions",
          "permissionIds",
          "Roles with type %s may only include permissions with type %s"
              .formatted(roleType, expectedType));
    }
  }

  /**
   * Resolves permission references after DTO-to-entity conversion by batch-loading permissions from
   * the provided IDs.
   *
   * @param entity the role entity
   * @param input the role input DTO containing permission IDs
   * @return the entity with resolved permission relationships
   */
  @Override
  protected Role postConvertToEntity(Role entity, RoleInputDTO input) {
    // Use findAllById for efficient batch loading of permissions instead of N+1 queries
    if (Objects.nonNull(input.getPermissionIds())) {
      entity.setPermissions(new HashSet<>());
      if (!input.getPermissionIds().isEmpty()) {
        Set<Permission> permissions =
            new HashSet<>(permissionService.getRepository().findAllById(input.getPermissionIds()));
        validatePermissionTypes(entity.getRoleType(), permissions);
        entity.setPermissions(permissions);
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Prevents modification of system-managed readonly roles.
   *
   * @param input the role update input
   * @param existingEntity the current role entity
   * @return the input if the role is not readonly
   * @throws ForbiddenException if the role is marked as readonly
   */
  @Override
  protected RoleInputDTO preProcessUpdateInput(RoleInputDTO input, Role existingEntity) {
    if (existingEntity.isReadonly()) {
      throw new ForbiddenException(
          "role", existingEntity.getId(), "Readonly roles cannot be modified");
    }

    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Prevents deletion of system-managed readonly roles.
   *
   * @param id the role ID to delete
   * @return the role entity to be deleted
   * @throws ForbiddenException if the role is marked as readonly
   */
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

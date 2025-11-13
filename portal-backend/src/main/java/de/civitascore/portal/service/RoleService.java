package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService extends BaseService<Role, String, RoleInputDTO> {

  private final RoleRepository roleRepository;
  private final RoleMapper roleMapper;
  private final PermissionService permissionService;
  private final ObjectMapper objectMapper;

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
  public Role findById(String id) {
    preProcessLoad(id);
    Role entity =
        roleRepository
            .findByIdWithRelations(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
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
  protected Role preSave(Role entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Role entity) {
    roleRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    Role.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

  @Override
  protected RoleInputDTO preProcessUpdateInput(RoleInputDTO input, Role existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", "Name cannot be null or blank", existingEntity.getId());
      }
      if (jsonNode.has("roleType") && jsonNode.get("roleType").isNull()) {
        throw new InvalidInputException(
            "roleType", "Role type cannot be null", existingEntity.getId());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

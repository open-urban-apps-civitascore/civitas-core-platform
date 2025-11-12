package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.RoleMapper;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.RoleInputDTO;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService extends BaseTenantAwareService<Role, String, RoleInputDTO> {

  private final RoleRepository roleRepository;
  private final RoleMapper roleMapper;
  private final PermissionService permissionService;
  private final ObjectMapper objectMapper;

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

  @Override
  protected RoleInputDTO preProcessUpdateInput(RoleInputDTO input, Role existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", "Name cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("title") && StringUtils.isBlank(jsonNode.get("title").asText())) {
        throw new InvalidInputException(
            "title", "Title cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("roleType") && jsonNode.get("roleType").isNull()) {
        throw new InvalidInputException(
            "roleType", "Role type cannot be null", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

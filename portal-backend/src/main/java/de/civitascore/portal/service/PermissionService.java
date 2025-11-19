package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.PermissionMapper;
import de.civitascore.portal.model.entity.Permission;
import de.civitascore.portal.model.input.PermissionInputDTO;
import de.civitascore.portal.repository.PermissionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService extends BaseService<Permission, PermissionInputDTO> {

  private final PermissionRepository permissionRepository;
  private final PermissionMapper permissionMapper;
  private final ObjectMapper objectMapper;

  @Override
  protected PermissionRepository getRepository() {
    return permissionRepository;
  }

  @Override
  protected PermissionMapper getMapper() {
    return permissionMapper;
  }

  @Override
  protected String getEntityName() {
    return Permission.class.getSimpleName();
  }

  @Override
  protected Permission preSave(Permission entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Permission entity) {
    permissionRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    Permission.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

  @Override
  protected PermissionInputDTO preProcessUpdateInput(
      PermissionInputDTO input, Permission existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", existingEntity.getId(), "Name cannot be null or blank");
      }
      if (jsonNode.has("permissionType") && jsonNode.get("permissionType").isNull()) {
        throw new InvalidInputException(
            "permissionType", existingEntity.getId(), "Permission type cannot be null");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

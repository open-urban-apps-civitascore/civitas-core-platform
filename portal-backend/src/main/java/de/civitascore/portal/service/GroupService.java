package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
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
public class GroupService extends BaseTenantAwareService<Group, String, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final RoleService roleService;
  private final ObjectMapper objectMapper;

  @Override
  protected Group preSave(Group entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(Group entity) {
    groupRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "Group", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    if (input.getContactUserId() != null) {
      entity.setContactUser(userService.findById(input.getContactUserId()));
    } else {
      entity.setContactUser(null);
    }

    if (input.getParentGroupId() != null) {
      groupRepository.findById(input.getParentGroupId()).ifPresent(entity::setParentGroup);
    } else {
      entity.setParentGroup(null);
    }

    if (Objects.nonNull(input.getMemberIds())) {
      entity.setMembers(new HashSet<>());
      if (!input.getMemberIds().isEmpty()) {
        input
            .getMemberIds()
            .forEach(
                userId ->
                    userService
                        .getRepository()
                        .findById(userId)
                        .ifPresent(entity.getMembers()::add));
      }
    }

    // Set system roles
    if (Objects.nonNull(input.getSystemRoleIds())) {
      entity.setSystemRoles(new HashSet<>());
      if (!input.getSystemRoleIds().isEmpty()) {
        input
            .getSystemRoleIds()
            .forEach(
                roleId ->
                    roleService
                        .getRepository()
                        .findById(roleId)
                        .ifPresent(entity.getSystemRoles()::add));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected TenantAwareRepository<Group, String> getRepository() {
    return groupRepository;
  }

  @Override
  protected GroupMapper getMapper() {
    return groupMapper;
  }

  @Override
  protected String getEntityName() {
    return "Group";
  }

  @Override
  protected GroupInputDTO preProcessUpdateInput(GroupInputDTO input, Group existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("title") && StringUtils.isBlank(jsonNode.get("title").asText())) {
        throw new InvalidInputException(
            "title", "Title cannot be null or blank", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

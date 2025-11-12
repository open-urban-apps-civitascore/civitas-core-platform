package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.GroupMapper;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Group_;
import de.civitascore.portal.model.input.GroupInputDTO;
import de.civitascore.portal.repository.GroupRepository;
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
public class GroupService extends BaseTenantAwareService<Group, GroupInputDTO> {

  private final GroupRepository groupRepository;
  private final GroupMapper groupMapper;
  private final UserService userService;
  private final RoleService roleService;
  private final ObjectMapper objectMapper;

  @Override
  protected Group preSave(Group entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Group entity) {
    groupRepository
        .findByNameAndTenantId(entity.getName(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    Group.class.getSimpleName(),
                    Group_.NAME,
                    entity.getName(),
                    Group_.TENANT_ID,
                    entity.getTenantId());
              }
            });
  }

  @Override
  protected Group postConvertToEntity(Group entity, GroupInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getContactUserId() != null) {
      entity.setContactUser(userService.getReferenceById(input.getContactUserId()));
    } else {
      entity.setContactUser(null);
    }

    if (input.getParentGroupId() != null) {
      entity.setParentGroup(groupRepository.getReferenceById(input.getParentGroupId()));
    } else {
      entity.setParentGroup(null);
    }

    // For collections, use findAllById for efficient batch loading
    if (Objects.nonNull(input.getMemberIds())) {
      entity.setMembers(new HashSet<>());
      if (!input.getMemberIds().isEmpty()) {
        entity.setMembers(
            new HashSet<>(userService.getRepository().findAllById(input.getMemberIds())));
      }
    }

    // Set system roles
    if (Objects.nonNull(input.getRoleIds())) {
      entity.setRoles(new HashSet<>());
      if (!input.getRoleIds().isEmpty()) {
        entity.setRoles(new HashSet<>(roleService.getRepository().findAllById(input.getRoleIds())));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected GroupRepository getRepository() {
    return groupRepository;
  }

  @Override
  protected GroupMapper getMapper() {
    return groupMapper;
  }

  @Override
  protected String getEntityName() {
    return Group.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * Group along with contactUser, parentGroup, members and roles in a single JOIN query, preventing
   * N+1 query problems.
   */
  @Override
  public Group findById(String id) {
    preProcessLoad(id);
    Group entity =
        groupRepository
            .findByIdAndTenantIdWithRelations(id, getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    return postLoad(entity);
  }

  @Override
  protected GroupInputDTO preProcessUpdateInput(GroupInputDTO input, Group existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", "Name cannot be null or blank", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

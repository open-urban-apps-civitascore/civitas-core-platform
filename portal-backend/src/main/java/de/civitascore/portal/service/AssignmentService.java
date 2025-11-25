package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService extends BaseService<Assignment, AssignmentInputDTO> {

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final GroupService groupService;
  private final RoleService roleService;
  private final ObjectMapper objectMapper;

  @Override
  protected AssignmentRepository getRepository() {
    return assignmentRepository;
  }

  @Override
  protected AssignmentMapper getMapper() {
    return assignmentMapper;
  }

  @Override
  protected String getEntityName() {
    return Assignment.class.getSimpleName();
  }

  @Override
  protected Assignment postConvertToEntity(Assignment entity, AssignmentInputDTO input) {

    if (input.getGroupId() != null) {
      entity.setGroup(groupService.findById(input.getGroupId()));
    } else {
      entity.setGroup(null);
    }

    if (input.getRoleId() != null) {
      entity.setRole(roleService.findById(input.getRoleId()));
    } else {
      entity.setRole(null);
    }

    if (input.getParentAssignmentId() != null) {
      entity.setParentAssignment(findById(input.getParentAssignmentId()));
    } else {
      entity.setParentAssignment(null);
    }

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * Assignment along with Group, Role, and ParentAssignment in a single JOIN query, preventing N+1
   * query problems that would occur with lazy loading.
   */
  @Override
  public Assignment findById(UUID id) {
    preProcessLoad(id);
    Assignment entity =
        assignmentRepository
            .findByIdWithRelations(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    return postLoad(entity);
  }

  @Override
  protected AssignmentInputDTO preProcessUpdateInput(
      AssignmentInputDTO input, Assignment existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("groupId") && StringUtils.isBlank(jsonNode.get("groupId").asText())) {
        throw new InvalidInputException(
            "groupId", existingEntity.getId(), "Group ID cannot be null or blank");
      }
      if (jsonNode.has("roleId") && StringUtils.isBlank(jsonNode.get("roleId").asText())) {
        throw new InvalidInputException(
            "roleId", existingEntity.getId(), "Role ID cannot be null or blank");
      }
      if (jsonNode.has("scopeType") && jsonNode.get("scopeType").isNull()) {
        throw new InvalidInputException(
            "scopeType", existingEntity.getId(), "Scope type cannot be null");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

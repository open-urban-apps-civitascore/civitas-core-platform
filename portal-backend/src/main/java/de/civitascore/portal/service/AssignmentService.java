package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService
    extends BaseTenantAwareService<Assignment, String, AssignmentInputDTO> {

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final GroupService groupService;
  private final RoleService roleService;
  private final ObjectMapper objectMapper;

  @Override
  protected TenantAwareRepository<Assignment, String> getRepository() {
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
      entity.setGroup(groupService.getReferenceById(input.getGroupId()));
    } else {
      entity.setGroup(null);
    }

    if (input.getRoleId() != null) {
      entity.setRole(roleService.getReferenceById(input.getRoleId()));
    } else {
      entity.setRole(null);
    }

    if (input.getParentAssignmentId() != null) {
      entity.setParentAssignment(
          assignmentRepository.getReferenceById(input.getParentAssignmentId()));
    } else {
      entity.setParentAssignment(null);
    }

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * Assignment along with Group, Role, and ParentAssignment in a single JOIN query, preventing N+1
   * query problems that would occur with lazy loading. Also ensures tenant isolation by filtering
   * by tenant ID.
   */
  @Override
  public Assignment findById(String id) {
    preProcessLoad(id);
    Assignment entity =
        assignmentRepository
            .findByIdAndTenantIdWithRelations(id, getCurrentTenantId())
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
            "groupId", "Group ID cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("roleId") && StringUtils.isBlank(jsonNode.get("roleId").asText())) {
        throw new InvalidInputException(
            "roleId", "Role ID cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("scopeType") && jsonNode.get("scopeType").isNull()) {
        throw new InvalidInputException(
            "scopeType", "Scope type cannot be null", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

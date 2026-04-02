package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Group;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.assignment.AssignmentGroupInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Factory for constructing {@link Assignment} entities from various input DTOs. Resolves and
 * validates referenced entities (groups, roles, scope targets) during assembly.
 */
@Service
@RequiredArgsConstructor
public class AssignmentFactory {

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;
  private final DataSetRepository dataSetRepository;
  private final DataStructureRepository dataStructureRepository;
  private final DataSourceRepository dataSourceRepository;

  /**
   * Build a new assignment from a scoped input DTO (used by BaseDataEntityService). Only resolves
   * group and role; scope is set by the calling data entity service.
   */
  public Assignment build(AssignmentScopedInputDTO dto) {
    return build(new Assignment(), dto);
  }

  /** Populate an existing assignment from a scoped input DTO. */
  public Assignment build(Assignment assignment, AssignmentScopedInputDTO dto) {
    assignment.setGroup(getGroup(dto.getGroupId()));
    assignment.setRole(getRole(dto.getRoleId(), true));

    return assignment;
  }

  /**
   * Build a new assignment from a group input DTO (used by GroupService). Resolves role and scope;
   * group is set by the calling service.
   */
  public Assignment build(AssignmentGroupInputDTO dto) {
    return build(new Assignment(), dto);
  }

  /** Populate an existing assignment from a group input DTO. */
  public Assignment build(Assignment assignment, AssignmentGroupInputDTO dto) {
    assignment.setRole(getRole(dto.getRoleId(), dto.getScopeType() != null));
    setScope(assignment, dto.getScopeType(), dto.getScopeId());

    return assignment;
  }

  /**
   * Build a new assignment from a full input DTO (used by AssignmentService). Resolves group, role,
   * scope type, and scope entity.
   */
  public Assignment build(AssignmentInputDTO dto) {
    return build(new Assignment(), dto);
  }

  /** Populate an existing assignment from a full input DTO. */
  public Assignment build(Assignment assignment, AssignmentInputDTO dto) {
    assignment.setGroup(getGroup(dto.getGroupId()));
    assignment.setRole(getRole(dto.getRoleId(), dto.getScopeType() != null));
    setScope(assignment, dto.getScopeType(), dto.getScopeId());

    return assignment;
  }

  private Group getGroup(UUID groupId) {
    if (groupId == null) {
      throw new InvalidInputException("Assignment", "groupId", "groupId is required");
    }

    return groupRepository
        .findById(groupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));
  }

  private Role getRole(UUID roleId, boolean scoped) {
    if (roleId == null) {
      throw new InvalidInputException("Assignment", "roleId", "roleId is required");
    }

    Role role =
        roleRepository
            .findById(roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
    if (scoped && role.getRoleType() == RoleType.SYSTEM) {
      throw new InvalidInputException("Assignment", "roleId", "SYSTEM roles cannot be scoped");
    }
    return role;
  }

  private Assignment setScope(Assignment assignment, ScopeType scopeType, UUID scopeId) {
    assignment.setScopeType(scopeType);

    if (scopeType == null) {
      return assignment;
    }

    if (scopeType != ScopeType.TENANT && scopeId == null) {
      throw new InvalidInputException(
          "Assignment", "scopeId", "scopeId is required for scope type " + scopeType);
    }

    switch (scopeType) {
      case TENANT -> {} // no scope entity resolution needed
      case DATASET ->
          assignment.setDataset(
              dataSetRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataSet", scopeId)));
      case DATASTRUCTURE ->
          assignment.setDataStructure(
              dataStructureRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataStructure", scopeId)));
      case DATASOURCE ->
          assignment.setDataSource(
              dataSourceRepository
                  .findById(scopeId)
                  .orElseThrow(() -> new ResourceNotFoundException("DataSource", scopeId)));
      case DATASPACE, CATALOG ->
          throw new InvalidInputException(
              "Assignment",
              scopeType.name(),
              scopeType + " scope is not available in this release");
      default ->
          throw new InvalidInputException(
              "Assignment", "scopeType", "Unsupported scope type: " + scopeType);
    }

    return assignment;
  }
}

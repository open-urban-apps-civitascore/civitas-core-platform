package de.civitascore.portal.service;

import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Role;
import de.civitascore.portal.model.input.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScopedAssignmentBuilderService {

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;

  public Assignment build(AssignmentScopedInputDTO dto) {
    Assignment assignment = new Assignment();
    assignment.setGroup(
        groupRepository
            .findById(dto.getGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Group", dto.getGroupId())));
    Role role =
        roleRepository
            .findById(dto.getRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("Role", dto.getRoleId()));
    if (role.getRoleType() == RoleType.SYSTEM) {
      throw new InvalidInputException("Assignment", "roleId", "SYSTEM roles cannot be scoped");
    }
    assignment.setRole(role);
    return assignment;
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.GroupRepository;
import de.civitascore.portal.repository.RoleRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentBuilderService {

  private final GroupRepository groupRepository;
  private final RoleRepository roleRepository;

  public Assignment build(AssignmentScopedInputDTO dto) {
    Assignment assignment = new Assignment();
    assignment.setGroup(
        groupRepository
            .findById(dto.getGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Group", dto.getGroupId())));
    assignment.setRole(
        roleRepository
            .findById(dto.getRoleId())
            .orElseThrow(() -> new ResourceNotFoundException("Role", dto.getRoleId())));
    return assignment;
  }
}

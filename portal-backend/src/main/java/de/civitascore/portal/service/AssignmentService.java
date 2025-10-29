package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService extends TenantAwareService<Assignment, String, AssignmentInputDTO> {

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final GroupService groupService;
  private final RoleService roleService;

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
    return "Assignment";
  }

  @Override
  protected Assignment postConvertToEntity(Assignment entity, AssignmentInputDTO input) {
    if (Objects.nonNull(input.getGroupId())) {
      entity.setGroup(groupService.findById(input.getGroupId()));
    }

    if (Objects.nonNull(input.getRoleId())) {
      entity.setRole(roleService.findById(input.getRoleId()));
    }

    if (Objects.nonNull(input.getParentAssignmentId())) {
      assignmentRepository
          .findById(input.getParentAssignmentId())
          .ifPresent(entity::setParentAssignment);
    }

    return super.postConvertToEntity(entity, input);
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.embedded.AssignmentType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import java.util.List;
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

  public List<Assignment> findByGroup(String groupId, String tenantId) {
    return assignmentRepository.findByGroupIdAndTenantId(groupId, tenantId);
  }

  public List<Assignment> findByScope(ScopeType scopeType, String scopeId, String tenantId) {
    return assignmentRepository.findByScopeTypeAndScopeIdAndTenantId(scopeType, scopeId, tenantId);
  }

  public List<Assignment> findByGroupAndScope(
      String groupId, ScopeType scopeType, String scopeId, String tenantId) {
    return assignmentRepository.findByGroupIdAndScopeTypeAndScopeIdAndTenantId(
        groupId, scopeType, scopeId, tenantId);
  }

  public List<Assignment> findBinaryAssignments(String tenantId) {
    return assignmentRepository.findByAssignmentTypeAndTenantId(AssignmentType.BINARY, tenantId);
  }

  public List<Assignment> findInheritedAssignments(String tenantId) {
    return assignmentRepository.findByIsInheritedTrueAndTenantId(tenantId);
  }

  public List<Assignment> findByRole(String roleId, String tenantId) {
    return assignmentRepository.findByRoleIdAndTenantId(roleId, tenantId);
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

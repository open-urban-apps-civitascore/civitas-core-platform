package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.embedded.RoleType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssignmentService extends BaseService<Assignment, AssignmentInputDTO> {

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final GroupService groupService;
  private final RoleService roleService;
  private final DataSetService dataSetService;
  private final DataStructureService dataStructureService;
  private final DataSourceService dataSourceService;

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
  protected AssignmentInputDTO preProcessCreateInput(AssignmentInputDTO input) {
    if (input.getScopeType() != null
        && input.getScopeType() != ScopeType.TENANT
        && input.getScopeId() == null) {
      throw new InvalidInputException(
          "Assignment", "scopeId", "scopeId is required for scope type " + input.getScopeType());
    }
    return super.preProcessCreateInput(input);
  }

  @Override
  protected AssignmentInputDTO preProcessUpdateInput(
      AssignmentInputDTO input, Assignment existingEntity) {
    if (input.getScopeType() != null
        && input.getScopeType() != ScopeType.TENANT
        && input.getScopeId() == null) {
      throw new InvalidInputException(
          "Assignment", "scopeId", "scopeId is required for scope type " + input.getScopeType());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  @Override
  protected Assignment postConvertToEntity(Assignment entity, AssignmentInputDTO input) {

    if (input.getGroupId() != null) {
      entity.setGroup(groupService.findByIdOrThrow(input.getGroupId()));
    } else {
      entity.setGroup(null);
    }

    if (input.getRoleId() != null) {
      var role = roleService.findByIdOrThrow(input.getRoleId());
      if (role.getRoleType() == RoleType.SYSTEM && input.getScopeId() != null) {
        throw new InvalidInputException("Assignment", "roleId", "SYSTEM roles cannot be scoped");
      }
      entity.setRole(role);
    } else {
      entity.setRole(null);
    }

    if (input.getScopeId() != null) {
      switch (input.getScopeType()) {
        case DATASET -> entity.setDataset(dataSetService.findByIdOrThrow(input.getScopeId()));
        case DATASTRUCTURE ->
            entity.setDataStructure(dataStructureService.findByIdOrThrow(input.getScopeId()));
        case DATASOURCE ->
            entity.setDataSource(dataSourceService.findByIdOrThrow(input.getScopeId()));
        case DATASPACE, CATALOG ->
            throw new InvalidInputException(
                "Assignment",
                input.getScopeType().name(),
                input.getScopeType() + " scope is not available in this release");
        default -> {}
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * Assignment along with Group, Role, and ParentAssignment in a single JOIN query, preventing N+1
   * query problems that would occur with lazy loading.
   */
  @Override
  public Optional<Assignment> findById(UUID id) {
    Optional<Assignment> entity = assignmentRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  public List<Assignment> findAllByRoleId(UUID roleId) {
    return getRepository().findAllByRoleId(roleId);
  }

  public List<Assignment> findAllByScopeTypeAndScopeId(ScopeType scopeType, UUID scopeId) {
    return switch (scopeType) {
      case DATASOURCE -> getRepository().findAllByScopeTypeAndDataSourceId(scopeType, scopeId);
      case DATASET -> getRepository().findAllByScopeTypeAndDatasetId(scopeType, scopeId);
      case DATASPACE -> getRepository().findAllByScopeTypeAndDataSpaceId(scopeType, scopeId);
      case CATALOG -> getRepository().findAllByScopeTypeAndCatalogId(scopeType, scopeId);
      case DATASTRUCTURE ->
          getRepository().findAllByScopeTypeAndDataStructureId(scopeType, scopeId);
      default ->
          throw new InvalidInputException("Assignment", scopeType.name(), "Unsupported scope type");
    };
  }
}

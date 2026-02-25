package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.Catalog;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.AssignmentInputDTO;
import de.civitascore.portal.model.input.AssignmentScopedInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentService extends BaseService<Assignment, AssignmentInputDTO> {

  private final CatalogService catalogService;

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final GroupService groupService;
  private final RoleService roleService;
  private final DataSetService datasetService;
  private final DataSpaceService dataSpaceService;
  private final DataStructureService dataStructureService;

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
      entity.setGroup(groupService.findByIdOrThrow(input.getGroupId()));
    } else {
      entity.setGroup(null);
    }

    if (input.getRoleId() != null) {
      entity.setRole(roleService.findByIdOrThrow(input.getRoleId()));
    } else {
      entity.setRole(null);
    }

    ScopeType scopeType = input.getScopeType();
    UUID scopeId = input.getScopeId();

    if (scopeId == null) return super.postConvertToEntity(entity, input);

    if (scopeType == ScopeType.DATASET) {
      DataSet dataset = datasetService.findByIdOrThrow(scopeId);
      entity.setDataset(dataset);
    }
    if (scopeType == ScopeType.DATASPACE) {
      DataSpace dataSpace = dataSpaceService.findByIdOrThrow(scopeId);
      entity.setDataSpace(dataSpace);
    }
    if (scopeType == ScopeType.CATALOG) {
      Catalog catalog = catalogService.findByIdOrThrow(scopeId);
      entity.setCatalog(catalog);
    }
    // TODO: set dataSource
    if (scopeType == ScopeType.DATASOURCE) {
      throw new ResourceNotFoundException("Datasource", scopeId);
    }
    if (scopeType == ScopeType.DATASTRUCTURE) {
      DataStructure dataStructure = dataStructureService.findByIdOrThrow(scopeId);
      entity.setDataStructure(dataStructure);
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

  /**
   * Replace all assignments for the given scope with new assignments. Deletes all existing
   * assignments matching the scopeType and scopeId, then creates new ones from the input list.
   *
   * @param inputs the new assignments to create
   * @param scopeType the scope type to replace assignments for
   * @param scopeId the scope ID to replace assignments for
   * @return the newly created assignments
   */
  @Transactional
  public List<Assignment> replaceAllByScopeTypeAndScopeId(
      List<AssignmentScopedInputDTO> inputs, ScopeType scopeType, UUID scopeId) {

    // Delete existing assignments
    List<Assignment> existing = findAllByScopeTypeAndScopeId(scopeType, scopeId);
    existing.forEach(entity -> deleteById(entity.getId()));

    return inputs.stream()
        .map(
            scopedInput -> {
              AssignmentInputDTO input = new AssignmentInputDTO();
              input.setGroupId(scopedInput.getGroupId());
              input.setRoleId(scopedInput.getRoleId());
              input.setScopeType(scopeType);
              input.setScopeId(scopeId);

              return create(input);
            })
        .toList();
  }
}

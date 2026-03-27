package de.civitascore.portal.service;

import de.civitascore.portal.mapper.AssignmentMapper;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.input.assignment.AssignmentInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Assignment} entities, which link groups and roles with optional
 * resource-level scoping (e.g., per datasource, dataset, or datastructure).
 */
@Service
@RequiredArgsConstructor
public class AssignmentService extends BaseService<Assignment, AssignmentInputDTO> {

  private final AssignmentRepository assignmentRepository;
  private final AssignmentMapper assignmentMapper;
  private final AssignmentFactory assignmentFactory;

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

  /**
   * Validates that a scope ID is provided when the scope type requires one before creating the
   * assignment.
   *
   * @param input the assignment creation input
   * @return the validated input
   * @throws InvalidInputException if a non-TENANT scope type is set without a scope ID
   */
  @Override
  protected AssignmentInputDTO preProcessCreateInput(AssignmentInputDTO input) {
    validateScopeId(input);
    return super.preProcessCreateInput(input);
  }

  /**
   * Validates that a scope ID is provided when the scope type requires one before updating the
   * assignment.
   *
   * @param input the assignment update input
   * @param existingEntity the current assignment entity
   * @return the validated input
   * @throws InvalidInputException if a non-TENANT scope type is set without a scope ID
   */
  @Override
  protected AssignmentInputDTO preProcessUpdateInput(
      AssignmentInputDTO input, Assignment existingEntity) {
    validateScopeId(input);
    return super.preProcessUpdateInput(input, existingEntity);
  }

  private void validateScopeId(AssignmentInputDTO input) {
    if (input.getScopeType() != null
        && input.getScopeType() != ScopeType.TENANT
        && input.getScopeId() == null) {
      throw new InvalidInputException(
          "Assignment", "scopeId", "scopeId is required for scope type " + input.getScopeType());
    }
  }

  /**
   * Delegates entity assembly to the {@link AssignmentFactory}, which resolves group, role, and
   * scope references from the input DTO.
   *
   * @param entity the assignment entity
   * @param input the assignment input DTO
   * @return the fully assembled assignment entity
   */
  @Override
  protected Assignment postConvertToEntity(Assignment entity, AssignmentInputDTO input) {
    return assignmentFactory.build(entity, input);
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

  /**
   * Finds all assignments for a user identified by their external (Keycloak) ID.
   *
   * @param externalId the external identity provider ID of the user
   * @return list of assignments associated with the user
   */
  public List<Assignment> findAllByUserExternalId(String externalId) {
    return getRepository().findAllByUserExternalId(externalId);
  }

  /**
   * Finds all assignments that reference the given role.
   *
   * @param roleId the role ID
   * @return list of assignments for the role
   */
  public List<Assignment> findAllByRoleId(UUID roleId) {
    return getRepository().findAllByRoleId(roleId);
  }

  /**
   * Finds all assignments scoped to a specific resource type and resource ID. Dispatches to the
   * appropriate repository query based on the {@link ScopeType}.
   *
   * @param scopeType the type of scoped resource (e.g., DATASOURCE, DATASET)
   * @param scopeId the ID of the scoped resource
   * @return list of matching assignments
   * @throws InvalidInputException if the scope type is unsupported
   */
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

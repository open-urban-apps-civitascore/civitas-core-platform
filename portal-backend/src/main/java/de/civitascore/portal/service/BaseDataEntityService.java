package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import de.civitascore.portal.model.input.assignment.AssignmentScopedInputDTO;
import de.civitascore.portal.security.AssignmentChangeAuthorizer;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base service for entities that carry scoped {@link Assignment} collections. Extends {@link
 * BaseService} by converting assignment input DTOs to entities via the {@link AssignmentFactory}
 * during the post-conversion lifecycle hook, authorized by the {@link AssignmentChangeAuthorizer}.
 */
public abstract class BaseDataEntityService<
        E extends BaseDataEntity, I extends BaseDataEntityInputDTO>
    extends BaseService<E, I> {

  /**
   * Returns the assignment factory used to build assignment entities from input DTOs.
   *
   * @return the assignment factory
   */
  protected abstract AssignmentFactory getAssignmentFactory();

  protected abstract AssignmentChangeAuthorizer getAssignmentChangeAuthorizer();

  /**
   * Replaces the data entity's assignments with those of the input after the base DTO-to-entity
   * conversion.
   *
   * @param entity the data entity
   * @param input the input DTO containing assignment definitions
   * @return the entity with resolved assignments
   */
  @Override
  protected E postConvertToEntity(E entity, I input) {
    replaceAssignments(entity, input.getAssignments());
    return super.postConvertToEntity(entity, input);
  }

  /**
   * Replaces the entity's assignments with the requested ones, once the caller is authorized for
   * every assignment added or removed. The only path by which a data entity's request body changes
   * its assignments.
   *
   * @param entity the data entity
   * @param requested the requested assignments; {@code null} leaves them untouched
   * @throws org.springframework.security.access.AccessDeniedException if the caller may not add or
   *     remove one of the changed assignments
   */
  protected final void replaceAssignments(E entity, Set<AssignmentScopedInputDTO> requested) {
    if (requested == null) {
      return;
    }
    Set<Assignment> assignments =
        requested.stream()
            .map(dto -> getAssignmentFactory().build(dto))
            .collect(Collectors.toSet());
    getAssignmentChangeAuthorizer().authorizeReplacement(entity, assignments);
    entity.setAssignments(assignments);
  }
}

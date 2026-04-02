package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base service for entities that carry scoped {@link Assignment} collections. Extends {@link
 * BaseService} by converting assignment input DTOs to entities via the {@link AssignmentFactory}
 * during the post-conversion lifecycle hook.
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

  /**
   * Converts assignment input DTOs to entities via the {@link AssignmentFactory} and sets them on
   * the data entity after the base DTO-to-entity conversion.
   *
   * @param entity the data entity
   * @param input the input DTO containing assignment definitions
   * @return the entity with resolved assignments
   */
  @Override
  protected E postConvertToEntity(E entity, I input) {
    if (input.getAssignments() != null) {
      Set<Assignment> assignments =
          input.getAssignments().stream()
              .map(dto -> getAssignmentFactory().build(dto))
              .collect(Collectors.toSet());
      entity.setAssignments(assignments);
    }
    return super.postConvertToEntity(entity, input);
  }
}

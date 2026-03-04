package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseDataEntityService<
        E extends BaseDataEntity, I extends BaseDataEntityInputDTO>
    extends BaseService<E, I> {

  protected abstract ScopedAssignmentBuilderService getAssignmentBuilderService();

  @Override
  protected E postConvertToEntity(E entity, I input) {
    if (input.getAssignments() != null) {
      Set<Assignment> assignments =
          input.getAssignments().stream()
              .distinct()
              .map(dto -> getAssignmentBuilderService().build(dto))
              .collect(Collectors.toSet());
      // Clear and flush DELETEs before INSERTs to avoid unique constraint violation
      // when re-inserting the same group+role+scope combination.
      entity.getAssignments().clear();
      getRepository().flush();
      entity.setAssignments(assignments);
    }
    return super.postConvertToEntity(entity, input);
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.base.BaseDataEntity;
import de.civitascore.portal.model.input.BaseDataEntityInputDTO;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseDataEntityService<
        E extends BaseDataEntity, I extends BaseDataEntityInputDTO>
    extends BaseService<E, I> {

  protected abstract AssignmentBuilderService getAssignmentBuilderService();

  @Override
  protected E postConvertToEntity(E entity, I input) {
    if (input.getAssignments() != null) {
      Set<Assignment> assignments =
          input.getAssignments().stream()
              .map(dto -> getAssignmentBuilderService().build(dto))
              .collect(Collectors.toSet());
      entity.setAssignments(assignments);
    }
    return super.postConvertToEntity(entity, input);
  }
}

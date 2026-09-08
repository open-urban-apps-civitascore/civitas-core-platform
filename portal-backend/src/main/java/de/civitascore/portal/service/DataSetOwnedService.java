package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;

/**
 * Abstract base service for entities owned by a parent dataset. Its create, update and delete hooks
 * reject the write unless the parent dataset is editable; an override that does not call {@code
 * super} drops that check.
 *
 * @param <T> the JPA entity type
 * @param <I> the input DTO type
 */
public abstract class DataSetOwnedService<
        T extends DataSetOwnedEntity, I extends DataSetOwnedInputDTO>
    extends BaseService<T, I> {

  private final DataSetMutationGuard dataSetMutationGuard;

  protected DataSetOwnedService(DataSetMutationGuard dataSetMutationGuard) {
    this.dataSetMutationGuard = dataSetMutationGuard;
  }

  @Override
  protected I preProcessCreateInput(I input) {
    dataSetMutationGuard.requireMutable(input.getDataSetId(), "create " + getEntityName());
    return super.preProcessCreateInput(input);
  }

  @Override
  protected I preProcessUpdateInput(I input, T existingEntity) {
    dataSetMutationGuard.requireMutable(existingEntity, "update " + getEntityName());
    return super.preProcessUpdateInput(input, existingEntity);
  }

  @Override
  protected T preProcessDelete(UUID id) {
    T entity = findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    dataSetMutationGuard.requireMutable(entity, "delete " + getEntityName());
    onDelete(entity);
    return entity;
  }

  /**
   * Runs after the parent dataset has been accepted as editable and before the entity is deleted.
   * Subclasses reject the delete here when the entity is still referenced.
   *
   * @param entity the entity about to be deleted
   */
  protected void onDelete(T entity) {}
}

package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataSetOwned;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;

/**
 * Abstract base service for entities owned by a parent dataset.
 *
 * @param <T> the JPA entity type
 * @param <I> the input DTO type
 */
public abstract class DataSetOwnedService<T extends DataSetOwned, I extends DataSetOwnedInputDTO>
    extends BaseService<T, I> {

  /**
   * @throws ResourceNotFoundException if the entity does not exist
   */
  @Override
  protected final T preProcessDelete(UUID id) {
    T entity = findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    onDelete(entity);
    return entity;
  }

  /**
   * Runs before the entity is deleted. Subclasses reject the delete here when the entity is still
   * referenced.
   *
   * @param entity the entity about to be deleted
   */
  protected void onDelete(T entity) {}
}

package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.base.DataSetOwnedEntity;
import de.civitascore.portal.model.input.DataSetOwnedInputDTO;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

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

  /**
   * @throws ResourceNotFoundException if the entity does not exist or belongs to another dataset
   */
  public T findOwned(UUID dataSetId, UUID id) {
    T entity = findByIdOrThrow(id);
    ParentOwnership.requireSameParent(dataSetId, entity.getDataSet().getId(), getEntityName(), id);
    return entity;
  }

  public Page<T> findAll(UUID dataSetId, Specification<T> spec, Pageable pageable) {
    Specification<T> inDataSet =
        (root, query, cb) -> cb.equal(root.get("dataSet").get("id"), dataSetId);
    return findAll(spec == null ? inDataSet : spec.and(inDataSet), pageable);
  }

  /**
   * @throws ResourceNotFoundException if the dataset does not exist
   */
  @Transactional
  public T create(UUID dataSetId, I input) {
    input.setDataSetId(dataSetId);
    return create(input);
  }

  /**
   * @throws ResourceNotFoundException if the entity does not exist or belongs to another dataset
   */
  @Transactional
  public T update(UUID dataSetId, UUID id, I input) {
    findOwned(dataSetId, id);
    input.setDataSetId(dataSetId);
    return update(id, input);
  }

  /**
   * @throws ResourceNotFoundException if the entity does not exist or belongs to another dataset
   */
  @Transactional
  public void delete(UUID dataSetId, UUID id) {
    findOwned(dataSetId, id);
    deleteById(id);
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

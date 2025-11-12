package de.civitascore.portal.service;

import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.io.Serializable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public abstract class BaseService<T, ID extends Serializable, I extends BaseInputDTO> {

  protected abstract BaseRepository<T, ID> getRepository();

  /**
   * Create a new entity from input DTO.
   *
   * @param input the input DTO
   * @return the created entity
   */
  @Transactional
  public abstract T create(I input);

  /**
   * Update an existing entity with input DTO.
   *
   * @param id the entity ID
   * @param input the input DTO
   * @return the updated entity
   */
  @Transactional
  public abstract T update(ID id, I input);

  public Page<T> findAll(Specification<T> spec, Pageable pageable) {
    return getRepository().findAll(spec, pageable);
  }

  public T findById(ID id) {
    return getRepository()
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id.toString()));
  }

  @Transactional
  public T save(T entity) {
    return getRepository().save(entity);
  }

  @Transactional
  public void deleteById(ID id) {
    if (!getRepository().existsById(id)) {
      throw new ResourceNotFoundException(getEntityName(), id.toString());
    }
    getRepository().deleteById(id);
  }

  public boolean existsById(ID id) {
    return getRepository().existsById(id);
  }

  public long count() {
    return getRepository().count();
  }

  /**
   * Get the entity name for error messages.
   *
   * @return the entity name
   */
  protected abstract String getEntityName();
}

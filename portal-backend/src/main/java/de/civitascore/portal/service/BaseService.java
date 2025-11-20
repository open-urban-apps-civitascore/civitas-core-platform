package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.io.Serializable;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public abstract class BaseService<T, I extends BaseInputDTO> {

  protected abstract BaseRepository<T, UUID> getRepository();

  /**
   * Get the mapper for converting between DTOs and entities.
   *
   * @return the mapper
   */
  protected abstract DtoMapper<I, ?, T> getMapper();

  /**
   * Create a new entity from input DTO.
   *
   * <p>This method uses lifecycle hooks that can be overridden by subclasses:
   *
   * <ul>
   *   <li>{@link #preProcessCreateInput(BaseInputDTO)} - process input before conversion
   *   <li>{@link #postConvertToEntity(Object, BaseInputDTO)} - customize entity after conversion
   *   <li>{@link #preSave(Object)} - final modifications before saving
   *   <li>{@link #postSave(Object, BaseInputDTO)} - actions after saving
   * </ul>
   *
   * @param input the input DTO
   * @return the created entity
   */
  @Transactional
  public T create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    T entity = getMapper().toEntity(preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    T saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  /**
   * Update an existing entity with input DTO.
   *
   * <p>This method uses lifecycle hooks that can be overridden by subclasses:
   *
   * <ul>
   *   <li>{@link #preProcessUpdateInput(BaseInputDTO, Object)} - process input before conversion
   *   <li>{@link #postConvertToEntity(Object, BaseInputDTO)} - customize entity after conversion
   *   <li>{@link #preSave(Object)} - final modifications before saving
   *   <li>{@link #postSave(Object, BaseInputDTO)} - actions after saving
   * </ul>
   *
   * @param id the entity ID
   * @param input the input DTO
   * @return the updated entity
   */
  @Transactional
  public T update(UUID id, I input) {
    T entity = findById(id);
    I preProcessedInput = preProcessUpdateInput(input, entity);
    getMapper().updateEntity(entity, preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    T saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  /**
   * Find all entities matching the specification.
   *
   * <p>This method uses lifecycle hooks:
   *
   * <ul>
   *   <li>{@link #preProcessQuery(Specification, Pageable)} - enhance specification
   *   <li>{@link #postProcessQueryResult(Page)} - modify result page
   * </ul>
   *
   * @param spec the specification
   * @param pageable the pagination info
   * @return the page of entities
   */
  public Page<T> findAll(Specification<T> spec, Pageable pageable) {
    Specification<T> enhancedSpec = preProcessQuery(spec, pageable);
    Page<T> result = getRepository().findAll(enhancedSpec, pageable);
    return postProcessQueryResult(result);
  }

  /**
   * Find an entity by ID.
   *
   * <p>This method uses lifecycle hooks:
   *
   * <ul>
   *   <li>{@link #preProcessLoad(Serializable)} - load and optionally process entity
   *   <li>{@link #postLoad(Object)} - customize entity after loading
   * </ul>
   *
   * @param id the entity ID
   * @return the entity
   * @throws ResourceNotFoundException if entity not found
   */
  public T findById(UUID id) {
    T entity = preProcessLoad(id);
    if (entity == null) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return postLoad(entity);
  }

  @Transactional
  public T save(T entity) {
    return getRepository().save(entity);
  }

  /**
   * Delete an entity by ID.
   *
   * <p>This method uses lifecycle hooks:
   *
   * <ul>
   *   <li>{@link #preProcessDelete(Serializable)} - load entity before deletion
   *   <li>{@link #postDelete(Object)} - actions after deletion
   * </ul>
   *
   * @param id the entity ID
   * @throws ResourceNotFoundException if entity not found
   */
  @Transactional
  public void deleteById(UUID id) {
    if (!getRepository().existsById(id)) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    T entity = preProcessDelete(id);
    getRepository().deleteById(id);
    postDelete(entity);
  }

  public boolean existsById(UUID id) {
    return getRepository().existsById(id);
  }

  public long count() {
    return getRepository().count();
  }

  /**
   * Get a reference to an entity by ID without loading it from the database. This returns a JPA
   * proxy that will only be loaded when accessed. Useful for setting foreign key relationships
   * without triggering unnecessary queries.
   *
   * @param id the entity ID
   * @return a reference to the entity (lazy proxy)
   * @throws ResourceNotFoundException if entity not found
   */
  @Transactional(readOnly = true)
  public T getReferenceById(UUID id) {
    return getRepository().getReferenceById(id);
  }

  /**
   * Get the entity name for error messages.
   *
   * @return the entity name
   */
  protected abstract String getEntityName();

  /**
   * Pre-process input before creating entity.
   *
   * @param input the input DTO
   * @return the processed input
   */
  protected I preProcessCreateInput(I input) {
    return input;
  }

  /**
   * Called after converting input DTO to entity, before saving.
   *
   * @param entity the entity to process
   * @param input the input DTO
   * @return the processed entity
   */
  protected T postConvertToEntity(T entity, I input) {
    return entity;
  }

  /**
   * Called before saving the entity (create or update).
   *
   * @param entity the entity to process
   * @return the processed entity
   */
  protected T preSave(T entity) {
    return entity;
  }

  /**
   * Called after saving the entity (create or update).
   *
   * @param entity the saved entity
   * @param input the input DTO
   */
  protected void postSave(T entity, I input) {}

  /**
   * Pre-process input before updating entity.
   *
   * @param input the input DTO
   * @param existingEntity the existing entity
   * @return the processed input
   */
  protected I preProcessUpdateInput(I input, T existingEntity) {
    return input;
  }

  /**
   * Pre-process query specification.
   *
   * @param spec the original specification
   * @param pageable the pagination info
   * @return the enhanced specification
   */
  protected Specification<T> preProcessQuery(Specification<T> spec, Pageable pageable) {
    return spec;
  }

  /**
   * Post-process query result.
   *
   * @param result the query result page
   * @return the processed result page
   */
  protected Page<T> postProcessQueryResult(Page<T> result) {
    return result;
  }

  /**
   * Load entity by ID from database. Override this method to add custom loading logic or caching.
   * The returned entity will be passed to {@link #postLoad(Object)}.
   *
   * @param id the entity ID
   * @return the loaded entity or null if not found
   */
  protected T preProcessLoad(UUID id) {
    return getRepository().findById(id).orElse(null);
  }

  /**
   * Post-process entity after loading.
   *
   * @param entity the loaded entity
   * @return the processed entity
   */
  protected T postLoad(T entity) {
    return entity;
  }

  /**
   * Pre-process before deleting entity.
   *
   * @param id the entity ID
   * @return the entity to be deleted or null
   */
  protected T preProcessDelete(UUID id) {
    return getRepository().findById(id).orElse(null);
  }

  /**
   * Post-process after deleting entity.
   *
   * @param entity the deleted entity
   */
  protected void postDelete(T entity) {}
}

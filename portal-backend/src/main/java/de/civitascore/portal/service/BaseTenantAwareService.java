package de.civitascore.portal.service;

import de.civitascore.portal.configuration.TenantContext;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.entity.base.TenantAwareEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.io.Serializable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abstract base service for tenant-aware entities.
 *
 * <p>This service provides CRUD operations with automatic tenant ID injection and validation. All
 * entities managed by this service must extend {@link TenantAwareEntity}.
 *
 * @param <E> the entity type extending TenantAwareEntity
 * @param <ID> the ID type
 * @param <I> the input DTO type
 */
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public abstract class BaseTenantAwareService<
        E extends TenantAwareEntity, ID extends Serializable, I extends BaseInputDTO>
    extends BaseService<E, ID, I> {

  @Override
  @Transactional
  public E create(I input) {
    I preProcessedInput = preProcessCreateInput(input);
    E entity = getMapper().toEntity(preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    E saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  @Override
  @Transactional
  public E update(ID id, I input) {
    E entity = findById(id);
    I preProcessedInput = preProcessUpdateInput(input, entity);
    getMapper().updateEntity(entity, preProcessedInput);
    entity = postConvertToEntity(entity, preProcessedInput);
    entity = preSave(entity);
    E saved = getRepository().save(entity);
    postSave(saved, preProcessedInput);
    return saved;
  }

  @Override
  public Page<E> findAll(Specification<E> spec, Pageable pageable) {
    Specification<E> enhancedSpec = preProcessQuery(spec, pageable);
    Page<E> result = getRepository().findAll(enhancedSpec, pageable);
    return postProcessQueryResult(result);
  }

  @Override
  public E findById(ID id) {
    preProcessLoad(id);
    E entity =
        getRepository()
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id.toString()));
    return postLoad(entity);
  }

  @Override
  @Transactional
  public void deleteById(ID id) {
    if (!getRepository().existsById(id)) {
      throw new ResourceNotFoundException(getEntityName(), id.toString());
    }
    E entity = preProcessDelete(id);
    getRepository().deleteById(id);
    postDelete(entity);
  }

  protected I preProcessCreateInput(I input) {
    return input;
  }

  /**
   * Called after converting input DTO to entity, before saving.
   *
   * <p>This is the default hook to set the tenant ID. Override if you need custom behavior.
   *
   * @param entity the entity to process
   * @param input the input DTO
   * @return the processed entity
   */
  protected E postConvertToEntity(E entity, I input) {
    setTenantId(entity);
    return entity;
  }

  protected E preSave(E entity) {
    return entity;
  }

  protected void postSave(E entity, I input) {}

  protected I preProcessUpdateInput(I input, E existingEntity) {
    return input;
  }

  protected Specification<E> preProcessQuery(Specification<E> spec, Pageable pageable) {
    return spec;
  }

  protected Page<E> postProcessQueryResult(Page<E> result) {
    return result;
  }

  protected E preProcessLoad(ID id) {
    return getRepository().findById(id).orElse(null);
  }

  protected E postLoad(E entity) {
    return entity;
  }

  protected E preProcessDelete(ID id) {
    return getRepository().findById(id).orElse(null);
  }

  protected void postDelete(E entity) {}

  @Override
  protected abstract TenantAwareRepository<E, ID> getRepository();

  protected abstract DtoMapper<I, ?, E> getMapper();

  /**
   * Gets the current tenant ID from the TenantContext.
   *
   * @return the tenant ID
   * @throws IllegalStateException if no tenant context is available
   */
  protected String getCurrentTenantId() {
    return TenantContext.requireTenantId();
  }

  /**
   * Sets the tenant ID on the entity from the current tenant context.
   *
   * <p>This method is type-safe since all entities handled by this service must extend {@link
   * TenantAwareEntity}.
   *
   * @param entity the entity to set the tenant ID on
   */
  protected void setTenantId(E entity) {
    if (entity.getTenantId() != null) {
      log.debug(
          "Entity {} already has tenant ID '{}', not overwriting",
          entity.getClass().getSimpleName(),
          entity.getTenantId());
      return;
    }

    String tenantId = getCurrentTenantId();
    entity.setTenantId(tenantId);
    log.trace("Set tenant ID '{}' on entity {}", tenantId, entity.getClass().getSimpleName());
  }
}

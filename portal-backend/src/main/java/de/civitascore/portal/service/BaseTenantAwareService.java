package de.civitascore.portal.service;

import de.civitascore.portal.configuration.TenantContext;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.entity.base.TenantAwareEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abstract base service for tenant-aware entities.
 *
 * <p>This service provides CRUD operations with automatic tenant ID injection and validation. All
 * entities managed by this service must extend {@link TenantAwareEntity}.
 *
 * <p>The tenant ID is automatically set in the {@link #postConvertToEntity(TenantAwareEntity,
 * BaseInputDTO)} hook.
 *
 * @param <E> the entity type extending TenantAwareEntity
 * @param <I> the input DTO type
 */
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public abstract class BaseTenantAwareService<E extends TenantAwareEntity, I extends BaseInputDTO>
    extends BaseService<E, String, I> {

  /**
   * Called after converting input DTO to entity, before saving.
   *
   * <p>This override sets the tenant ID automatically. Subclasses should call {@code
   * super.postConvertToEntity(entity, input)} if they override this method.
   *
   * @param entity the entity to process
   * @param input the input DTO
   * @return the processed entity
   */
  @Override
  protected E postConvertToEntity(E entity, I input) {
    setTenantId(entity);
    return entity;
  }

  @Override
  protected abstract TenantAwareRepository<E, String> getRepository();

  @Override
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

  /**
   * Get a reference to an entity by ID without loading it from the database. This returns a JPA
   * proxy that will only be loaded when accessed.
   *
   * <p>This override validates that the entity exists within the current tenant before returning
   * the reference, ensuring tenant isolation.
   *
   * @param id the entity ID
   * @return a reference to the entity (lazy proxy)
   * @throws jakarta.persistence.EntityNotFoundException if entity not found in current tenant
   */
  @Override
  public E getReferenceById(String id) {
    E entity = getRepository().getReferenceByIdAndTenantIdOrThrow(id, getCurrentTenantId());
    if (entity == null) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return entity;
  }

  /**
   * Check if an entity exists by ID within the current tenant.
   *
   * @param id the entity ID
   * @return true if the entity exists in the current tenant, false otherwise
   */
  @Override
  public boolean existsById(String id) {
    return getRepository().existsByIdAndTenantId(id, getCurrentTenantId());
  }
}

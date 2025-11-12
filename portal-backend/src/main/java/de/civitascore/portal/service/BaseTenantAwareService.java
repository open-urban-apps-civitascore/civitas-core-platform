package de.civitascore.portal.service;

import de.civitascore.portal.configuration.TenantContext;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.entity.base.TenantAwareEntity;
import de.civitascore.portal.model.input.BaseInputDTO;
import de.civitascore.portal.repository.TenantAwareRepository;
import java.io.Serializable;
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
 * @param <ID> the ID type
 * @param <I> the input DTO type
 */
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public abstract class BaseTenantAwareService<
        E extends TenantAwareEntity, ID extends Serializable, I extends BaseInputDTO>
    extends BaseService<E, ID, I> {

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
  protected abstract TenantAwareRepository<E, ID> getRepository();

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
}

package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.base.TenantAwareEntity;
import jakarta.persistence.EntityNotFoundException;
import java.io.Serializable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantAwareEntity, ID extends Serializable>
    extends BaseRepository<T, ID> {

  /**
   * Check if an entity exists by ID and tenant ID.
   *
   * @param id the entity ID
   * @param tenantId the tenant ID
   * @return true if the entity exists, false otherwise
   */
  boolean existsByIdAndTenantId(ID id, String tenantId);

  /**
   * Get a reference to an entity by ID and tenant ID, throwing an exception if it doesn't exist.
   * This is the tenant-aware version of {@link #getReferenceByIdOrThrow(Serializable)}.
   *
   * @param id the entity ID
   * @param tenantId the tenant ID
   * @return a reference to the entity (lazy proxy)
   * @throws EntityNotFoundException if the entity doesn't exist in the given tenant
   */
  default T getReferenceByIdAndTenantIdOrThrow(ID id, String tenantId) {
    if (!existsByIdAndTenantId(id, tenantId)) {
      return null;
    }
    return getReferenceById(id);
  }
}

package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.base.NamedEntity;
import java.io.Serializable;
import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface NamedEntityRepository<T extends NamedEntity, ID extends Serializable>
    extends TenantAwareRepository<T, ID> {

  /**
   * Find an entity by name and tenant ID.
   *
   * @param name the entity name
   * @param tenantId the tenant ID
   * @return the entity if found
   */
  Optional<T> findByNameAndTenantId(String name, String tenantId);
}

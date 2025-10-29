package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Tenant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantRepository extends BaseRepository<Tenant, String> {
  Optional<Tenant> findByName(String name);
}

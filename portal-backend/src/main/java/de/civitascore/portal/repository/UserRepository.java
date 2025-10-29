package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.User;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends TenantAwareRepository<User, String> {
  Optional<User> findByEmailAndTenantId(String email, String tenantId);

  Optional<User> findByExternalIdAndTenantId(String externalId, String tenantId);
}

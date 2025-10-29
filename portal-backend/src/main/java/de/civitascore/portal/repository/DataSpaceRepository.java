package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSpace;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSpaceRepository extends TenantAwareRepository<DataSpace, String> {
  Optional<DataSpace> findByTitleAndTenantId(String title, String tenantId);
}

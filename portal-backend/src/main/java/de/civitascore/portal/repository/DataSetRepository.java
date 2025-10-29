package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSetRepository extends TenantAwareRepository<DataSet, String> {
  Optional<DataSet> findByTitleAndTenantId(String title, String tenantId);

  Optional<DataSet> findByExternalIdAndTenantId(String externalId, String tenantId);

  List<DataSet> findByOwnerIdAndTenantId(String ownerId, String tenantId);
}

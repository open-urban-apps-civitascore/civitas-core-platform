package de.civitascore.portal.repository;

import java.io.Serializable;
import java.util.List;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TenantAwareRepository<T, ID extends Serializable> extends BaseRepository<T, ID> {

  List<T> findByTenantId(String tenantId);

  void deleteByTenantId(String tenantId);

  long countByTenantId(String tenantId);
}

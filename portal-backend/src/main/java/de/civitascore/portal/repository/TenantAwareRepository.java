package de.civitascore.portal.repository;

import java.io.Serializable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TenantAwareRepository<T, ID extends Serializable> extends BaseRepository<T, ID> {}

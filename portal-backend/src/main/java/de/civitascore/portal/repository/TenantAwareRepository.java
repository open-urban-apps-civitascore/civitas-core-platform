package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.base.TenantAwareEntity;
import java.io.Serializable;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface TenantAwareRepository<T extends TenantAwareEntity, ID extends Serializable>
    extends BaseRepository<T, ID> {}

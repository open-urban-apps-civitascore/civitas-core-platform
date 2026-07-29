package de.civitascore.authz.repository.data;

import de.civitascore.portal.model.entity.DataSource;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for datasource→datapool usability lookups, used read-only.
 *
 * <p>Used by {@link de.civitascore.authz.repository.service.DataSourcePoolsService} to resolve
 * which datapools a single data source may be used in. Reads the same portal database as the portal
 * backend via the shared {@code portal-model} entities.
 */
@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, UUID> {}

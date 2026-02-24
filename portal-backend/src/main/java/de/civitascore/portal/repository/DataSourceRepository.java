package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSource;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSourceRepository extends NamedEntityRepository<DataSource, UUID> {}

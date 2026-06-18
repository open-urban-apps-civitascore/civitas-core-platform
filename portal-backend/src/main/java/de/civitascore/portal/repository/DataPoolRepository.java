package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataPool;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataPool} entities. */
@Repository
public interface DataPoolRepository extends NamedEntityRepository<DataPool, UUID> {}

package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Resource;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Resource} entities. */
@Repository
public interface ResourceRepository extends BaseRepository<Resource, UUID> {}

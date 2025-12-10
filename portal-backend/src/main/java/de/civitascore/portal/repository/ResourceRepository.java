package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Resource;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface ResourceRepository extends BaseRepository<Resource, UUID> {}

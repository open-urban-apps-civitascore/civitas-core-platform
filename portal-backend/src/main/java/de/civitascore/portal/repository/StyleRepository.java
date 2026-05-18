package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Style;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link Style} entities. */
@Repository
public interface StyleRepository extends BaseRepository<Style, UUID> {

  boolean existsByDataSetIdAndName(UUID dataSetId, String name);
}

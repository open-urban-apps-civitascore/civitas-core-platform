package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSink;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSink} entities. */
@Repository
public interface DataSinkRepository extends BaseRepository<DataSink, UUID> {}

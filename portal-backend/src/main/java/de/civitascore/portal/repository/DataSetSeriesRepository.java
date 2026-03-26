package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSetSeries;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link DataSetSeries} entities. */
@Repository
public interface DataSetSeriesRepository extends NamedEntityRepository<DataSetSeries, UUID> {}

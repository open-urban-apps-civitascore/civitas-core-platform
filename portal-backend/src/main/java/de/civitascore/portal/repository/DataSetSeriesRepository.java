package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DataSetSeries;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface DataSetSeriesRepository extends NamedEntityRepository<DataSetSeries, UUID> {}

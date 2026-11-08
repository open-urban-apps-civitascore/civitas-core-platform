package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.DashboardSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardSourceRepository extends BaseRepository<DashboardSource, UUID> {

  Optional<DashboardSource> findByExternalSourceId(String externalSourceId);

  boolean existsByExternalSourceId(String externalSourceId);

  List<DashboardSource> findByMappedDatasetId(UUID mappedDatasetId);
}

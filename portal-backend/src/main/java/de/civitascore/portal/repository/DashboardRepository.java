package de.civitascore.portal.repository;

import de.civitascore.portal.model.entity.Dashboard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardRepository extends BaseRepository<Dashboard, UUID> {

  Optional<Dashboard> findByExternalDashboardId(String externalDashboardId);

  boolean existsByExternalDashboardId(String externalDashboardId);

  List<Dashboard> findByDatasetId(UUID datasetId);
}

package de.civitascore.authz.repository.data;

import de.civitascore.portal.model.entity.Dashboard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only repository for dashboard→dataset resolution.
 *
 * <p>Used by {@link de.civitascore.authz.repository.service.DashboardDatasetService} to resolve
 * which Civitas dataset a Superset (or other rendering engine) dashboard belongs to, keyed by the
 * engine's own dashboard id ({@link Dashboard#getExternalDashboardId()}) rather than the platform's
 * own {@code id} — that's the only identifier the engine-side caller has. Reads the same portal
 * database as the portal backend via the shared {@code portal-model} entities.
 */
@Repository
public interface DashboardRepository extends JpaRepository<Dashboard, UUID> {

  Optional<Dashboard> findByExternalDashboardId(String externalDashboardId);

  List<Dashboard> findByExternalDashboardIdIn(List<String> externalDashboardIds);
}

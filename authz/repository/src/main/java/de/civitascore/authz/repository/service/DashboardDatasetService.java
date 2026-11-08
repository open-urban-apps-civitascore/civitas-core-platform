package de.civitascore.authz.repository.service;

import de.civitascore.authz.repository.data.DashboardRepository;
import de.civitascore.authz.repository.model.dto.DashboardDatasetBatchItem;
import de.civitascore.authz.repository.model.dto.DashboardDatasetResponse;
import de.civitascore.portal.model.entity.Dashboard;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service resolving which Civitas dataset a rendering-engine dashboard belongs to, by the engine's
 * own dashboard id.
 *
 * <p>Exists so authorization decisions for a dashboard can be keyed off the platform's own,
 * authoritative {@code Dashboard.dataSet} relationship instead of a copy of the dataset id stored
 * in the engine's own metadata (e.g. Superset's {@code json_metadata}) — the engine-side copy can
 * only ever be as reliable as that engine's own write path and schema validation, whereas this
 * relationship is the platform's system of record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardDatasetService {

  private final DashboardRepository dashboardRepository;

  /**
   * @param externalDashboardId the rendering engine's own dashboard id
   * @return present with the dataset id if a dashboard with this external id exists; empty
   *     otherwise
   */
  @Transactional(readOnly = true)
  public Optional<DashboardDatasetResponse> getDashboardDataset(String externalDashboardId) {
    return dashboardRepository
        .findByExternalDashboardId(externalDashboardId)
        .map(
            dashboard ->
                DashboardDatasetResponse.builder()
                    .civitasDatasetId(dashboard.getDataSet().getId())
                    .build());
  }

  /**
   * Batch variant of {@link #getDashboardDataset(String)} — one query for every id in {@code
   * externalDashboardIds}, rather than one round-trip per dashboard. Ids with no matching dashboard
   * are simply absent from the result, in the same spirit as the single lookup's 404.
   *
   * @param externalDashboardIds the rendering engine's own dashboard ids
   * @return one entry per externalDashboardId that resolved to a known dashboard
   */
  @Transactional(readOnly = true)
  public List<DashboardDatasetBatchItem> getDashboardDatasets(List<String> externalDashboardIds) {
    return dashboardRepository.findByExternalDashboardIdIn(externalDashboardIds).stream()
        .map(DashboardDatasetService::toBatchItem)
        .toList();
  }

  private static DashboardDatasetBatchItem toBatchItem(Dashboard dashboard) {
    return DashboardDatasetBatchItem.builder()
        .externalDashboardId(dashboard.getExternalDashboardId())
        .civitasDatasetId(dashboard.getDataSet().getId())
        .build();
  }
}

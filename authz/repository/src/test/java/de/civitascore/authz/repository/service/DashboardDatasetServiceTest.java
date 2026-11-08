package de.civitascore.authz.repository.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.authz.repository.data.DashboardRepository;
import de.civitascore.authz.repository.model.dto.DashboardDatasetBatchItem;
import de.civitascore.authz.repository.model.dto.DashboardDatasetResponse;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardDatasetServiceTest {

  @Mock private DashboardRepository dashboardRepository;

  @InjectMocks private DashboardDatasetService dashboardDatasetService;

  private static final String EXTERNAL_DASHBOARD_ID = "42";
  private static final UUID DATASET_ID = UUID.randomUUID();

  private static Dashboard dashboardFor(String externalDashboardId, UUID datasetId) {
    DataSet dataSet = new DataSet();
    dataSet.setId(datasetId);
    Dashboard dashboard = new Dashboard();
    dashboard.setExternalDashboardId(externalDashboardId);
    dashboard.setDataSet(dataSet);
    return dashboard;
  }

  @Nested
  @DisplayName("getDashboardDataset")
  class GetDashboardDataset {

    @Test
    @DisplayName("returns empty when no dashboard has this external id")
    void unknownExternalId_returnsEmpty() {
      when(dashboardRepository.findByExternalDashboardId(EXTERNAL_DASHBOARD_ID))
          .thenReturn(Optional.empty());

      Optional<DashboardDatasetResponse> result =
          dashboardDatasetService.getDashboardDataset(EXTERNAL_DASHBOARD_ID);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns the dataset id when the dashboard exists")
    void knownExternalId_returnsDatasetId() {
      when(dashboardRepository.findByExternalDashboardId(EXTERNAL_DASHBOARD_ID))
          .thenReturn(Optional.of(dashboardFor(EXTERNAL_DASHBOARD_ID, DATASET_ID)));

      Optional<DashboardDatasetResponse> result =
          dashboardDatasetService.getDashboardDataset(EXTERNAL_DASHBOARD_ID);

      assertThat(result).isPresent();
      assertThat(result.get().getCivitasDatasetId()).isEqualTo(DATASET_ID);
    }
  }

  @Nested
  @DisplayName("getDashboardDatasets (batch)")
  class GetDashboardDatasets {

    @Test
    @DisplayName("returns one item per resolvable external id")
    void mixOfKnownAndUnknown_returnsOnlyKnown() {
      UUID otherDatasetId = UUID.randomUUID();
      when(dashboardRepository.findByExternalDashboardIdIn(List.of("42", "43", "99")))
          .thenReturn(List.of(dashboardFor("42", DATASET_ID), dashboardFor("43", otherDatasetId)));

      List<DashboardDatasetBatchItem> result =
          dashboardDatasetService.getDashboardDatasets(List.of("42", "43", "99"));

      assertThat(result)
          .hasSize(2)
          .extracting(DashboardDatasetBatchItem::getExternalDashboardId)
          .containsExactlyInAnyOrder("42", "43");
    }

    @Test
    @DisplayName("returns an empty list when none of the requested ids resolve")
    void noneResolve_returnsEmptyList() {
      when(dashboardRepository.findByExternalDashboardIdIn(List.of("99"))).thenReturn(List.of());

      List<DashboardDatasetBatchItem> result =
          dashboardDatasetService.getDashboardDatasets(List.of("99"));

      assertThat(result).isEmpty();
    }
  }
}

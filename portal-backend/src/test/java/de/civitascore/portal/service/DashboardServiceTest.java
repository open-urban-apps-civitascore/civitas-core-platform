package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DashboardSource;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  @Mock private DashboardRepository dashboardRepository;
  @Mock private DashboardMapper dashboardMapper;
  @Mock private DataSetRepository dataSetRepository;
  @InjectMocks private DashboardService service;

  private UUID datasetId;
  private DataSet dataset;

  @BeforeEach
  void setUp() {
    datasetId = UUID.randomUUID();
    dataset = new DataSet();
    dataset.setId(datasetId);
  }

  @Test
  void postConvertToEntityResolvesDataset() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDatasetId(datasetId);
    when(dataSetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));

    Dashboard result = service.postConvertToEntity(new Dashboard(), input);

    assertThat(result.getDataset()).isEqualTo(dataset);
    assertThat(result.isActive()).isTrue();
  }

  @Test
  void postConvertToEntityOnUpdatePreservesActiveState() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDatasetId(datasetId);
    when(dataSetRepository.findById(datasetId)).thenReturn(Optional.of(dataset));
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID()); // non-null id => update path
    existing.setActive(false);

    Dashboard result = service.postConvertToEntity(existing, input);

    // An update must not reset active back to true — the engine-sync flow owns that flag.
    assertThat(result.isActive()).isFalse();
  }

  @Test
  void postConvertToEntityThrowsWhenDatasetMissing() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDatasetId(datasetId);
    when(dataSetRepository.findById(datasetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.postConvertToEntity(new Dashboard(), input))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void findByIdAndDatasetThrowsOnMismatch() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataset(dataset);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThatThrownBy(() -> service.findByIdAndDatasetOrThrow(dashboardId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void findByIdAndDatasetReturnsWhenDatasetMatches() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataset(dataset);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThat(service.findByIdAndDatasetOrThrow(dashboardId, datasetId)).isSameAs(dashboard);
  }

  @Test
  void findByIdAndDatasetThrowsWhenDashboardHasNoDataset() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId); // dataset left null
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThatThrownBy(() -> service.findByIdAndDatasetOrThrow(dashboardId, datasetId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void preProcessUpdateRejectsRebindingToAnotherDataset() {
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID());
    existing.setDataset(dataset);
    DashboardInputDTO input = new DashboardInputDTO();
    input.setDatasetId(UUID.randomUUID()); // a dataset other than the one the dashboard is bound to

    assertThatThrownBy(() -> service.preProcessUpdateInput(input, existing))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void preProcessUpdateAcceptsSameDataset() {
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID());
    existing.setDataset(dataset);
    DashboardInputDTO input = new DashboardInputDTO();
    input.setDatasetId(datasetId); // matches the bound dataset (the controller injects this)

    assertThat(service.preProcessUpdateInput(input, existing)).isSameAs(input);
  }

  @Test
  void preSaveRejectsSourceWithForeignMappedDataset() {
    Dashboard dashboard = new Dashboard();
    dashboard.setDataset(dataset);
    DashboardSource foreign = new DashboardSource();
    foreign.setMappedDatasetId(
        UUID.randomUUID()); // anchored to a different dataset than the dashboard
    dashboard.setSources(Set.of(foreign));

    assertThatThrownBy(() -> service.preSave(dashboard)).isInstanceOf(InvalidInputException.class);
  }

  @Test
  void preSaveAcceptsSourceMatchingDataset() {
    Dashboard dashboard = new Dashboard();
    dashboard.setDataset(dataset);
    DashboardSource matching = new DashboardSource();
    matching.setMappedDatasetId(datasetId);
    dashboard.setSources(Set.of(matching));

    Dashboard result = service.preSave(dashboard);
    assertThat(result).isSameAs(dashboard);
  }

  @Test
  void deleteDelegatesToRepositoryRemoval() {
    // The service adds no soft-delete override, so deletion delegates straight to the repository's
    // physical removal. The hard-delete behaviour itself is pinned by the integration tests.
    UUID dashboardId = UUID.randomUUID();
    when(dashboardRepository.existsById(dashboardId)).thenReturn(true);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.empty());

    service.deleteById(dashboardId);

    verify(dashboardRepository).deleteById(dashboardId);
  }
}

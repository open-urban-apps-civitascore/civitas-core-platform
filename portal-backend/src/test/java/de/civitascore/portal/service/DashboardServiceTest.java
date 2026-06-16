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

  private UUID dataSetId;
  private DataSet dataSet;

  @BeforeEach
  void setUp() {
    dataSetId = UUID.randomUUID();
    dataSet = new DataSet();
    dataSet.setId(dataSetId);
  }

  @Test
  void postConvertToEntityResolvesDataSet() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDataSetId(dataSetId);
    when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));

    Dashboard result = service.postConvertToEntity(new Dashboard(), input);

    assertThat(result.getDataSet()).isEqualTo(dataSet);
    assertThat(result.isActive()).isTrue();
  }

  @Test
  void postConvertToEntityOnUpdatePreservesActiveState() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDataSetId(dataSetId);
    when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID()); // non-null id => update path
    existing.setActive(false);

    Dashboard result = service.postConvertToEntity(existing, input);

    // An update must not reset active back to true — the engine-sync flow owns that flag.
    assertThat(result.isActive()).isFalse();
  }

  @Test
  void postConvertToEntityThrowsWhenDataSetMissing() {
    DashboardInputDTO input = new DashboardInputDTO();
    input.setName("Traffic");
    input.setDataSetId(dataSetId);
    when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.postConvertToEntity(new Dashboard(), input))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void findByIdAndDataSetThrowsOnMismatch() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataSet(dataSet);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThatThrownBy(() -> service.findByIdAndDataSetOrThrow(dashboardId, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void findByIdAndDataSetReturnsWhenDataSetMatches() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataSet(dataSet);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThat(service.findByIdAndDataSetOrThrow(dashboardId, dataSetId)).isSameAs(dashboard);
  }

  @Test
  void findByIdAndDataSetThrowsWhenDashboardHasNoDataSet() {
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId); // dataSet left null
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    assertThatThrownBy(() -> service.findByIdAndDataSetOrThrow(dashboardId, dataSetId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void preProcessUpdateRejectsRebindingToAnotherDataSet() {
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID());
    existing.setDataSet(dataSet);
    DashboardInputDTO input = new DashboardInputDTO();
    input.setDataSetId(UUID.randomUUID()); // a dataSet other than the one the dashboard is bound to

    assertThatThrownBy(() -> service.preProcessUpdateInput(input, existing))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void preProcessUpdateAcceptsSameDataSet() {
    Dashboard existing = new Dashboard();
    existing.setId(UUID.randomUUID());
    existing.setDataSet(dataSet);
    DashboardInputDTO input = new DashboardInputDTO();
    input.setDataSetId(dataSetId); // matches the bound dataSet (the controller injects this)

    assertThat(service.preProcessUpdateInput(input, existing)).isSameAs(input);
  }

  @Test
  void preSaveRejectsSourceWithForeignMappedDataSet() {
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);
    DashboardSource foreign = new DashboardSource();
    foreign.setMappedDatasetId(
        UUID.randomUUID()); // anchored to a different dataSet than the dashboard
    dashboard.setSources(Set.of(foreign));

    assertThatThrownBy(() -> service.preSave(dashboard)).isInstanceOf(InvalidInputException.class);
  }

  @Test
  void preSaveAcceptsSourceMatchingDataSet() {
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);
    DashboardSource matching = new DashboardSource();
    matching.setMappedDatasetId(dataSetId);
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

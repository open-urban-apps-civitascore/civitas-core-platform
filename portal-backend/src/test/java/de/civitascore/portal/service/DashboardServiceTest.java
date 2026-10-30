package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
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
  @Mock private DataSetSagaPublisher sagaPublisher;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @InjectMocks private DashboardService service;

  private UUID dataSetId;
  private DataSet dataSet;

  @BeforeEach
  void setUp() {
    dataSetId = UUID.randomUUID();
    dataSet = new DataSet();
    dataSet.setId(dataSetId);
    dataSet.setManifestLogicalUrn("urn:core:platform:civitas:dataset:common:test");
    lenient()
        .when(
            modelRegistryGateway.storePayload(
                eq(PayloadKind.DASHBOARD), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            new ModelPin(
                "urn:core:platform:civitas:dashboard:common:test",
                "urn:core:platform:civitas:dashboard:common:test:1.0.0",
                "1.0.0"));
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
  void postSaveFiresUpdateSagaWhenDataSetIsAvailable() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setProjectId("apisix-project-1"); // has engine infrastructure
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);

    service.postSave(dashboard, new DashboardInputDTO());

    assertThat(dataSet.getPendingSagaType()).isEqualTo(PendingSagaType.UPDATE);
    verify(dataSetRepository).save(dataSet);
    verify(sagaPublisher).publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  @Test
  void postSaveSkipsSagaForDraftDataSet() {
    dataSet.setDataSetStatus(DataSetStatus.DRAFT);
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);

    service.postSave(dashboard, new DashboardInputDTO());

    assertThat(dataSet.getPendingSagaType()).isNull();
    verify(sagaPublisher, never()).publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  @Test
  void postSaveRejectsDataSetWithoutManifestAsInvalidInput() {
    dataSet.setManifestLogicalUrn(null);
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);

    assertThatThrownBy(() -> service.postSave(dashboard, new DashboardInputDTO()))
        .isInstanceOf(InvalidInputException.class);
    verify(modelRegistryGateway, never())
        .storePayload(eq(PayloadKind.DASHBOARD), any(), any(), any(), any(), any(), any());
  }

  @Test
  void postSaveSkipsSagaWhenAvailableButProjectIdNull() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    // projectId left null: released but no engine infrastructure yet — nothing to sync.
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);

    service.postSave(dashboard, new DashboardInputDTO());

    assertThat(dataSet.getPendingSagaType()).isNull();
    verify(sagaPublisher, never()).publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  @Test
  void postSaveRejectsChangeWhileSagaInFlight() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setProjectId("apisix-project-1");
    dataSet.setPendingSagaType(PendingSagaType.CREATE); // a saga is already running
    Dashboard dashboard = new Dashboard();
    dashboard.setDataSet(dataSet);

    assertThatThrownBy(() -> service.postSave(dashboard, new DashboardInputDTO()))
        .isInstanceOf(ResourceInUseException.class);
    verify(modelRegistryGateway, never())
        .storePayload(eq(PayloadKind.DASHBOARD), any(), any(), any(), any(), any(), any());
    verify(sagaPublisher, never()).publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  @Test
  void deleteFiresUpdateSagaWhenDataSetIsAvailable() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setProjectId("apisix-project-1");
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataSet(dataSet);
    when(dashboardRepository.existsById(dashboardId)).thenReturn(true);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    service.deleteById(dashboardId);

    verify(dashboardRepository).deleteById(dashboardId);
    verify(sagaPublisher).publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  @Test
  void deleteRejectedWhileSagaInFlight() {
    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setProjectId("apisix-project-1");
    dataSet.setPendingSagaType(PendingSagaType.UPDATE); // a saga is already running
    UUID dashboardId = UUID.randomUUID();
    Dashboard dashboard = new Dashboard();
    dashboard.setId(dashboardId);
    dashboard.setDataSet(dataSet);
    when(dashboardRepository.existsById(dashboardId)).thenReturn(true);
    when(dashboardRepository.findById(dashboardId)).thenReturn(Optional.of(dashboard));

    // The guard runs in preProcessDelete, so a refused delete must leave the row intact.
    assertThatThrownBy(() -> service.deleteById(dashboardId))
        .isInstanceOf(ResourceInUseException.class);
    verify(dashboardRepository, never()).deleteById(dashboardId);
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

package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetService Tests")
class DataSetServiceTest {

  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSetMapper dataSetMapper;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private DistributionService distributionService;
  @Mock private DataSetSagaPublisher sagaPublisher;

  private DataSetService createService() {
    return new DataSetService(
        dataSetRepository, dataSetMapper, assignmentFactory, distributionService, sagaPublisher);
  }

  private DataSet readyDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setDataSetStatus(DataSetStatus.READY);
    ds.setPipelines(new java.util.HashSet<>());
    ds.setDistributions(new HashSet<>());
    return ds;
  }

  private DataSet availableDataSet(UUID id) {
    DataSet ds = readyDataSet(id);
    ds.setDataSetStatus(DataSetStatus.AVAILABLE);
    ds.setProjectId("proj-1");
    ds.setRouteId("route-1");
    ds.setServiceId("svc-1");
    ds.setPublicUrl("https://example.com");
    ds.setPipelineIds(List.of("pipe-1"));
    return ds;
  }

  private DataSet draftDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setName("test-dataset");
    ds.setDescription("test description");
    ds.setDataSetStatus(DataSetStatus.DRAFT);
    ds.setPipelines(new HashSet<>());
    ds.setDistributions(new HashSet<>());
    return ds;
  }

  @Nested
  @DisplayName("stage()")
  class StageTests {

    @Test
    @DisplayName("stages dataset with feed-in pipeline (has datasources)")
    void stagesWithDatasources() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(List.of(new de.civitascore.portal.model.entity.DataSource())));
      ds.getPipelines().add(p);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("stages dataset with provide pipeline (has APIs, no datasources)")
    void stagesWithApisOnly() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      Pipeline p = new Pipeline();
      p.setApis(List.of("/v1.1/Things"));
      ds.getPipelines().add(p);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(distributionService.createFromApiUrlAndDataSet(any(), any()))
          .thenReturn(new Distribution());

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("rejects pipeline with neither datasources nor APIs")
    void rejectsEmptyPipeline() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      ds.getPipelines().add(new Pipeline());

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSources or APIs");
    }

    @Test
    @DisplayName("rejects dataset without pipelines")
    void rejectsNoPipelines() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Pipeline");
    }
  }

  @Nested
  @DisplayName("release()")
  class ReleaseTests {

    @Test
    @DisplayName("throws when dataset not found")
    void throwsWhenDatasetNotFound() {
      UUID id = UUID.randomUUID();
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("throws when dataset not in READY status")
    void throwsWhenNotReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("READY");
    }

    @Test
    @DisplayName("sets status to AVAILABLE, pendingSagaType to CREATE, publishes trigger")
    void setsStatusAndPublishesTrigger() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetService service = createService();
      DataSet result = service.release(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.CREATE);
      verify(sagaPublisher).publishCreateRequested(result);
    }
  }

  @Nested
  @DisplayName("unrelease()")
  class UnreleaseTests {

    @Test
    @DisplayName("throws when dataset not in AVAILABLE status")
    void throwsWhenNotAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unrelease(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE");
    }

    @Test
    @DisplayName("throws ResourceInUseException when saga already in-flight")
    void throwsWhenSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unrelease(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight");
    }

    @Test
    @DisplayName("sets pendingSagaType to DELETE and publishes trigger")
    void setsPendingDeleteAndPublishes() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetService service = createService();
      DataSet result = service.unrelease(id);

      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.DELETE);
      verify(sagaPublisher).publishDeleteRequested(result);
    }
  }

  @Nested
  @DisplayName("updateReleasedMeta()")
  class UpdateReleasedMetaTests {

    @Test
    @DisplayName("throws ResourceInUseException when CREATE saga is in-flight")
    void throwsWhenCreateSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining("CREATE");
    }

    @Test
    @DisplayName("throws ResourceInUseException when UPDATE saga is in-flight")
    void throwsWhenUpdateSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining("UPDATE");
    }

    @Test
    @DisplayName("throws ResourceInUseException when DELETE saga is in-flight")
    void throwsWhenDeleteSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight")
          .hasMessageContaining("DELETE");
    }

    @Test
    @DisplayName("allows update when no saga is pending")
    void allowsUpdateWhenNoSagaPending() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().updateReleasedMeta(id, input);
      assertThat(result).isNotNull();
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }
  }

  @Nested
  @DisplayName("handleSagaCompleted()")
  class HandleSagaCompletedTests {

    @Test
    @DisplayName("CREATE: persists infrastructure fields and clears pendingSagaType")
    void createPersistsInfrastructure() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              "route-1",
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getProjectId()).isEqualTo("proj-1");
      assertThat(persisted.getFrostBaseUrl()).isEqualTo("https://frost.example.com");
      assertThat(persisted.getRouteId()).isEqualTo("route-1");
      assertThat(persisted.getServiceId()).isEqualTo("svc-1");
      assertThat(persisted.getPublicUrl()).isEqualTo("https://public.example.com");
      assertThat(persisted.getPipelineIds()).containsExactly("pipe-1");
      assertThat(persisted.getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("DELETE: clears infrastructure fields and reverts to READY")
    void deleteClearsAndReverts() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      Distribution autoDist = new Distribution();
      autoDist.setAutoGenerated(true);
      ds.getDistributions().add(autoDist);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(persisted.getDistributions()).isEmpty();
      assertThat(persisted.getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("CREATE: regenerates distributions when removed by prior DELETE saga")
    void createRegeneratesDistributionsWhenMissing() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);

      Pipeline pipeline = new Pipeline();
      pipeline.setApis(List.of("/v1.1/Things", "/v1.1/Observations"));
      ds.getPipelines().add(pipeline);

      Distribution createdDist1 = new Distribution();
      createdDist1.setAutoGenerated(true);
      createdDist1.setAccessUrl("/Things");
      Distribution createdDist2 = new Distribution();
      createdDist2.setAutoGenerated(true);
      createdDist2.setAccessUrl("/Observations");

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(distributionService.createFromApiUrlAndDataSet("/v1.1/Things", ds))
          .thenReturn(createdDist1);
      when(distributionService.createFromApiUrlAndDataSet("/v1.1/Observations", ds))
          .thenReturn(createdDist2);

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              "route-1",
              "svc-1",
              "https://public.example.com/datasets/" + id,
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getDistributions()).hasSize(2);
      assertThat(persisted.getDistributions())
          .extracting(Distribution::getAccessUrl)
          .containsExactlyInAnyOrder(
              "https://public.example.com/datasets/" + id + "/Things",
              "https://public.example.com/datasets/" + id + "/Observations");
    }

    @Test
    @DisplayName(
        "CREATE: regenerates auto-generated distributions even when manual distributions exist")
    void createRegeneratesWhenOnlyManualDistributionsRemain() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);

      Pipeline pipeline = new Pipeline();
      pipeline.setApis(List.of("/v1.1/Things"));
      ds.getPipelines().add(pipeline);

      Distribution manualDist = new Distribution();
      manualDist.setAutoGenerated(false);
      manualDist.setAccessUrl("https://manual.example.com/data");
      ds.getDistributions().add(manualDist);

      Distribution createdDist = new Distribution();
      createdDist.setAutoGenerated(true);
      createdDist.setAccessUrl("/Things");

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(distributionService.createFromApiUrlAndDataSet("/v1.1/Things", ds))
          .thenReturn(createdDist);

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              "route-1",
              "svc-1",
              "https://public.example.com/datasets/" + id,
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      DataSet persisted = saved.getValue();
      assertThat(persisted.getDistributions()).hasSize(2);
      assertThat(persisted.getDistributions())
          .extracting(Distribution::getAccessUrl)
          .containsExactlyInAnyOrder(
              "https://manual.example.com/data",
              "https://public.example.com/datasets/" + id + "/Things");
    }

    @Test
    @DisplayName("no pending saga: skips save (duplicate delivery)")
    void noPendingSagaSkipsSave() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(null);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      verify(dataSetRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("handleSagaFailed()")
  class HandleSagaFailedTests {

    @Test
    @DisplayName("CREATE failure: reverts status to READY")
    void createFailureRevertsToReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "FROST", "timeout", true);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }
  }

  @Nested
  @DisplayName("updateDistributionUrls()")
  class UpdateDistributionUrlsTests {

    @Test
    @DisplayName("prepends publicUrl to distribution accessUrl")
    void prependsPublicUrlToAccessUrl() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);

      Distribution dist = new Distribution();
      dist.setAutoGenerated(true);
      // accessUrl already has version prefix stripped by DistributionService
      dist.setAccessUrl("/Things");
      ds.getDistributions().add(dist);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              "route-1",
              "svc-1",
              "https://public.example.com/datasets/" + id,
              List.of(),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      String accessUrl = saved.getValue().getDistributions().iterator().next().getAccessUrl();
      assertThat(accessUrl).isEqualTo("https://public.example.com/datasets/" + id + "/Things");
    }

    @Test
    @DisplayName("prepends publicUrl to bare root accessUrl")
    void prependsPublicUrlToRootAccessUrl() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);

      Distribution dist = new Distribution();
      dist.setAutoGenerated(true);
      // Bare base URL (e.g. "http://frost:8080/FROST-Server/v1.1") stripped to "/"
      dist.setAccessUrl("/");
      ds.getDistributions().add(dist);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              "route-1",
              "svc-1",
              "https://public.example.com/datasets/" + id,
              List.of(),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      String accessUrl = saved.getValue().getDistributions().iterator().next().getAccessUrl();
      assertThat(accessUrl).isEqualTo("https://public.example.com/datasets/" + id + "/");
    }
  }
}

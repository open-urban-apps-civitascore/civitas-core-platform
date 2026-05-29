package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetService Tests")
class DataSetServiceTest {

  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSetMapper dataSetMapper;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private DataSetSagaPublisher sagaPublisher;

  private DataSetService createService() {
    return new DataSetService(dataSetRepository, dataSetMapper, assignmentFactory, sagaPublisher);
  }

  private DataSet readyDataSet(UUID id) {
    DataSet ds = new DataSet();
    ds.setId(id);
    ds.setDataSetStatus(DataSetStatus.READY);
    ds.setPipelines(new HashSet<>());
    NamedApi api = new NamedApi();
    api.setName("Traffic Sensor Readings");
    api.setSlug("traffic");
    api.setStandard(ApiStandard.STA);
    ds.setNamedApis(new HashSet<>(Set.of(api)));
    return ds;
  }

  private DataSet availableDataSet(UUID id) {
    DataSet ds = readyDataSet(id);
    ds.setDataSetStatus(DataSetStatus.AVAILABLE);
    ds.setProjectId("proj-1");
    ds.getNamedApis().forEach(api -> api.setRouteId("route-1"));
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
      p.setDataSources(new HashSet<>(List.of(new DataSource())));
      ds.getPipelines().add(p);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    @Test
    @DisplayName("rejects pipeline without datasources")
    void rejectsEmptyPipeline() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      ds.getPipelines().add(new Pipeline());

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().stage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("DataSources");
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

    @Test
    @DisplayName("stages dataset with valid named APIs")
    void stagesWithValidNamedApis() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSetWithPipeline(id);
      NamedApi api = new NamedApi();
      api.setName("Traffic");
      api.setSlug("traffic");
      api.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(api)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().stage(id);
      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
    }

    /** A DRAFT dataset that passes the pipeline precondition of {@code stage()}. */
    private DataSet draftDataSetWithPipeline(UUID id) {
      DataSet ds = draftDataSet(id);
      Pipeline p = new Pipeline();
      p.setDataSources(new HashSet<>(List.of(new DataSource())));
      ds.getPipelines().add(p);
      return ds;
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

    @Test
    @DisplayName(
        "rejects any non-null namedApis on /released/meta (concept #1379/#1384 immutability)")
    void rejectsAnyNamedApisOnReleased() {
      // The contract is that namedApis cannot appear at all on this endpoint — even a no-op
      // round-trip of the existing list is rejected, not just diffs.
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      NamedApiInputDTO roundTripped = new NamedApiInputDTO();
      roundTripped.setName("Traffic Sensor Readings");
      roundTripped.setSlug("traffic");
      roundTripped.setStandard(ApiStandard.STA);
      input.setNamedApis(List.of(roundTripped));

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be changed")
          .hasMessageContaining("AVAILABLE");

      // Pins the ordering contract: the namedApis guard fires BEFORE the saga trigger. Without
      // this, a regression that placed the check after publishUpdateRequested would leak an
      // UPDATE event for invalid input.
      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("rejects an empty namedApis list too (the field is forbidden, not just changes)")
    void rejectsEmptyNamedApisListOnReleased() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setNamedApis(List.of());

      assertThatThrownBy(() -> createService().updateReleasedMeta(id, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("cannot be changed")
          .hasMessageContaining("READY");

      verify(sagaPublisher, never()).publishUpdateRequested(any(), any());
    }

    @Test
    @DisplayName("allows updateReleasedMeta with namedApis omitted (PATCH semantics)")
    void allowsOmittedNamedApis() {
      UUID id = UUID.randomUUID();
      // availableDataSet seeds a NamedApi with slug "traffic"; the omit-means-unchanged
      // contract must leave that collection untouched.
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      // input.getNamedApis() stays null

      DataSet result = createService().updateReleasedMeta(id, input);
      assertThat(result.getNamedApis()).extracting(NamedApi::getSlug).containsExactly("traffic");
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
              Map.of("traffic", "route-1"),
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
      assertThat(persisted.getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-1"));
      assertThat(persisted.getServiceId()).isEqualTo("svc-1");
      assertThat(persisted.getPublicUrl()).isEqualTo("https://public.example.com");
      assertThat(persisted.getPipelineIds()).containsExactly("pipe-1");
      assertThat(persisted.getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("CREATE: writes per-slug routeIds across multiple named APIs")
    void createWritesMultipleRouteIds() {
      UUID id = UUID.randomUUID();
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setPipelines(new HashSet<>());
      NamedApi traffic = new NamedApi();
      traffic.setName("Traffic");
      traffic.setSlug("traffic");
      traffic.setStandard(ApiStandard.STA);
      NamedApi weather = new NamedApi();
      weather.setName("Weather");
      weather.setSlug("weather");
      weather.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(traffic, weather)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1", "weather", "route-2"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactlyInAnyOrder(tuple("traffic", "route-1"), tuple("weather", "route-2"));
    }

    @Test
    @DisplayName("CREATE: partial routeIds map leaves unmatched entries with null routeId")
    void createPartialRouteIdsLeavesEntriesUnchanged() {
      UUID id = UUID.randomUUID();
      DataSet ds = new DataSet();
      ds.setId(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setPipelines(new HashSet<>());
      NamedApi traffic = new NamedApi();
      traffic.setName("Traffic");
      traffic.setSlug("traffic");
      traffic.setStandard(ApiStandard.STA);
      NamedApi weather = new NamedApi();
      weather.setName("Weather");
      weather.setSlug("weather");
      weather.setStandard(ApiStandard.STA);
      ds.setNamedApis(new HashSet<>(Set.of(traffic, weather)));

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Saga returned only one of the two slugs
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactlyInAnyOrder(tuple("traffic", "route-1"), tuple("weather", null));
    }

    @Test
    @DisplayName("CREATE: empty routeIds map leaves entity routeIds untouched")
    void createEmptyRouteIdsMapIsNoOp() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id); // routeId already "route-1" via helper
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // routeIds present but empty — distinct from null (which means "no infrastructure step
      // ran"). The branch must skip without overwriting anything.
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(), null, null, Map.of(), null, null, null, null, null, null);

      createService().handleSagaCompleted(id, result);

      assertThat(ds.getNamedApis()).extracting(NamedApi::getRouteId).containsExactly("route-1");
    }

    @Test
    @DisplayName("CREATE: incoming routeId replaces a previously-set routeId for the same slug")
    void createIncomingRouteIdReplacesExisting() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      // Simulate: entity already has a routeId from a previous saga response.
      ds.getNamedApis().forEach(api -> api.setRouteId("route-old"));
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-NEW"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      Logger serviceLogger = (Logger) LoggerFactory.getLogger(DataSetService.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      serviceLogger.addAppender(appender);
      try {
        createService().handleSagaCompleted(id, result);
      } finally {
        serviceLogger.detachAppender(appender);
      }

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-NEW"));
      assertThat(appender.list)
          .anyMatch(
              event ->
                  event.getLevel() == Level.ERROR
                      && event.getFormattedMessage().contains("drift=replaced-routeid")
                      && event.getFormattedMessage().contains("traffic")
                      && event.getFormattedMessage().contains("route-old")
                      && event.getFormattedMessage().contains("route-NEW"));
    }

    @Test
    @DisplayName("CREATE: orphan slug in saga result is logged as drift but does not throw")
    void createOrphanSlugSilentlySkipped() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Saga returned a slug that doesn't exist on the entity (drift)
      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("traffic", "route-1", "ghost", "route-orphan"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      createService().handleSagaCompleted(id, result);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      // The matching slug still gets its routeId; orphan is ignored without throwing.
      assertThat(saved.getValue().getNamedApis())
          .extracting(NamedApi::getSlug, NamedApi::getRouteId)
          .containsExactly(tuple("traffic", "route-1"));
    }

    @Test
    @DisplayName(
        "CREATE: routeIds returned for entity without namedApis is logged as drift but does not"
            + " throw")
    void createUnexpectedRouteIdsLoggedAsDrift() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setNamedApis(new HashSet<>());
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      SagaResultPayload result =
          new SagaResultPayload(
              id.toString(),
              "proj-1",
              "https://frost.example.com",
              Map.of("ghost", "route-orphan"),
              "svc-1",
              "https://public.example.com",
              List.of("pipe-1"),
              null,
              null,
              null);

      Logger serviceLogger = (Logger) LoggerFactory.getLogger(DataSetService.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      serviceLogger.addAppender(appender);
      try {
        createService().handleSagaCompleted(id, result);
      } finally {
        serviceLogger.detachAppender(appender);
      }

      assertThat(appender.list)
          .anyMatch(
              event ->
                  event.getLevel() == Level.ERROR
                      && event.getFormattedMessage().contains("drift=unexpected-routeids")
                      && event.getFormattedMessage().contains("ghost"));
    }

    @Test
    @DisplayName("DELETE: clears infrastructure fields and reverts to READY")
    void deleteClearsAndReverts() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
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
      assertThat(persisted.getPendingSagaType()).isNull();
      assertThat(persisted.getProjectId()).isNull();
      assertThat(persisted.getServiceId()).isNull();
      assertThat(persisted.getPublicUrl()).isNull();
      assertThat(persisted.getPipelineIds()).isNull();
      assertThat(persisted.getNamedApis()).allMatch(api -> api.getRouteId() == null);
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
}

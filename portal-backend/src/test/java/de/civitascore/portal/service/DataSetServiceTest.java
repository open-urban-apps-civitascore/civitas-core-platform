package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.security.AllowedScopes;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetService Tests")
class DataSetServiceTest {

  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSetMapper dataSetMapper;
  @Mock private DataPoolRepository dataPoolRepository;
  @Mock private AssignmentFactory assignmentFactory;
  @Mock private DataSetSagaPublisher sagaPublisher;
  @Mock private ObjectProvider<AllowedScopes> allowedScopesProvider;

  private DataSetService createService() {
    // Default to TENANT wildcard so the F4 target-pool check passes for existing pool-setting
    // tests;
    // F4-specific tests override allowedScopesProvider.getObject() after calling createService().
    lenient().when(allowedScopesProvider.getObject()).thenReturn(wildcardScopes());
    return new DataSetService(
        dataSetRepository,
        dataSinkRepository,
        dataSetMapper,
        dataPoolRepository,
        assignmentFactory,
        sagaPublisher,
        allowedScopesProvider);
  }

  private static AllowedScopes wildcardScopes() {
    AllowedScopes scopes = new AllowedScopes();
    scopes.setWildcard();
    return scopes;
  }

  private static AllowedScopes poolScopes(UUID... poolIds) {
    AllowedScopes scopes = new AllowedScopes();
    scopes.setPoolIds(Set.of(poolIds));
    return scopes;
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
    ds.setFrostBaseUrl("https://frost.example.com/Projects(1)");
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
  @DisplayName("unstage()")
  class UnstageTests {

    @Test
    @DisplayName("throws when dataset not in READY status")
    void throwsWhenNotReady() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unstage(id))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("READY");
    }

    @Test
    @DisplayName("throws ResourceInUseException when a CREATE saga is in-flight")
    void throwsWhenCreateSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().unstage(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight");
    }

    @Test
    @DisplayName("allows unstage while an UNRELEASE saga is in-flight (AVAILABLE -> DRAFT chain)")
    void allowsUnstageWhileUnreleaseInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().unstage(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UNRELEASE);
    }

    @Test
    @DisplayName("reverts status to DRAFT")
    void revertsToDraft() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet result = createService().unstage(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
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
    @DisplayName("throws ResourceInUseException when a saga is in-flight")
    void throwsWhenSagaInFlight() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findByIdWithPipelineDataSources(id)).thenReturn(Optional.of(ds));

      assertThatThrownBy(() -> createService().release(id))
          .isInstanceOf(ResourceInUseException.class)
          .hasMessageContaining("saga is in-flight");
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
    @DisplayName(
        "optimistically sets status to READY, pendingSagaType to UNRELEASE, publishes trigger")
    void setsReadyAndPendingUnreleaseAndPublishes() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetService service = createService();
      DataSet result = service.unrelease(id);

      assertThat(result.getDataSetStatus()).isEqualTo(DataSetStatus.READY);
      assertThat(result.getPendingSagaType()).isEqualTo(PendingSagaType.UNRELEASE);
      // Infrastructure fields must survive unrelease so the teardown payload carries the resource
      // IDs; the completion callback clears route/pipeline, not unrelease.
      assertThat(result.getProjectId()).isEqualTo("proj-1");
      assertThat(result.getServiceId()).isEqualTo("svc-1");
      verify(sagaPublisher).publishUnreleaseRequested(result);
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
      UUID poolId = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      DataPool pool = new DataPool();
      pool.setId(poolId);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");
      input.setDatapoolId(poolId);

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
    @DisplayName("rejects duplicate named-API slugs with a business 400 (not a DB 500)")
    void rejectsDuplicateNamedApiSlugs() {
      // Two named APIs sharing a slug must be rejected up front; otherwise both reach the DB unique
      // constraint as an opaque 500 (and the slug is the per-named-API route key).
      DataSet entity = new DataSet();
      NamedApiInputDTO first = new NamedApiInputDTO();
      first.setName("Traffic A");
      first.setSlug("traffic");
      first.setStandard(ApiStandard.STA);
      NamedApiInputDTO duplicate = new NamedApiInputDTO();
      duplicate.setName("Traffic B");
      duplicate.setSlug("traffic");
      duplicate.setStandard(ApiStandard.STA);
      DataSetInputDTO input = new DataSetInputDTO();
      input.setNamedApis(List.of(first, duplicate));

      assertThatThrownBy(() -> createService().postConvertToEntity(entity, input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("Duplicate named-API slug")
          .hasMessageContaining("traffic");
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
      assertThat(persisted.isProvisioned()).isTrue();
    }

    @Test
    @DisplayName("UPDATE: a completion that yields a project id marks the dataset provisioned")
    void updateMarksProvisionedWhenSinkExists() {
      UUID id = UUID.randomUUID();
      DataSet ds = readyDataSet(id);
      ds.setDataSetStatus(DataSetStatus.AVAILABLE);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
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
      assertThat(saved.getValue().isProvisioned()).isTrue();
      assertThat(saved.getValue().getPendingSagaType()).isNull();
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
    @DisplayName("UNRELEASE: clears only route/pipeline fields, keeps the sink, stays READY")
    void unreleaseClearsRouteAndPipelineKeepsSink() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      // unrelease set READY optimistically before the saga ran.
      ds.setDataSetStatus(DataSetStatus.READY);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
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
      // Route/consumer-access layer torn down (routes/upstream deleted by DELETE_ROUTE).
      assertThat(persisted.getPipelineIds()).isNull();
      assertThat(persisted.getNamedApis()).allMatch(api -> api.getRouteId() == null);
      assertThat(persisted.getServiceId()).isNull();
      assertThat(persisted.getPublicUrl()).isNull();
      // Data-holding sink references survive so a re-release reuses the existing data.
      assertThat(persisted.getProjectId()).isEqualTo("proj-1");
      assertThat(persisted.getFrostBaseUrl()).isEqualTo(ds.getFrostBaseUrl());
      verify(dataSetRepository, never()).delete(any(DataSet.class));
    }

    @Test
    @DisplayName("UNRELEASE: leaves a user-moved DRAFT status untouched")
    void unreleaseLeavesDraftUntouched() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      // AVAILABLE -> DRAFT chain moved the dataset on to DRAFT while the teardown was running.
      ds.setDataSetStatus(DataSetStatus.DRAFT);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("DELETE: removes the entity after teardown, without a status save")
    void deleteRemovesEntity() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      verify(dataSetRepository).delete(ds);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("CREATE: marks the dataset provisioned")
    void createMarksProvisioned() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.CREATE);
      ds.setProvisioned(false);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService()
          .handleSagaCompleted(
              id,
              new SagaResultPayload(
                  id.toString(), null, null, null, null, null, null, null, null, null));

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().isProvisioned()).isTrue();
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

    @Test
    @DisplayName("UNRELEASE failure: reverts optimistic READY to AVAILABLE, pending cleared")
    void unreleaseFailureRevertsToAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setDataSetStatus(DataSetStatus.READY);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "delete-route", "APISIX down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      // A failed teardown must NOT stay READY — live routes may remain, so undo the optimistic set.
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("UNRELEASE failure: leaves a user-moved DRAFT status untouched")
    void unreleaseFailureLeavesDraftUntouched() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setDataSetStatus(DataSetStatus.DRAFT);
      ds.setPendingSagaType(PendingSagaType.UNRELEASE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "delete-route", "APISIX down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.DRAFT);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("DELETE failure: stays AVAILABLE, pending cleared, entity kept")
    void deleteFailureStaysAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.DELETE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "deprovision-sink", "PostGIS down", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
      verify(dataSetRepository, never()).delete(any(DataSet.class));
    }

    @Test
    @DisplayName("UPDATE failure: stays AVAILABLE, pending cleared")
    void updateFailureStaysAvailable() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(PendingSagaType.UPDATE);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      createService().handleSagaFailed(id, "update-route", "APISIX timeout", false);

      ArgumentCaptor<DataSet> saved = ArgumentCaptor.forClass(DataSet.class);
      verify(dataSetRepository).save(saved.capture());
      assertThat(saved.getValue().getDataSetStatus()).isEqualTo(DataSetStatus.AVAILABLE);
      assertThat(saved.getValue().getPendingSagaType()).isNull();
    }

    @Test
    @DisplayName("no pending saga: skips save (duplicate delivery)")
    void noPendingSagaSkipsSave() {
      UUID id = UUID.randomUUID();
      DataSet ds = availableDataSet(id);
      ds.setPendingSagaType(null);
      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));

      createService().handleSagaFailed(id, "any", "any", false);

      verify(dataSetRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("datapool resolution")
  class DataPoolResolutionTests {

    @Test
    @DisplayName("create throws ResourceNotFoundException when datapoolId points to unknown pool")
    void createRejectsUnknownDatapoolId() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> createService().create(input))
          .isInstanceOf(ResourceNotFoundException.class);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName("create resolves datapoolId and assigns the DataPool to the entity")
    void createAssignsResolvedDataPool() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      DataPool pool = new DataPool();
      pool.setId(poolId);

      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet saved = createService().create(input);
      assertThat(saved.getDataPool()).isSameAs(pool);
    }

    @Test
    @DisplayName("create rejects a target datapool the caller is not authorized for (F4)")
    void createRejectsUnauthorizedTargetPool() {
      UUID poolId = UUID.randomUUID();
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");
      input.setDatapoolId(poolId);

      DataSet entity = draftDataSet(UUID.randomUUID());
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSetService service = createService();
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataPoolRepository.findById(poolId)).thenReturn(Optional.of(pool));
      // Caller is authorized only for a DIFFERENT pool → the target pool is off-limits.
      when(allowedScopesProvider.getObject()).thenReturn(poolScopes(UUID.randomUUID()));

      assertThatThrownBy(() -> service.create(input)).isInstanceOf(AccessDeniedException.class);
      verify(dataSetRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "create with null datapoolId creates dataset without pool (datapoolId is optional)")
    void createWithNullDatapoolIdLeavesDataPoolNull() {
      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("ds");

      DataSet entity = draftDataSet(UUID.randomUUID());
      when(dataSetMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSet saved = createService().create(input);
      assertThat(saved.getDataPool()).isNull();
      verify(dataPoolRepository, never()).findById(any());
    }

    @Test
    @DisplayName("update with null datapoolId unassigns the DataPool from the entity")
    void updateWithNullDatapoolIdUnassignsDataPool() {
      UUID id = UUID.randomUUID();
      DataSet ds = draftDataSet(id);
      DataPool existingPool = new DataPool();
      existingPool.setId(UUID.randomUUID());
      ds.setDataPool(existingPool);

      when(dataSetRepository.findById(id)).thenReturn(Optional.of(ds));
      when(dataSetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      DataSetInputDTO input = new DataSetInputDTO();
      input.setName("updated name");

      DataSet result = createService().update(id, input);
      assertThat(result.getDataPool()).isNull();
      verify(dataPoolRepository, never()).findById(any());
    }
  }
}

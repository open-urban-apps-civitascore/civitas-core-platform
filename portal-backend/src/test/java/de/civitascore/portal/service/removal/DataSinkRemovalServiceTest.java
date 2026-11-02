package de.civitascore.portal.service.removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.kind.ApiKindRegistry;
import de.civitascore.portal.kind.TestKinds;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkState;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class DataSinkRemovalServiceTest {

  private static final String CONFIGURATION_URN =
      "urn:core:platform:civitas:datasink:common:3f2b8c1e-7a4d-4e9b-9c6f-1d2e3f4a5b6c";

  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private LayerRepository layerRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Spy private ApiKindRegistry apiKinds = TestKinds.apiKindRegistry();

  @InjectMocks private DataSinkRemovalService service;

  private DataSet dataSet;
  private Pipeline pipeline;

  @BeforeEach
  void dataSetWithPipeline() {
    dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    pipeline = new Pipeline();
    pipeline.setId(UUID.randomUUID());
    pipeline.setDataSet(dataSet);
    dataSet.getPipelines().add(pipeline);
    TransactionSynchronizationManager.initSynchronization();
  }

  @AfterEach
  void clearSynchronization() {
    TransactionSynchronizationManager.clearSynchronization();
  }

  private static void commit() {
    TransactionSynchronizationUtils.invokeAfterCommit(
        TransactionSynchronizationManager.getSynchronizations());
  }

  @Test
  void prepare_provisionedSinkWithoutConfirmDataLoss_isRefused() {
    DataSink sink = activeSink(DataSinkType.FROST, true);

    assertThatThrownBy(() -> service.prepare(DataSinkChange.deletePipeline(pipeline), false))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            e -> assertThat(e.getResourceId()).isEqualTo(sink.getId()))
        .hasMessageContaining("confirmDataLoss=true");
  }

  @Test
  void prepare_provisionedSinkWithConfirmDataLoss_returnsThePlan() {
    DataSink sink = activeSink(DataSinkType.FROST, true);

    PreparedDataSinkChange prepared =
        service.prepare(DataSinkChange.deletePipeline(pipeline), true);

    assertThat(prepared.plan().deactivatedDataSinks()).containsExactly(sink);
  }

  @Test
  void prepare_unprovisionedSinkWithApi_needsNoConfirmation() {
    DataSink sink = activeSink(DataSinkType.FROST, false);
    NamedApi sta = api(ApiStandard.STA);

    PreparedDataSinkChange prepared =
        service.prepare(DataSinkChange.deletePipeline(pipeline), false);

    assertThat(prepared.plan().deactivatedDataSinks()).containsExactly(sink);
    assertThat(prepared.plan().apis()).containsExactly(sta);
  }

  @Test
  void prepare_deletingASinkAPipelineLinks_isRefusedBeforeTheConfirmation() {
    DataSink sink = activeSink(DataSinkType.FROST, true);

    assertThatThrownBy(() -> service.prepare(DataSinkChange.deleteDataSink(sink), false))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            e -> {
              assertThat(e.getResourceId()).isEqualTo(sink.getId());
              assertThat(e.getBlockedBy()).containsExactly(pipeline.getId().toString());
            });
  }

  @Test
  void prepare_deletingAnUnlinkedSink_returnsThePlan() {
    DataSink sink = activeSink(DataSinkType.FROST, false);
    sink.getPipelines().clear();

    PreparedDataSinkChange prepared = service.prepare(DataSinkChange.deleteDataSink(sink), false);

    assertThat(prepared.plan().deactivatedDataSinks()).containsExactly(sink);
  }

  @Test
  void prepare_linkPipeline_addsTheNewAndRemovesTheDroppedLinks() {
    DataSink dropped = activeSink(DataSinkType.FROST, false);
    DataSink free = unlinkedSink(dataSet);
    when(dataSinkRepository.findAllById(Set.of(free.getId()))).thenReturn(List.of(free));
    when(dataSinkRepository.findByPipelinesId(pipeline.getId())).thenReturn(List.of(dropped));

    PreparedDataSinkChange prepared =
        service.prepare(DataSinkChange.linkPipeline(pipeline, Set.of(free.getId())), false);

    assertThat(prepared.linksToAdd()).containsExactly(free);
    assertThat(prepared.linksToRemove()).containsExactly(dropped);
    assertThat(prepared.plan().deactivatedDataSinks()).containsExactly(dropped);
  }

  @Test
  void prepare_linkPipeline_unknownDataSinkId_isRejected() {
    UUID unknown = UUID.randomUUID();

    assertThatThrownBy(
            () -> service.prepare(DataSinkChange.linkPipeline(pipeline, Set.of(unknown)), false))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("One or more DataSink IDs not found");
  }

  @Test
  void prepare_linkPipeline_pendingDataSink_isRejected() {
    DataSink pending = unlinkedSink(dataSet);
    pending.setState(DataSinkState.PENDING_UNPROVISION);
    when(dataSinkRepository.findAllById(Set.of(pending.getId()))).thenReturn(List.of(pending));

    assertThatThrownBy(
            () ->
                service.prepare(
                    DataSinkChange.linkPipeline(pipeline, Set.of(pending.getId())), false))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("One or more DataSink IDs not found");
  }

  @Test
  void prepare_linkPipeline_dataSinkOfAnotherDataSet_isRejected() {
    DataSet other = new DataSet();
    other.setId(UUID.randomUUID());
    DataSink foreign = unlinkedSink(other);
    when(dataSinkRepository.findAllById(Set.of(foreign.getId()))).thenReturn(List.of(foreign));

    assertThatThrownBy(
            () ->
                service.prepare(
                    DataSinkChange.linkPipeline(pipeline, Set.of(foreign.getId())), false))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("different DataSet");
  }

  @Test
  void prepare_linkPipeline_dataSinkOfAnotherPipeline_isRejected() {
    Pipeline otherPipeline = new Pipeline();
    otherPipeline.setId(UUID.randomUUID());
    DataSink occupied = unlinkedSink(dataSet);
    occupied.getPipelines().add(otherPipeline);
    when(dataSinkRepository.findAllById(Set.of(occupied.getId()))).thenReturn(List.of(occupied));

    assertThatThrownBy(
            () ->
                service.prepare(
                    DataSinkChange.linkPipeline(pipeline, Set.of(occupied.getId())), false))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("already attached to another Pipeline");
  }

  @Test
  void apply_linkChanges_addAndRemoveThePipeline() {
    DataSink dropped = unlinkedSink(dataSet);
    dropped.getPipelines().add(pipeline);
    DataSink added = unlinkedSink(dataSet);

    service.apply(
        new PreparedDataSinkChange(
            DataSinkRemovalPlan.empty(dataSet), pipeline, List.of(added), List.of(dropped)));

    assertThat(added.getPipelines()).containsExactly(pipeline);
    assertThat(dropped.getPipelines()).isEmpty();
  }

  @Test
  void apply_unprovisionedSink_isDeletedAndItsConfigurationOnlyAfterTheCommit() {
    DataSink sink = activeSink(DataSinkType.FROST, false);
    sink.setConfigurationLogicalUrn(CONFIGURATION_URN);

    service.apply(
        PreparedDataSinkChange.removalOnly(
            new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(), List.of())));

    verify(dataSinkRepository).delete(sink);
    verify(modelRegistryGateway, never()).deletePayload(any());
    commit();
    verify(modelRegistryGateway).deletePayload(CONFIGURATION_URN);
  }

  @Test
  void apply_configurationDeleteFailsAfterTheCommit_doesNotFailTheRequest() {
    // A thrown afterCommit reaches the caller as an error for a change that is already committed.
    DataSink sink = activeSink(DataSinkType.FROST, false);
    sink.setConfigurationLogicalUrn(CONFIGURATION_URN);
    doThrow(new IllegalStateException("Model Forge unavailable"))
        .when(modelRegistryGateway)
        .deletePayload(CONFIGURATION_URN);
    service.apply(
        PreparedDataSinkChange.removalOnly(
            new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(), List.of())));

    assertThatCode(DataSinkRemovalServiceTest::commit).doesNotThrowAnyException();
  }

  @Test
  void apply_provisionedSink_staysPendingWithoutLinksAndKeepsItsConfiguration() {
    DataSink sink = activeSink(DataSinkType.POSTGIS, true);
    sink.setConfigurationLogicalUrn(CONFIGURATION_URN);

    service.apply(
        PreparedDataSinkChange.removalOnly(
            new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(), List.of())));

    assertThat(sink.getState()).isEqualTo(DataSinkState.PENDING_UNPROVISION);
    assertThat(sink.getPipelines()).isEmpty();
    verify(dataSinkRepository).save(sink);
    verify(dataSinkRepository, never()).delete(any(DataSink.class));
    verify(modelRegistryGateway, never()).deletePayload(any());
  }

  @Test
  void apply_layersAndApis_areDetachedAndDeleted() {
    DataSink sink = activeSink(DataSinkType.POSTGIS, true);
    NamedApi ows = api(ApiStandard.OWS);
    Layer layer = new Layer();
    layer.setId(UUID.randomUUID());
    layer.setDataSet(dataSet);
    layer.setDataSink(sink);
    layer.setNamedApi(ows);
    sink.getLayers().add(layer);
    dataSet.getLayers().add(layer);

    service.apply(
        PreparedDataSinkChange.removalOnly(
            new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(layer), List.of(ows))));

    assertThat(sink.getLayers()).isEmpty();
    assertThat(dataSet.getLayers()).isEmpty();
    assertThat(dataSet.getNamedApis()).isEmpty();
    verify(layerRepository).delete(layer);
  }

  private static DataSink unlinkedSink(DataSet owner) {
    DataSink sink = new DataSink();
    sink.setId(UUID.randomUUID());
    sink.setDataSet(owner);
    sink.setDataSinkType(DataSinkType.POSTGIS);
    return sink;
  }

  private DataSink activeSink(DataSinkType type, boolean provisioned) {
    DataSink sink = new DataSink();
    sink.setId(UUID.randomUUID());
    sink.setDataSet(dataSet);
    sink.setDataSinkType(type);
    sink.setProvisioned(provisioned);
    sink.getPipelines().add(pipeline);
    lenient()
        .when(dataSinkRepository.findByDataSetIdAndState(dataSet.getId(), DataSinkState.ACTIVE))
        .thenReturn(List.of(sink));
    return sink;
  }

  private NamedApi api(ApiStandard standard) {
    NamedApi api =
        NamedApi.builder().slug(standard.name().toLowerCase()).standard(standard).build();
    api.setId(UUID.randomUUID());
    api.setDataSet(dataSet);
    dataSet.getNamedApis().add(api);
    return api;
  }
}

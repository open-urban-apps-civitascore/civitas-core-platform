package de.civitascore.portal.service.removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import de.civitascore.portal.model.output.DataSinkRemovalImpactDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.RemovalConfirmationRequiredException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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
  }

  @Test
  void planConfirmed_missingConfirmation_throwsWithTheImpact() {
    DataSink sink = activeSink(DataSinkType.FROST, true);
    NamedApi sta = api(ApiStandard.STA);

    assertThatThrownBy(
            () ->
                service.planConfirmed(
                    dataSet, DataSinkRemovalRequest.deletePipeline(pipeline.getId()), Set.of()))
        .isInstanceOfSatisfying(
            RemovalConfirmationRequiredException.class,
            e -> {
              assertThat(e.getImpact().requiresConfirmation()).isTrue();
              assertThat(e.getImpact().apis()).extracting("id").containsExactly(sta.getId());
              assertThat(e.getImpact().dataSinks()).extracting("id").containsExactly(sink.getId());
            });
  }

  @Test
  void planConfirmed_partOfTheNeededIds_throwsWithAllOfThem() {
    DataSink sink = activeSink(DataSinkType.FROST, true);
    NamedApi sta = api(ApiStandard.STA);

    assertThatThrownBy(
            () ->
                service.planConfirmed(
                    dataSet,
                    DataSinkRemovalRequest.deletePipeline(pipeline.getId()),
                    Set.of(sink.getId())))
        .isInstanceOfSatisfying(
            RemovalConfirmationRequiredException.class,
            e ->
                assertThat(e.getImpact().confirmationIds())
                    .containsExactlyInAnyOrder(sink.getId(), sta.getId()));
  }

  @Test
  void planConfirmed_supersetOfTheNeededIds_returnsThePlan() {
    DataSink sink = activeSink(DataSinkType.FROST, true);
    NamedApi sta = api(ApiStandard.STA);

    DataSinkRemovalPlan plan =
        service.planConfirmed(
            dataSet,
            DataSinkRemovalRequest.deletePipeline(pipeline.getId()),
            Set.of(sink.getId(), sta.getId(), UUID.randomUUID()));

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
  }

  @Test
  void planConfirmed_unprovisionedSinkWithoutLayersOrApis_needsNoConfirmation() {
    DataSink sink = activeSink(DataSinkType.FROST, false);

    DataSinkRemovalPlan plan =
        service.planConfirmed(
            dataSet, DataSinkRemovalRequest.deletePipeline(pipeline.getId()), Set.of());

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
  }

  @Test
  void planConfirmed_deletingASinkAPipelineLinks_isRefusedBeforeTheConfirmation() {
    DataSink sink = activeSink(DataSinkType.FROST, true);

    assertThatThrownBy(
            () ->
                service.planConfirmed(
                    dataSet, DataSinkRemovalRequest.deleteDataSink(sink.getId()), Set.of()))
        .isInstanceOfSatisfying(
            ResourceInUseException.class,
            e -> {
              assertThat(e.getResourceId()).isEqualTo(sink.getId());
              assertThat(e.getBlockedBy()).containsExactly(pipeline.getId().toString());
            });
  }

  @Test
  void planConfirmed_deletingAnUnlinkedSink_returnsThePlan() {
    DataSink sink = activeSink(DataSinkType.FROST, false);
    sink.getPipelines().clear();

    DataSinkRemovalPlan plan =
        service.planConfirmed(
            dataSet, DataSinkRemovalRequest.deleteDataSink(sink.getId()), Set.of());

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
  }

  @Test
  void preview_deletingASinkAPipelineLinks_isRefused() {
    DataSink sink = activeSink(DataSinkType.FROST, false);

    assertThatThrownBy(
            () -> service.preview(dataSet, DataSinkRemovalRequest.deleteDataSink(sink.getId())))
        .isInstanceOf(ResourceInUseException.class);
  }

  @Test
  void preview_deletingASinkTogetherWithItsPipeline_listsTheSink() {
    DataSink sink = activeSink(DataSinkType.FROST, false);

    DataSinkRemovalImpactDTO impact =
        service.preview(
            dataSet,
            new DataSinkRemovalRequest(Set.of(pipeline.getId()), Set.of(), Set.of(sink.getId())));

    assertThat(impact.dataSinks()).extracting("id").containsExactly(sink.getId());
  }

  @Test
  void apply_unprovisionedSink_isDeletedWithItsConfiguration() {
    DataSink sink = activeSink(DataSinkType.FROST, false);
    sink.setConfigurationLogicalUrn(CONFIGURATION_URN);

    service.apply(new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(), List.of()));

    verify(dataSinkRepository).delete(sink);
    verify(modelRegistryGateway).deletePayload(CONFIGURATION_URN);
  }

  @Test
  void apply_provisionedSink_staysPendingWithoutLinksAndKeepsItsConfiguration() {
    DataSink sink = activeSink(DataSinkType.POSTGIS, true);
    sink.setConfigurationLogicalUrn(CONFIGURATION_URN);

    service.apply(new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(), List.of()));

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

    service.apply(new DataSinkRemovalPlan(dataSet, List.of(sink), List.of(layer), List.of(ows)));

    assertThat(sink.getLayers()).isEmpty();
    assertThat(dataSet.getLayers()).isEmpty();
    assertThat(dataSet.getNamedApis()).isEmpty();
    verify(layerRepository).delete(layer);
  }

  @Test
  void preview_pipelineOfAnotherDataSet_isNotFound() {
    when(dataSinkRepository.findByDataSetIdAndState(dataSet.getId(), DataSinkState.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.preview(dataSet, DataSinkRemovalRequest.deletePipeline(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void preview_dataSinkThatIsNotActive_isNotFound() {
    when(dataSinkRepository.findByDataSetIdAndState(dataSet.getId(), DataSinkState.ACTIVE))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                service.preview(dataSet, DataSinkRemovalRequest.deleteDataSink(UUID.randomUUID())))
        .isInstanceOf(ResourceNotFoundException.class);
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

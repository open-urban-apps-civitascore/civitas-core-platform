package de.civitascore.portal.service.removal;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.kind.ApiKindRegistry;
import de.civitascore.portal.kind.TestKinds;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataSinkRemovalPlanTest {

  private final ApiKindRegistry apiKinds = TestKinds.apiKindRegistry();
  private final DataSet dataSet = new DataSet();
  private final List<DataSink> sinks = new ArrayList<>();
  private final List<Layer> layers = new ArrayList<>();
  private final List<NamedApi> apis = new ArrayList<>();

  @Test
  void compute_deletedLastPipeline_deactivatesSinkAndTakesItsLayersAndApi() {
    Pipeline pipeline = pipeline();
    DataSink sink = sink(DataSinkType.POSTGIS, true, pipeline);
    NamedApi ows = api(ApiStandard.OWS);
    Layer layer = layer(sink, ows);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
    assertThat(plan.layers()).containsExactly(layer);
    assertThat(plan.apis()).containsExactly(ows);
    assertThat(plan.needsConfirmation())
        .containsExactlyInAnyOrder(sink.getId(), layer.getId(), ows.getId());
  }

  @Test
  void compute_sinkWithAnotherPipeline_staysActive() {
    Pipeline deleted = pipeline();
    DataSink sink = sink(DataSinkType.POSTGIS, true, deleted, pipeline());
    api(ApiStandard.OWS);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(deleted.getId()));

    assertThat(plan.isEmpty()).isTrue();
    assertThat(sink.getPipelines()).hasSize(2);
  }

  @Test
  void compute_sinkWithoutPipelineBefore_staysActiveAndKeepsTheApi() {
    // A Data sink that clients fill without a Pipeline must survive the delete of another one.
    Pipeline pipeline = pipeline();
    DataSink linked = sink(DataSinkType.FROST, false, pipeline);
    DataSink unlinked = sink(DataSinkType.FROST, false);
    api(ApiStandard.STA);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.deactivatedDataSinks()).containsExactly(linked);
    assertThat(plan.deactivatedDataSinks()).doesNotContain(unlinked);
    assertThat(plan.apis()).isEmpty();
  }

  @Test
  void compute_replacedSinkInOneSave_keepsTheApiAndTakesTheOldLayers() {
    Pipeline pipeline = pipeline();
    DataSink old = sink(DataSinkType.POSTGIS, true, pipeline);
    sink(DataSinkType.POSTGIS, false);
    NamedApi ows = api(ApiStandard.OWS);
    Layer oldLayer = layer(old, ows);

    DataSinkRemovalPlan plan =
        compute(
            DataSinkRemovalRequest.removeLinks(
                Set.of(new PipelineDataSinkLink(pipeline.getId(), old.getId()))));

    assertThat(plan.deactivatedDataSinks()).containsExactly(old);
    assertThat(plan.layers()).containsExactly(oldLayer);
    assertThat(plan.apis()).isEmpty();
  }

  @Test
  void compute_directDelete_deactivatesASinkWithoutPipeline() {
    DataSink sink = sink(DataSinkType.FROST, false);
    NamedApi sta = api(ApiStandard.STA);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deleteDataSink(sink.getId()));

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
    assertThat(plan.apis()).containsExactly(sta);
  }

  @Test
  void compute_lastFrostSinkGoes_takesOnlyTheStaApi() {
    Pipeline pipeline = pipeline();
    sink(DataSinkType.FROST, true, pipeline);
    sink(DataSinkType.POSTGIS, true, pipeline());
    NamedApi sta = api(ApiStandard.STA);
    api(ApiStandard.OWS);
    api(ApiStandard.CUSTOM);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.apis()).containsExactly(sta);
  }

  @Test
  void compute_lastSinkGoes_neverTakesACustomApi() {
    Pipeline pipeline = pipeline();
    sink(DataSinkType.FROST, false, pipeline);
    api(ApiStandard.CUSTOM);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.apis()).isEmpty();
  }

  @Test
  void needsConfirmation_unprovisionedSinkAlone_isEmpty() {
    Pipeline pipeline = pipeline();
    DataSink sink = sink(DataSinkType.FROST, false, pipeline);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.deactivatedDataSinks()).containsExactly(sink);
    assertThat(plan.needsConfirmation()).isEmpty();
  }

  @Test
  void needsConfirmation_provisionedSink_containsIt() {
    Pipeline pipeline = pipeline();
    DataSink sink = sink(DataSinkType.FROST, true, pipeline);

    DataSinkRemovalPlan plan = compute(DataSinkRemovalRequest.deletePipeline(pipeline.getId()));

    assertThat(plan.needsConfirmation()).containsExactly(sink.getId());
  }

  private DataSinkRemovalPlan compute(DataSinkRemovalRequest request) {
    return DataSinkRemovalPlan.compute(dataSet, sinks, layers, apis, request, apiKinds);
  }

  private static Pipeline pipeline() {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(UUID.randomUUID());
    return pipeline;
  }

  private DataSink sink(DataSinkType type, boolean provisioned, Pipeline... pipelines) {
    DataSink sink = new DataSink();
    sink.setId(UUID.randomUUID());
    sink.setDataSinkType(type);
    sink.setProvisioned(provisioned);
    sink.getPipelines().addAll(List.of(pipelines));
    sinks.add(sink);
    return sink;
  }

  private NamedApi api(ApiStandard standard) {
    NamedApi api =
        NamedApi.builder().slug(standard.name().toLowerCase()).standard(standard).build();
    api.setId(UUID.randomUUID());
    apis.add(api);
    return api;
  }

  private Layer layer(DataSink sink, NamedApi api) {
    Layer layer = new Layer();
    layer.setId(UUID.randomUUID());
    layer.setLayerName("layer-" + layers.size());
    layer.setDataSink(sink);
    layer.setNamedApi(api);
    layers.add(layer);
    return layer;
  }
}

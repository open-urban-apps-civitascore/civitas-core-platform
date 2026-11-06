package de.civitascore.portal.service.removal;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.base.BaseEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the removal cases that the portal frontend runs against its own calculation of the dialog
 * list. A rule changed on one side only fails the other side's test.
 */
class DataSinkRemovalPlanSharedCasesTest {

  private static final Path CASES =
      Path.of("..", "api", "portal-backend", "data-sink-removal-cases.json");

  static Stream<Arguments> cases() {
    JsonNode root = JsonMapper.shared().readTree(CASES);
    return StreamSupport.stream(root.get("cases").spliterator(), false)
        .map(node -> Arguments.of(node.get("name").asString(), node));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void compute_sharedCase_removesWhatTheFrontendShows(String name, JsonNode removalCase) {
    Map<String, Pipeline> pipelines = new HashMap<>();
    List<DataSink> sinks = list(removalCase.get("dataSinks"), node -> sink(node, pipelines));
    List<NamedApi> apis = list(removalCase.get("apis"), DataSinkRemovalPlanSharedCasesTest::api);
    List<Layer> layers = list(removalCase.get("layers"), node -> layer(node, sinks, apis));
    JsonNode request = removalCase.get("request");
    DataSinkRemovalRequest removal =
        new DataSinkRemovalRequest(
            Set.copyOf(
                list(
                    request.get("removedLinks"),
                    link ->
                        new PipelineDataSinkLink(
                            id(link.get("pipelineId")), id(link.get("dataSinkId"))))),
            Set.copyOf(
                list(request.get("deletedDataSinkIds"), DataSinkRemovalPlanSharedCasesTest::id)));

    DataSinkRemovalPlan plan =
        DataSinkRemovalPlan.compute(new DataSet(), sinks, layers, apis, removal);

    JsonNode expected = removalCase.get("expected");
    assertThat(ids(plan.deactivatedDataSinks()))
        .containsExactlyInAnyOrderElementsOf(expectedIds(expected.get("dataSinks")));
    assertThat(ids(plan.layers()))
        .containsExactlyInAnyOrderElementsOf(expectedIds(expected.get("layers")));
    assertThat(ids(plan.apis()))
        .containsExactlyInAnyOrderElementsOf(expectedIds(expected.get("apis")));
    assertThat(plan.deletesStoredData()).isEqualTo(expected.get("deletesStoredData").asBoolean());
  }

  private static DataSink sink(JsonNode node, Map<String, Pipeline> pipelines) {
    DataSink sink = new DataSink();
    sink.setId(id(node.get("id")));
    sink.setDataSinkType(DataSinkType.valueOf(node.get("type").asString()));
    sink.setProvisioned(node.get("provisioned").asBoolean());
    for (JsonNode pipelineId : node.get("pipelineIds")) {
      sink.getPipelines()
          .add(pipelines.computeIfAbsent(pipelineId.asString(), label -> pipeline(pipelineId)));
    }
    return sink;
  }

  private static Pipeline pipeline(JsonNode label) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id(label));
    return pipeline;
  }

  private static NamedApi api(JsonNode node) {
    ApiStandard standard = ApiStandard.valueOf(node.get("standard").asString());
    NamedApi api = NamedApi.builder().slug(node.get("id").asString()).standard(standard).build();
    api.setId(id(node.get("id")));
    return api;
  }

  private static Layer layer(JsonNode node, List<DataSink> sinks, List<NamedApi> apis) {
    Layer layer = new Layer();
    layer.setId(id(node.get("id")));
    layer.setDataSink(byId(sinks, id(node.get("dataSinkId"))));
    JsonNode namedApiId = node.get("namedApiId");
    layer.setNamedApi(namedApiId.isNull() ? null : byId(apis, id(namedApiId)));
    return layer;
  }

  private static <T extends BaseEntity> T byId(List<T> entities, UUID id) {
    return entities.stream().filter(entity -> entity.getId().equals(id)).findFirst().orElseThrow();
  }

  private static UUID id(JsonNode label) {
    return UUID.nameUUIDFromBytes(label.asString().getBytes(StandardCharsets.UTF_8));
  }

  private static <T> List<T> list(JsonNode array, Function<JsonNode, T> map) {
    return StreamSupport.stream(array.spliterator(), false).map(map).toList();
  }

  private static Set<UUID> ids(List<? extends BaseEntity> entities) {
    return entities.stream().map(BaseEntity::getId).collect(Collectors.toSet());
  }

  private static List<UUID> expectedIds(JsonNode labels) {
    return list(labels, DataSinkRemovalPlanSharedCasesTest::id);
  }
}

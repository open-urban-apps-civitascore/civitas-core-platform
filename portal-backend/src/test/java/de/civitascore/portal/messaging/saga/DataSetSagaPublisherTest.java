package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.NotLeaderOrFollowerException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetSagaPublisher Tests")
class DataSetSagaPublisherTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplate;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private ModelRegistryGateway modelRegistryGateway;

  private DataSetSagaPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = publisher(10);
  }

  private DataSetSagaPublisher publisher(int maxDepth) {
    return new DataSetSagaPublisher(
        kafkaTemplate,
        new JsonMapper(),
        new SagaProperties("test.saga.trigger", 5),
        dataSinkRepository,
        modelRegistryGateway,
        new PipelineClosureValidationProperties(maxDepth));
  }

  private static String datasourceUrn(UUID id) {
    return "urn:core:platform:civitas:datasource:common:" + id + ":1.0.0";
  }

  private DataSource dataSource(UUID id, ConnectorType type) {
    DataSource ds = new DataSource();
    ds.setId(id);
    ds.setName("ds-" + id);
    ds.setConnectorType(type);
    ds.setConfigurationUrn(datasourceUrn(id));
    return ds;
  }

  private Pipeline pipeline(UUID id, DataSource... sources) {
    Pipeline p = new Pipeline();
    p.setId(id);
    p.setName("pipeline-" + id);
    p.setVersion(3L);
    p.setDataSources(Set.of(sources));
    return p;
  }

  /**
   * Pins a registry-stored configuration document on the sink and stubs the gateway read for it.
   * Lenient so tests that fail the publish before reading the sink don't trip strict stubbing.
   */
  private void stubSinkConfiguration(DataSink sink, Map<String, Object> cfg) {
    String urn = "urn:core:platform:civitas:datasink:common:" + sink.getId() + ":1.0.0";
    sink.setConfigurationUrn(urn);
    lenient()
        .when(modelRegistryGateway.fetchPayload(urn))
        .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(cfg, null)));
  }

  private DataSink postgisSink(UUID id, String tableName) {
    DataSink sink = new DataSink();
    sink.setId(id);
    sink.setDataSinkType(DataSinkType.POSTGIS);
    Map<String, Object> cfg = new HashMap<>();
    cfg.put("tableName", tableName);
    stubSinkConfiguration(sink, cfg);
    return sink;
  }

  private Layer layer(UUID id, String layerName, String crs, DataSink sink) {
    Layer layer = new Layer();
    layer.setId(id);
    layer.setLayerName(layerName);
    layer.setCrs(crs);
    layer.setDataSink(sink);
    return layer;
  }

  private ArgumentCaptor<String> stubKafkaSend() {
    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));
    return jsonCaptor;
  }

  @Nested
  @DisplayName("buildDatasinks() schema resolution")
  class BuildDatasinksTests {

    private DataSink postgisSink(UUID id, String elementUrn, String tableName) {
      DataSink sink = new DataSink();
      sink.setId(id);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      Map<String, Object> cfg = new HashMap<>();
      cfg.put("tableName", tableName);
      cfg.put("element", elementUrn);
      stubSinkConfiguration(sink, cfg);
      return sink;
    }

    private DataSet datasetWithPipeline(Pipeline pipeline) {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(pipeline));
      return dataSet;
    }

    @Test
    @DisplayName(
        "carries the referenced element's inlined registry model (JSON Schema) on the sink")
    void carriesPersistedModelOnSink() {
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      String modelUrn =
          "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:2.0.0";
      DataSink sink = postgisSink(sinkId, modelUrn, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      // The gateway serves the inlined view with x-ui-styles already stripped; unknown x-*
      // keywords like x-core-primaryKey survive verbatim (the config-adapter reads them).
      when(modelRegistryGateway.fetchInlinedModel(modelUrn))
          .thenReturn(
              Optional.of(
                  Map.of(
                      "$id",
                      modelUrn,
                      "title",
                      "Observation",
                      "x-core-primaryKey",
                      "observationId",
                      "definitions",
                      Map.of("Observation", Map.of("type", "object")))));

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var datasinks = payload.get("datasinks");
      assertThat(datasinks).isNotNull();
      assertThat(datasinks.size()).isEqualTo(1);
      var ds = datasinks.get(0);
      assertThat(ds.get("id").asString())
          .as("datasink is keyed by its configuration CORE URN, not its GUID")
          .isEqualTo("urn:core:platform:civitas:datasink:common:" + sinkId + ":1.0.0");
      assertThat(ds.get("type").asString()).isEqualTo("POSTGIS");
      assertThat(ds.get("configuration").get("tableName").asString())
          .isEqualTo("sensor_observations");
      assertThat(ds.get("configuration").get("element").asString())
          .as(
              "the element URN soft reference is preserved verbatim inside the shipped configuration")
          .isEqualTo(modelUrn);
      assertThat(ds.get("dataStructure").get("title").asString()).isEqualTo("Observation");
      assertThat(ds.get("dataStructure").get("x-core-primaryKey").asString())
          .isEqualTo("observationId");
      assertThat(ds.get("dataStructure").get("definitions").has("Observation")).isTrue();
      assertThat(ds.get("dataStructure").has("x-ui-styles"))
          .as("the saga payload stays a pure schema document")
          .isFalse();
    }

    @Test
    @DisplayName("fails the publish when a referenced element cannot be resolved")
    void failsWhenSchemaUnresolved() {
      Pipeline pipeline = pipeline(UUID.randomUUID());
      String elementUrn =
          "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:2.0.0";
      DataSink sink = postgisSink(UUID.randomUUID(), elementUrn, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(modelRegistryGateway.fetchInlinedModel(elementUrn)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("fails the publish when the registry resolves the element to an empty model")
    void failsWhenReferencedElementResolvesToEmptyModel() {
      Pipeline pipeline = pipeline(UUID.randomUUID());
      String elementUrn =
          "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:2.0.0";
      DataSink sink = postgisSink(UUID.randomUUID(), elementUrn, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(modelRegistryGateway.fetchInlinedModel(elementUrn)).thenReturn(Optional.of(Map.of()));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("FROST sink without a DSV reference carries a null dataStructure, no failure")
    void frostSinkHasNoSchema() {
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = new DataSink();
      sink.setId(sinkId);
      sink.setDataSinkType(DataSinkType.FROST);
      DataSet dataSet = datasetWithPipeline(pipeline);

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var ds = payload.get("datasinks").get(0);
      assertThat(ds.get("type").asString()).isEqualTo("FROST");
      assertThat(ds.get("dataStructure").isNull())
          .as("FROST sink carries a null dataStructure")
          .isTrue();
    }

    @Test
    @DisplayName("DELETE trigger carries datasinks so the saga can tear down the PostGIS sink")
    void deleteTriggerCarriesDatasinks() {
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      String modelUrn =
          "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:1.0.0";
      DataSink sink = postgisSink(sinkId, modelUrn, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      when(modelRegistryGateway.fetchInlinedModel(modelUrn))
          .thenReturn(Optional.of(Map.of("$id", modelUrn)));
      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishDeleteRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var datasinks = payload.get("datasinks");
      assertThat(datasinks).isNotNull();
      assertThat(datasinks.size()).isEqualTo(1);
      var ds = datasinks.get(0);
      assertThat(ds.get("id").asString())
          .as("datasink is keyed by its configuration CORE URN, not its GUID")
          .isEqualTo("urn:core:platform:civitas:datasink:common:" + sinkId + ":1.0.0");
      assertThat(ds.get("type").asString()).isEqualTo("POSTGIS");
      assertThat(ds.get("configuration").get("tableName").asString())
          .isEqualTo("sensor_observations");
    }
  }

  @Nested
  @DisplayName("artifacts from the dependency graph")
  class ArtifactsTests {

    private static final String PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:8a46d02f-0994-8f94-8b55-031414ee2859:1.0.0";
    private static final String OTHER_PIPELINE_URN =
        "urn:core:platform:civitas:pipeline:common:3f0c1d2e-4b5a-8c6d-9e7f-0a1b2c3d4e5f:1.0.0";
    private static final String SOURCE_URN =
        "urn:core:platform:civitas:datasource:common:1c9b8d42-6f3e-8a71-b2d4-5e6f7a8b9c0d:1.1.0";
    private static final String SINK_URN =
        "urn:core:platform:civitas:datasink:common:7d2e4f60-8a1b-8c3d-9e5f-6a7b8c9d0e1f:1.0.0";
    private static final String MAPPING_URN =
        "urn:core:dataset:neustadt:mapping:traffic:b539f598-2ce4-8cb9-8068-920e30194249:1.0.0";
    private static final String SOURCE_STRUCTURE =
        "urn:core:platform:civitas:element:common:5cf6e9e7-fa0d-877a-bda7-df0cc339e439:1.0.0";
    private static final String TARGET_STRUCTURE =
        "urn:core:platform:civitas:datastructure:common:0e1c3a52-9f4b-8d2e-a7c6-3b5d1f2e4a60:2.0.0";
    private static final String NESTED_STRUCTURE =
        "urn:core:platform:civitas:element:common:9a8b7c6d-5e4f-8a3b-9c2d-1e0f9a8b7c6d:1.0.0";

    @BeforeEach
    void readArtifactTypeFromTheUrn() {
      lenient()
          .when(modelRegistryGateway.artifactType(anyString()))
          .thenAnswer(invocation -> invocation.<String>getArgument(0).split(":")[4]);
    }

    private void stored(String urn, Map<String, Object> document, String... dependencies) {
      when(modelRegistryGateway.fetchPayload(urn))
          .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(document, null)));
      when(modelRegistryGateway.dependencyUrns(urn)).thenReturn(List.of(dependencies));
    }

    private void structure(String urn, String title) {
      when(modelRegistryGateway.fetchInlinedModel(urn))
          .thenReturn(Optional.of(Map.of("$id", urn, "title", title, "type", "object")));
    }

    private void storedPipeline(String urn, String... dependencies) {
      stored(urn, Map.of("nodes", List.of()), dependencies);
    }

    private void sourceSinkAndMapping() {
      storedPipeline(PIPELINE_URN, SOURCE_URN, SINK_URN, MAPPING_URN);
      stored(
          SOURCE_URN,
          Map.of("connectionType", "mqtt", "password", "ENC(secret)", "element", SOURCE_STRUCTURE),
          SOURCE_STRUCTURE);
      stored(
          SINK_URN,
          Map.of("connectionType", "postgis", "tableName", "obs", "element", TARGET_STRUCTURE),
          TARGET_STRUCTURE);
      stored(
          MAPPING_URN,
          Map.of(
              "source",
              SOURCE_STRUCTURE,
              "target",
              TARGET_STRUCTURE,
              "fields",
              Map.of("$.name", "$.station")),
          SOURCE_STRUCTURE,
          TARGET_STRUCTURE);
      structure(SOURCE_STRUCTURE, "Reading");
      structure(TARGET_STRUCTURE, "Observation");
    }

    private Pipeline modelledPipeline(String modelUrn) {
      Pipeline pipeline = pipeline(UUID.randomUUID());
      pipeline.setModelUrn(modelUrn);
      return pipeline;
    }

    private DataSet dataSet(Pipeline... pipelines) {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(pipelines));
      return dataSet;
    }

    private JsonNode publishCreate(DataSet dataSet) {
      var jsonCaptor = stubKafkaSend();
      publisher.publishCreateRequested(dataSet);
      return new JsonMapper().readTree(jsonCaptor.getValue());
    }

    @Test
    @DisplayName("configurations and Mappings ship as stored, keyed by the URN the graph reports")
    void configurationsAndMappingsShipAsStored() {
      sourceSinkAndMapping();

      JsonNode artifacts = publishCreate(dataSet(modelledPipeline(PIPELINE_URN))).get("artifacts");

      JsonNode source = artifacts.get(SOURCE_URN);
      assertThat(source.get("connectionType").asString()).isEqualTo("mqtt");
      assertThat(source.get("password").asString()).isEqualTo("ENC(secret)");
      assertThat(source.get("element").asString()).isEqualTo(SOURCE_STRUCTURE);
      assertThat(artifacts.get(SINK_URN).get("tableName").asString()).isEqualTo("obs");
      assertThat(artifacts.get(MAPPING_URN).get("fields").get("$.name").asString())
          .isEqualTo("$.station");
      assertThat(artifacts.has(PIPELINE_URN)).isFalse();
    }

    @Test
    @DisplayName("every referenced Data structure ships once, as its inlined view")
    void dataStructuresShipInlinedOnce() {
      sourceSinkAndMapping();

      JsonNode artifacts = publishCreate(dataSet(modelledPipeline(PIPELINE_URN))).get("artifacts");

      assertThat(artifacts.get(SOURCE_STRUCTURE).get("title").asString()).isEqualTo("Reading");
      assertThat(artifacts.get(TARGET_STRUCTURE).get("title").asString()).isEqualTo("Observation");
      verify(modelRegistryGateway, times(1)).fetchInlinedModel(SOURCE_STRUCTURE);
      verify(modelRegistryGateway, times(1)).fetchInlinedModel(TARGET_STRUCTURE);
    }

    @Test
    @DisplayName("the walk stops at a Data structure")
    void walkStopsAtDataStructures() {
      sourceSinkAndMapping();
      lenient()
          .when(modelRegistryGateway.dependencyUrns(TARGET_STRUCTURE))
          .thenReturn(List.of(NESTED_STRUCTURE));

      JsonNode artifacts = publishCreate(dataSet(modelledPipeline(PIPELINE_URN))).get("artifacts");

      assertThat(artifacts.has(NESTED_STRUCTURE)).isFalse();
      verify(modelRegistryGateway, never()).dependencyUrns(SOURCE_STRUCTURE);
      verify(modelRegistryGateway, never()).dependencyUrns(TARGET_STRUCTURE);
    }

    @Test
    @DisplayName("the keys are sorted")
    void keysAreSorted() {
      sourceSinkAndMapping();

      JsonNode artifacts = publishCreate(dataSet(modelledPipeline(PIPELINE_URN))).get("artifacts");

      assertThat(List.copyOf(artifacts.propertyNames()))
          .containsExactlyInAnyOrder(
              SOURCE_URN, SINK_URN, MAPPING_URN, SOURCE_STRUCTURE, TARGET_STRUCTURE)
          .isSorted();
    }

    @Test
    @DisplayName(
        "the current configuration of an associated Data source and Data sink ships beside the"
            + " pinned one")
    void currentConfigurationsOfAssociatedEntitiesShip() {
      String currentSource = SOURCE_URN.replace(":1.1.0", ":1.2.0");
      String currentSink = SINK_URN.replace(":1.0.0", ":1.1.0");
      String currentStructure = SOURCE_STRUCTURE.replace(":1.0.0", ":1.1.0");
      storedPipeline(PIPELINE_URN, SOURCE_URN, SINK_URN);
      stored(
          SOURCE_URN,
          Map.of("connectionType", "mqtt", "element", SOURCE_STRUCTURE),
          SOURCE_STRUCTURE);
      stored(
          currentSource,
          Map.of("connectionType", "mqtt", "element", currentStructure),
          currentStructure);
      stored(SINK_URN, Map.of("connectionType", "postgis", "tableName", "obs"));
      stored(currentSink, Map.of("connectionType", "postgis", "tableName", "obs_current"));
      structure(SOURCE_STRUCTURE, "Reading");
      structure(currentStructure, "Reading, edited");
      DataSource source = dataSource(UUID.randomUUID(), ConnectorType.MQTT);
      source.setConfigurationUrn(currentSource);
      Pipeline pipeline = pipeline(UUID.randomUUID(), source);
      pipeline.setModelUrn(PIPELINE_URN);
      DataSink sink = new DataSink();
      sink.setConfigurationUrn(currentSink);
      when(dataSinkRepository.findByPipelineId(pipeline.getId())).thenReturn(List.of(sink));

      JsonNode payload = publishCreate(dataSet(pipeline));

      assertThat(List.copyOf(payload.get("artifacts").propertyNames()))
          .containsExactlyInAnyOrder(
              SOURCE_URN, currentSource, SINK_URN, currentSink, SOURCE_STRUCTURE, currentStructure);
      JsonNode entry = payload.get("dataPipelines").get(0);
      assertThat(entry.get("dataSourceIds").get(0).asString()).isEqualTo(currentSource);
      assertThat(entry.get("dataSinkIds").get(0).asString()).isEqualTo(currentSink);
    }

    @Test
    @DisplayName("a Data source two Pipelines share is fetched and shipped once")
    void sharedDataSourceShipsOnce() {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      storedPipeline(OTHER_PIPELINE_URN, SOURCE_URN);
      stored(SOURCE_URN, Map.of("connectionType", "sql"));

      JsonNode artifacts =
          publishCreate(
                  dataSet(modelledPipeline(PIPELINE_URN), modelledPipeline(OTHER_PIPELINE_URN)))
              .get("artifacts");

      assertThat(List.copyOf(artifacts.propertyNames())).containsExactly(SOURCE_URN);
      verify(modelRegistryGateway, times(1)).fetchPayload(SOURCE_URN);
      verify(modelRegistryGateway, times(1)).dependencyUrns(SOURCE_URN);
    }

    @Test
    @DisplayName("an UPDATE trigger carries the artifacts of the current Pipelines only")
    void updateTriggerCarriesArtifactsOfCurrentPipelines() {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      stored(SOURCE_URN, Map.of("connectionType", "sql"));
      Pipeline current = modelledPipeline(PIPELINE_URN);
      Pipeline removed = modelledPipeline(OTHER_PIPELINE_URN);
      var jsonCaptor = stubKafkaSend();

      publisher.publishUpdateRequested(dataSet(current), Set.of(current, removed));

      JsonNode artifacts = new JsonMapper().readTree(jsonCaptor.getValue()).get("artifacts");
      assertThat(List.copyOf(artifacts.propertyNames())).containsExactly(SOURCE_URN);
      verify(modelRegistryGateway, never()).dependencyUrns(OTHER_PIPELINE_URN);
    }

    @Test
    @DisplayName("a Pipeline without a stored model contributes nothing, artifacts stays an object")
    void pipelineWithoutModelContributesNothing() {
      JsonNode payload = publishCreate(dataSet(pipeline(UUID.randomUUID())));

      assertThat(payload.get("artifacts").isObject()).isTrue();
      assertThat(payload.get("artifacts").isEmpty()).isTrue();
    }

    @Test
    @DisplayName(
        "DELETE and UNRELEASE do not walk the graph, so a dangling artifact cannot block them")
    void teardownTriggersDoNotWalkTheGraph() {
      DataSet dataSet = dataSet(modelledPipeline(PIPELINE_URN));
      var jsonCaptor = stubKafkaSend();

      publisher.publishDeleteRequested(dataSet);
      publisher.publishUnreleaseRequested(dataSet);

      assertThat(jsonCaptor.getAllValues()).hasSize(2);
      verify(modelRegistryGateway, never()).dependencyUrns(anyString());
    }

    @Test
    @DisplayName("fails the publish when a referenced configuration is not stored")
    void unresolvedConfigurationFailsThePublish() {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      when(modelRegistryGateway.fetchPayload(SOURCE_URN)).thenReturn(Optional.empty());
      DataSet dataSet = dataSet(modelledPipeline(PIPELINE_URN));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(SOURCE_URN);
      verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @ParameterizedTest(name = "inlined view {0}")
    @MethodSource("missingModels")
    @DisplayName("fails the publish when a referenced Data structure has no model")
    void unresolvedDataStructureFailsThePublish(
        String description, Optional<Map<String, Object>> model) {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      stored(SOURCE_URN, Map.of("element", SOURCE_STRUCTURE), SOURCE_STRUCTURE);
      when(modelRegistryGateway.fetchInlinedModel(SOURCE_STRUCTURE)).thenReturn(model);
      DataSet dataSet = dataSet(modelledPipeline(PIPELINE_URN));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(SOURCE_STRUCTURE);
      verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    static Stream<Arguments> missingModels() {
      return Stream.of(
          Arguments.of("absent", Optional.empty()), Arguments.of("empty", Optional.of(Map.of())));
    }

    @Test
    @DisplayName("fails the publish when the walk reaches an artifact type no Pipeline uses")
    void unexpectedArtifactTypeFailsThePublish() {
      storedPipeline(PIPELINE_URN, OTHER_PIPELINE_URN);
      DataSet dataSet = dataSet(modelledPipeline(PIPELINE_URN));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(OTHER_PIPELINE_URN)
          .hasMessageContaining("type pipeline");
      verify(modelRegistryGateway, never()).fetchPayload(OTHER_PIPELINE_URN);
      verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("fails the publish when the artifacts reach beyond the depth bound")
    void walkPastTheDepthBoundFailsThePublish() {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      stored(SOURCE_URN, Map.of("element", SOURCE_STRUCTURE), SOURCE_STRUCTURE);
      DataSet dataSet = dataSet(modelledPipeline(PIPELINE_URN));

      assertThatThrownBy(() -> publisher(1).publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("beyond 1 references");
      verify(modelRegistryGateway, never()).fetchInlinedModel(anyString());
      verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a walk exactly as deep as the bound publishes")
    void walkAtTheDepthBoundPublishes() {
      storedPipeline(PIPELINE_URN, SOURCE_URN);
      stored(SOURCE_URN, Map.of("element", SOURCE_STRUCTURE), SOURCE_STRUCTURE);
      structure(SOURCE_STRUCTURE, "Reading");
      var jsonCaptor = stubKafkaSend();

      publisher(2).publishCreateRequested(dataSet(modelledPipeline(PIPELINE_URN)));

      JsonNode artifacts = new JsonMapper().readTree(jsonCaptor.getValue()).get("artifacts");
      assertThat(List.copyOf(artifacts.propertyNames()))
          .containsExactly(SOURCE_URN, SOURCE_STRUCTURE);
    }
  }

  @Nested
  @DisplayName("buildLayers() native-name resolution")
  class BuildLayersTests {

    private DataSink frostSink(UUID id) {
      DataSink sink = new DataSink();
      sink.setId(id);
      sink.setDataSinkType(DataSinkType.FROST);
      return sink;
    }

    @Test
    @DisplayName("CREATE trigger carries a layer with nativeName from its POSTGIS sink's tableName")
    void createTriggerCarriesLayerWithPostgisTableName() {
      UUID sinkId = UUID.randomUUID();
      UUID layerId = UUID.randomUUID();
      DataSink sink = postgisSink(sinkId, "my_table");
      Layer l = layer(layerId, "layer1", "EPSG:4326", sink);
      l.setGeometryColumnRef("geom");

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var layers = payload.get("layers");
      assertThat(layers).as("layers must be present in CREATE trigger").isNotNull();
      assertThat(layers.size()).isEqualTo(1);
      var entry = layers.get(0);
      assertThat(entry.get("id").asString()).isEqualTo(layerId.toString());
      assertThat(entry.get("layerName").asString()).isEqualTo("layer1");
      assertThat(entry.get("nativeName").asString()).isEqualTo("my_table");
      assertThat(entry.get("crs").asString()).isEqualTo("EPSG:4326");
      assertThat(entry.get("geometryColumnRef").asString())
          .as("the geometry-column selection is forwarded so the adapter can pick the native CRS")
          .isEqualTo("geom");
    }

    @Test
    @DisplayName("CREATE trigger carries the layer's published fields")
    void createTriggerCarriesLayerFieldsPublishedByGeoServer() {
      Layer l =
          layer(UUID.randomUUID(), "layer1", "EPSG:4326", postgisSink(UUID.randomUUID(), "t"));
      l.setTitle("Messstationen");
      l.setDescription("Alle Stationen");
      l.setAttribute(List.of("name", "temperature"));
      l.setCqlFilter("temperature > 20");
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("layers").get(0);
      assertThat(entry.get("title").asString()).isEqualTo("Messstationen");
      assertThat(entry.get("description").asString()).isEqualTo("Alle Stationen");
      assertThat(entry.get("attribute").get(0).asString()).isEqualTo("name");
      assertThat(entry.get("attribute").get(1).asString()).isEqualTo("temperature");
      assertThat(entry.get("cqlFilter").asString()).isEqualTo("temperature > 20");
    }

    @Test
    @DisplayName("Unset layer fields are omitted from the trigger")
    void unsetLayerFieldsAreOmitted() {
      Layer l =
          layer(UUID.randomUUID(), "layer1", "EPSG:4326", postgisSink(UUID.randomUUID(), "t"));
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("layers").get(0);
      assertThat(entry.has("title")).isFalse();
      assertThat(entry.has("description")).isFalse();
      assertThat(entry.has("attribute")).isFalse();
      assertThat(entry.has("cqlFilter")).isFalse();
    }

    @Test
    @DisplayName("Layer on a non-POSTGIS sink carries a null nativeName (adapter falls back)")
    void layerOnNonPostgisSinkHasNullNativeName() {
      DataSink sink = frostSink(UUID.randomUUID());
      Layer l = layer(UUID.randomUUID(), "frost-layer", null, sink);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var entry = payload.get("layers").get(0);
      assertThat(entry.get("layerName").asString()).isEqualTo("frost-layer");
      assertThat(entry.has("nativeName"))
          .as("non-POSTGIS sink → nativeName omitted via @JsonInclude(NON_NULL)")
          .isFalse();
    }

    @Test
    @DisplayName("Empty layers is omitted from the trigger JSON (NON_NULL)")
    void emptyLayersOmitted() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.has("layers"))
          .as("empty layers should be omitted from the JSON via @JsonInclude(NON_NULL)")
          .isFalse();
    }

    @Test
    @DisplayName("UPDATE trigger also carries layers")
    void updateTriggerCarriesLayers() {
      DataSink sink = postgisSink(UUID.randomUUID(), "events");
      Layer l = layer(UUID.randomUUID(), "events-layer", "EPSG:3857", sink);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishUpdateRequested(dataSet, Set.of());

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var layers = payload.get("layers");
      assertThat(layers).isNotNull();
      assertThat(layers.size()).isEqualTo(1);
      assertThat(layers.get(0).get("nativeName").asString()).isEqualTo("events");
    }
  }

  @Nested
  @DisplayName("buildStyles() and per-layer style references")
  class BuildStylesTests {

    private Style style(String name, String sld) {
      Style s = new Style();
      s.setId(UUID.randomUUID());
      s.setName(name);
      s.setSldContent(sld);
      return s;
    }

    @Test
    @DisplayName("CREATE trigger carries dataset styles[] and per-layer style references")
    void createTriggerCarriesStylesAndLayerReferences() {
      Style def = style("civitas_default_point", "<sld>default</sld>");
      Style alt = style("civitas_heat", "<sld>heat</sld>");

      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      Layer l = layer(UUID.randomUUID(), "layer1", "EPSG:4326", sink);
      l.setDefaultStyle(def);
      l.setAlternativeStyles(Set.of(alt));

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setStyles(Set.of(def, alt));
      dataSet.setLayers(Set.of(l));

      var jsonCaptor = stubKafkaSend();
      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());

      var styles = payload.get("styles");
      assertThat(styles).as("styles must be present in CREATE trigger").isNotNull();
      assertThat(styles.size()).isEqualTo(2);
      Map<String, String> sldByName = new HashMap<>();
      styles.forEach(n -> sldByName.put(n.get("name").asString(), n.get("sldContent").asString()));
      assertThat(sldByName).containsEntry("civitas_default_point", "<sld>default</sld>");
      assertThat(sldByName).containsEntry("civitas_heat", "<sld>heat</sld>");

      var entry = payload.get("layers").get(0);
      assertThat(entry.get("defaultStyle").asString()).isEqualTo("civitas_default_point");
      var altList = entry.get("alternativeStyles");
      assertThat(altList).isNotNull();
      assertThat(altList.size()).isEqualTo(1);
      assertThat(altList.get(0).asString()).isEqualTo("civitas_heat");
    }

    @Test
    @DisplayName("UPDATE trigger also carries styles[] and per-layer references")
    void updateTriggerCarriesStylesAndLayerReferences() {
      Style def = style("civitas_default_point", "<sld/>");

      DataSink sink = postgisSink(UUID.randomUUID(), "events");
      Layer l = layer(UUID.randomUUID(), "events-layer", "EPSG:3857", sink);
      l.setDefaultStyle(def);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      dataSet.setStyles(Set.of(def));
      dataSet.setLayers(Set.of(l));

      var jsonCaptor = stubKafkaSend();
      publisher.publishUpdateRequested(dataSet, Set.of());

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.get("styles").size()).isEqualTo(1);
      assertThat(payload.get("layers").get(0).get("defaultStyle").asString())
          .isEqualTo("civitas_default_point");
    }

    @Test
    @DisplayName("Dataset without styles omits styles field; layer style refs are null")
    void emptyStylesOmittedFromTrigger() {
      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      Layer l = layer(UUID.randomUUID(), "layer1", null, sink);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setLayers(Set.of(l));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.has("styles"))
          .as("empty styles should be omitted from the JSON via @JsonInclude(NON_NULL)")
          .isFalse();
      var entry = payload.get("layers").get(0);
      assertThat(entry.has("defaultStyle"))
          .as("no defaultStyle → field omitted via @JsonInclude(NON_NULL)")
          .isFalse();
      assertThat(entry.has("alternativeStyles"))
          .as("no alternativeStyles → field omitted via @JsonInclude(NON_NULL)")
          .isFalse();
    }

    @Test
    @DisplayName("alternativeStyles names are emitted in deterministic (sorted) order")
    void alternativeStylesAreSorted() {
      Style sA = style("civitas_a", "<sld/>");
      Style sM = style("civitas_m", "<sld/>");
      Style sZ = style("civitas_z", "<sld/>");
      Style def = style("civitas_default", "<sld/>");

      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      Layer l = layer(UUID.randomUUID(), "layer1", null, sink);
      l.setDefaultStyle(def);
      // Insert in non-alphabetical order; the publisher must still emit them sorted.
      Set<Style> alts = new LinkedHashSet<>();
      alts.add(sZ);
      alts.add(sA);
      alts.add(sM);
      l.setAlternativeStyles(alts);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setStyles(Set.of(sA, sM, sZ, def));
      dataSet.setLayers(Set.of(l));

      var jsonCaptor = stubKafkaSend();
      publisher.publishCreateRequested(dataSet);

      var altList =
          new JsonMapper()
              .readTree(jsonCaptor.getValue())
              .get("layers")
              .get(0)
              .get("alternativeStyles");
      assertThat(altList.size()).isEqualTo(3);
      assertThat(altList.get(0).asString()).isEqualTo("civitas_a");
      assertThat(altList.get(1).asString()).isEqualTo("civitas_m");
      assertThat(altList.get(2).asString()).isEqualTo("civitas_z");
    }

    @Test
    @DisplayName("Layer referencing an unknown defaultStyle fails the publish")
    void unknownDefaultStyleFailsPublish() {
      Style known = style("civitas_known", "<sld/>");
      Style stray = style("civitas_stray", "<sld/>"); // NOT in dataset.styles

      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      UUID layerId = UUID.randomUUID();
      Layer l = layer(layerId, "layer1", null, sink);
      l.setDefaultStyle(stray);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setStyles(Set.of(known));
      dataSet.setLayers(Set.of(l));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(layerId.toString())
          .hasMessageContaining("civitas_stray");
    }

    @Test
    @DisplayName("Layer referencing an unknown alternativeStyle fails the publish (UPDATE path)")
    void unknownAlternativeStyleFailsUpdatePublish() {
      Style def = style("civitas_default", "<sld/>");
      Style stray = style("civitas_stray", "<sld/>"); // NOT in dataset.styles

      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      UUID layerId = UUID.randomUUID();
      Layer l = layer(layerId, "layer1", null, sink);
      l.setDefaultStyle(def);
      l.setAlternativeStyles(Set.of(stray));

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      dataSet.setStyles(Set.of(def));
      dataSet.setLayers(Set.of(l));

      assertThatThrownBy(() -> publisher.publishUpdateRequested(dataSet, Set.of()))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(layerId.toString())
          .hasMessageContaining("civitas_stray");
    }

    @Test
    @DisplayName(
        "Layer referencing a foreign Style with a matching name still fails (identity by UUID)")
    void foreignStyleWithMatchingNameFailsPublish() {
      // Two Style instances share the same name but have distinct UUIDs.
      // localStyle is owned by the dataset; foreignStyle is a "look-alike" from another dataset
      // attached to the layer. Name-based validation would silently accept this — UUID-based
      // validation must reject it.
      Style localStyle = style("civitas_shared", "<sld>local</sld>");
      Style foreignStyle = style("civitas_shared", "<sld>foreign</sld>");

      DataSink sink = postgisSink(UUID.randomUUID(), "my_table");
      UUID layerId = UUID.randomUUID();
      Layer l = layer(layerId, "layer1", null, sink);
      l.setDefaultStyle(foreignStyle);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setStyles(Set.of(localStyle));
      dataSet.setLayers(Set.of(l));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining(layerId.toString())
          .hasMessageContaining("not owned by dataset")
          .hasMessageContaining("civitas_shared");
    }
  }

  @Nested
  @DisplayName("buildPipelineDiff() classification")
  class BuildPipelineDiffTests {

    @Test
    @DisplayName("new pipelines get ADD, existing get UPDATE, removed get DELETE")
    void classifiesPipelineActionsCorrectly() {
      UUID existingPipelineId = UUID.randomUUID();
      UUID newPipelineId = UUID.randomUUID();
      UUID removedPipelineId = UUID.randomUUID();

      DataSource ds = dataSource(UUID.randomUUID(), ConnectorType.MQTT);

      Pipeline existingPipeline = pipeline(existingPipelineId, ds);
      Pipeline newPipeline = pipeline(newPipelineId, ds);
      Pipeline removedPipeline = pipeline(removedPipelineId, ds);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      // routeId is now per-named-API on the child entity; no setter on the dataset itself
      dataSet.setServiceId("svc-1");
      dataSet.setPipelineIds(List.of("pipe-1"));
      dataSet.setPipelines(Set.of(existingPipeline, newPipeline));

      Set<Pipeline> previousPipelines = Set.of(existingPipeline, removedPipeline);

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishUpdateRequested(dataSet, previousPipelines);

      ObjectMapper mapper = new JsonMapper();
      var payload = mapper.readTree(jsonCaptor.getValue());
      var dataPipelines = payload.get("dataPipelines");
      assertThat(dataPipelines).isNotNull();
      assertThat(dataPipelines.size()).isEqualTo(3);

      boolean hasAdd = false, hasUpdate = false, hasDelete = false;
      for (var p : dataPipelines) {
        String action = p.get("action").asString();
        String pipelineId = p.get("id").asString();
        String version = p.get("version").asString();
        if (action.equals("ADD") && pipelineId.equals(newPipelineId.toString())) {
          hasAdd = true;
          assertThat(version).as("ADD pipeline should carry entity version").isEqualTo("3");
        }
        if (action.equals("UPDATE") && pipelineId.equals(existingPipelineId.toString())) {
          hasUpdate = true;
          assertThat(version).as("UPDATE pipeline should carry entity version").isEqualTo("3");
        }
        if (action.equals("DELETE") && pipelineId.equals(removedPipelineId.toString())) {
          hasDelete = true;
          assertThat(version).as("DELETE pipeline should use sentinel version").isEqualTo("0");
        }
      }

      assertThat(hasAdd).as("new pipeline should be ADD").isTrue();
      assertThat(hasUpdate).as("existing pipeline should be UPDATE").isTrue();
      assertThat(hasDelete).as("removed pipeline should be DELETE").isTrue();
    }

    @Test
    @DisplayName("pipeline model is served from the registry pin (layout styles stripped)")
    void pipelineModelServedFromRegistry() {
      Pipeline p = pipeline(UUID.randomUUID());
      String urn =
          "urn:core:platform:civitas:pipeline:common:8a46d02f-0994-8f94-8b55-031414ee2859:1.2.0";
      p.setModelUrn(urn);
      // fetchPayload returns the stored document already split: content without x-ui-styles,
      // the React Flow layout separately. The saga ships only the content.
      when(modelRegistryGateway.fetchPayload(urn))
          .thenReturn(
              Optional.of(
                  new ModelRegistryGateway.RegistryDocument(
                      Map.of("nodes", List.of(Map.of("id", "n1"))),
                      Map.of("viewport", Map.of("x", 1)))));

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(p));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var entry = payload.get("dataPipelines").get(0);
      assertThat(entry.get("data").get("nodes").get(0).get("id").asString()).isEqualTo("n1");
      assertThat(entry.get("data").has("x-ui-styles"))
          .as("the saga payload carries the definition only, never the UI layout")
          .isFalse();
      assertThat(entry.get("version").asString()).isEqualTo("3");
    }

    @Test
    @DisplayName("pipeline without a stored definition ships a null model (as before)")
    void pipelineWithoutDefinitionShipsNullModel() {
      DataSource ds = dataSource(UUID.randomUUID(), ConnectorType.MQTT);
      Pipeline p = pipeline(UUID.randomUUID(), ds); // no modelUrn

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(p));
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("dataPipelines").get(0);
      assertThat(entry.get("data").isNull())
          .as("no stored definition → null data, same as before the cut")
          .isTrue();
    }
  }

  @Nested
  @DisplayName("namedApis serialization in saga triggers (#1311)")
  class NamedApisSerializationTests {

    private NamedApi api(String name, String slug, String routeId) {
      NamedApi api = new NamedApi();
      api.setName(name);
      api.setDescription(name + " description");
      api.setSlug(slug);
      api.setStandard(ApiStandard.STA);
      api.setVersion("1.1");
      api.setRouteId(routeId);
      return api;
    }

    private DataSet datasetWithNamedApis(String trafficRouteId, String weatherRouteId) {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setNamedApis(
          new java.util.HashSet<>(
              Set.of(
                  api("Traffic Sensor Readings", "traffic", trafficRouteId),
                  api("Weather Sensor Readings", "weather", weatherRouteId))));
      return dataSet;
    }

    private void assertNamedApisInPayload(String json) {
      var payload = new JsonMapper().readTree(json);
      var namedApis = payload.get("namedApis");
      assertThat(namedApis).as("namedApis must be present in trigger payload").isNotNull();
      assertThat(namedApis.size()).isEqualTo(2);
      // Order is not guaranteed (Set), assert by slug
      Map<String, JsonNode> bySlug = new HashMap<>();
      namedApis.forEach(node -> bySlug.put(node.get("slug").asString(), node));
      assertThat(bySlug).containsKeys("traffic", "weather");
      assertThat(bySlug.get("traffic").get("standard").asString()).isEqualTo("STA");
      assertThat(bySlug.get("traffic").get("version").asString()).isEqualTo("1.1");
      assertThat(bySlug.get("traffic").get("name").asString()).isEqualTo("Traffic Sensor Readings");
      assertThat(bySlug.get("traffic").get("description").asString())
          .isEqualTo("Traffic Sensor Readings description");
    }

    @Test
    @DisplayName("DatasetCreate trigger carries namedApis but omits routeIds")
    void createTriggerCarriesNamedApis() {
      DataSet dataSet = datasetWithNamedApis(null, null); // no routeIds yet on CREATE
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      assertNamedApisInPayload(jsonCaptor.getValue());
      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.has("routeIds"))
          .as("CREATE trigger has no routeIds: routes are provisioned by this saga")
          .isFalse();
    }

    @Test
    @DisplayName("DatasetUpdate trigger carries namedApis and slug-keyed routeIds")
    void updateTriggerCarriesNamedApisAndRouteIds() {
      DataSet dataSet = datasetWithNamedApis("route-1", "route-2");
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      var jsonCaptor = stubKafkaSend();

      publisher.publishUpdateRequested(dataSet, Set.of());

      assertNamedApisInPayload(jsonCaptor.getValue());
      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var routeIds = payload.get("routeIds");
      assertThat(routeIds).isNotNull();
      assertThat(routeIds.get("traffic").asString()).isEqualTo("route-1");
      assertThat(routeIds.get("weather").asString()).isEqualTo("route-2");
    }

    @Test
    @DisplayName("DatasetDelete trigger carries namedApis and slug-keyed routeIds")
    void deleteTriggerCarriesNamedApisAndRouteIds() {
      DataSet dataSet = datasetWithNamedApis("route-1", "route-2");
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      var jsonCaptor = stubKafkaSend();

      publisher.publishDeleteRequested(dataSet);

      assertNamedApisInPayload(jsonCaptor.getValue());
      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var routeIds = payload.get("routeIds");
      assertThat(routeIds).isNotNull();
      assertThat(routeIds.size()).isEqualTo(2);
      assertThat(routeIds.get("traffic").asString()).isEqualTo("route-1");
      assertThat(routeIds.get("weather").asString()).isEqualTo("route-2");
    }

    @Test
    @DisplayName("DatasetUnrelease trigger carries route/pipeline fields but omits the sink")
    void unreleaseTriggerOmitsSinkFields() {
      DataSet dataSet = datasetWithNamedApis("route-1", "route-2");
      dataSet.setProjectId("proj-1");
      dataSet.setFrostBaseUrl("https://frost/Projects(1)");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelineIds(java.util.List.of("pipe-1"));
      var jsonCaptor = stubKafkaSend();

      publisher.publishUnreleaseRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.get("sagaType").asString()).isEqualTo("DATASET_UNRELEASE");
      // Route/pipeline teardown fields are present.
      assertThat(payload.get("serviceId").asString()).isEqualTo("svc-1");
      assertThat(payload.get("routeIds").size()).isEqualTo(2);
      assertThat(payload.get("pipelineIds")).isNotNull();
      assertNamedApisInPayload(jsonCaptor.getValue());
      // The sink-holding fields must NOT leak into an unrelease trigger — otherwise the adapter
      // could tear down the PostGIS table / FROST project (the bug #1923 fixes).
      assertThat(payload.has("projectId")).as("projectId must be omitted").isFalse();
      assertThat(payload.has("frostBaseUrl")).as("frostBaseUrl must be omitted").isFalse();
      assertThat(payload.has("datasinks")).as("datasinks must be omitted").isFalse();
    }

    @Test
    @DisplayName("DELETE trigger routeIds map omits entries with null routeId (partial release)")
    void deleteTriggerOmitsEntriesWithoutRouteId() {
      // Half-released dataset: traffic was provisioned, weather was not. The DELETE saga must
      // still tell the orchestrator about both named APIs (so it knows to clean up downstream
      // state for weather), but routeIds carries only the entries with a real APISIX route.
      DataSet dataSet = datasetWithNamedApis("route-1", null);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      var jsonCaptor = stubKafkaSend();

      publisher.publishDeleteRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var routeIds = payload.get("routeIds");
      assertThat(routeIds).isNotNull();
      assertThat(routeIds.size()).isEqualTo(1);
      assertThat(routeIds.has("traffic")).isTrue();
      assertThat(routeIds.has("weather"))
          .as("entries without a routeId should not appear in the DELETE map either")
          .isFalse();
      // namedApis must still carry both — the orchestrator needs to know weather exists.
      assertNamedApisInPayload(jsonCaptor.getValue());
    }

    @Test
    @DisplayName("Duplicate slug in collection throws with datasetId + slug context")
    void duplicateSlugThrowsWithContext() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      // Build the set via add() rather than Set.of(...), so the test still constructs two
      // duplicate-slug entries even if NamedApi later gains @EqualsAndHashCode on slug.
      Set<NamedApi> apis = new LinkedHashSet<>();
      apis.add(api("Traffic A", "traffic", "route-1"));
      apis.add(api("Traffic B", "traffic", "route-2"));
      dataSet.setNamedApis(apis);

      // DB UNIQUE(dataset_id, slug) prevents this in production, but if a transactional bug or
      // migration ever produced duplicates, the publisher must fail with diagnostic context
      // (datasetId + colliding routeIds) — not a bare IllegalStateException from Collectors.
      // UPDATE/DELETE triggers project routeIds; CREATE does not, so use UPDATE here.
      assertThatThrownBy(() -> publisher.publishUpdateRequested(dataSet, Set.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(dataSet.getId().toString())
          .hasMessageContaining("route-1")
          .hasMessageContaining("route-2");
    }

    @Test
    @DisplayName("UPDATE trigger routeIds map omits entries with null routeId (mid-saga state)")
    void updateTriggerOmitsEntriesWithoutRouteId() {
      // One entry has a routeId (already provisioned), the other doesn't yet.
      DataSet dataSet = datasetWithNamedApis("route-1", null);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelines(Set.of());
      var jsonCaptor = stubKafkaSend();

      publisher.publishUpdateRequested(dataSet, Set.of());

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      var routeIds = payload.get("routeIds");
      assertThat(routeIds).isNotNull();
      assertThat(routeIds.size()).isEqualTo(1);
      assertThat(routeIds.has("traffic")).isTrue();
      assertThat(routeIds.has("weather"))
          .as("entries without a routeId should not appear in the map")
          .isFalse();
    }

    @Test
    @DisplayName("Empty namedApis is omitted from the trigger JSON (NON_NULL)")
    void emptyNamedApisOmitted() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of());
      var jsonCaptor = stubKafkaSend();

      publisher.publishCreateRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.has("namedApis"))
          .as("empty namedApis should be omitted from the JSON via @JsonInclude(NON_NULL)")
          .isFalse();
    }

    @Test
    @DisplayName("routeIds map is omitted when no entry has a populated routeId")
    void routeIdsOmittedWhenNoneSet() {
      DataSet dataSet = datasetWithNamedApis(null, null);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      var jsonCaptor = stubKafkaSend();

      publisher.publishDeleteRequested(dataSet);

      var payload = new JsonMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.has("routeIds"))
          .as("routeIds should be omitted when no entry has a populated routeId")
          .isFalse();
    }
  }

  @Nested
  @DisplayName("Saga result handling for slug-keyed routeIds (#1311)")
  class RouteIdsResultTests {

    @Test
    @DisplayName("SagaResultPayload round-trips the routeIds map through Jackson")
    void payloadRoundTripsRouteIds() {
      SagaResultPayload result =
          new SagaResultPayload(
              UUID.randomUUID().toString(),
              "proj-1",
              "http://frost",
              Map.of("traffic", "route-1", "weather", "route-2"),
              "svc-1",
              "http://public",
              List.of("pipe-1"),
              null,
              null,
              null);

      ObjectMapper mapper = new JsonMapper();
      String json = mapper.writeValueAsString(result);
      SagaResultPayload roundTripped = mapper.readValue(json, SagaResultPayload.class);

      assertThat(roundTripped.routeIds()).containsEntry("traffic", "route-1");
      assertThat(roundTripped.routeIds()).containsEntry("weather", "route-2");
    }

    @Test
    @DisplayName("Legacy singular 'routeId' key is silently ignored (pre-#1311 wire shape)")
    void legacyRouteIdKeyIsIgnored() {
      // Document behavior: a stale producer or DLQ replay carrying the pre-#1311 singular
      // routeId key deserializes cleanly via @JsonIgnoreProperties(ignoreUnknown=true), and
      // routeIds is null on the parsed payload. If we ever want bridge compatibility this test
      // forces an explicit decision rather than a silent drop.
      String legacyJson =
          "{\"datasetId\":\""
              + UUID.randomUUID()
              + "\",\"projectId\":\"proj-1\",\"routeId\":\"legacy-route\"}";

      SagaResultPayload parsed = new JsonMapper().readValue(legacyJson, SagaResultPayload.class);

      assertThat(parsed.projectId()).isEqualTo("proj-1");
      assertThat(parsed.routeIds())
          .as("singular legacy 'routeId' is dropped — no bridge from pre-#1311 wire shape")
          .isNull();
    }

    @Test
    @DisplayName("Null routeIds round-trips as null (distinct from empty map)")
    void nullRouteIdsRoundTrip() {
      SagaResultPayload result =
          new SagaResultPayload(
              UUID.randomUUID().toString(),
              "proj-1",
              "http://frost",
              null,
              "svc-1",
              "http://public",
              List.of("pipe-1"),
              null,
              null,
              null);

      ObjectMapper mapper = new JsonMapper();
      String json = mapper.writeValueAsString(result);
      SagaResultPayload roundTripped = mapper.readValue(json, SagaResultPayload.class);

      assertThat(roundTripped.routeIds()).isNull();
    }
  }

  @Nested
  @DisplayName("sendTrigger() failures")
  class SendTriggerTests {

    private DataSet emptyDataSet() {
      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of());
      return dataSet;
    }

    private void stubKafkaFailure(Exception cause) {
      when(kafkaTemplate.send(anyString(), anyString(), anyString()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new KafkaProducerException(
                      new ProducerRecord<>("test.saga.trigger", "{}"), "Failed to send", cause)));
    }

    @Test
    @DisplayName("a trigger above the Kafka size limit fails as invalid input with its UTF-8 size")
    void tooLargeTriggerFailsAsInvalidInputWithItsSize() {
      DataSet dataSet = emptyDataSet();
      dataSet.setName("Straßenbäume");
      stubKafkaFailure(new RecordTooLargeException("too large"));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageStartingWith(
              "The saga trigger of this Dataset is " + sentTriggerBytes() + " bytes, ");
    }

    private int sentTriggerBytes() {
      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      verify(kafkaTemplate).send(anyString(), anyString(), jsonCaptor.capture());
      return jsonCaptor.getValue().getBytes(StandardCharsets.UTF_8).length;
    }

    @Test
    @DisplayName("any other broker failure stays an internal error")
    void otherBrokerFailureStaysInternal() {
      DataSet dataSet = emptyDataSet();
      stubKafkaFailure(new NotLeaderOrFollowerException("no leader"));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to send saga trigger to Kafka");
    }
  }
}

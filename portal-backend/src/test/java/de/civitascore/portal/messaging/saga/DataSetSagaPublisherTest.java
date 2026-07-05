package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;

  private DataSetSagaPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher =
        new DataSetSagaPublisher(
            kafkaTemplate,
            new JsonMapper(),
            new SagaProperties("test.saga.trigger", 5),
            dataSinkRepository,
            dataStructureVersionRepository);
  }

  private DataSource dataSource(UUID id, ConnectorType type) {
    DataSource ds = new DataSource();
    ds.setId(id);
    ds.setName("ds-" + id);
    ds.setConnectorType(type);
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

  private DataSink postgisSink(UUID id, String tableName) {
    DataSink sink = new DataSink();
    sink.setId(id);
    sink.setDataSinkType(DataSinkType.POSTGIS);
    Map<String, Object> cfg = new HashMap<>();
    cfg.put("tableName", tableName);
    sink.setConfiguration(cfg);
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
  @DisplayName("buildDatasources() deduplication")
  class BuildDatasourcesTests {

    @Test
    @DisplayName("deduplicates datasources shared across multiple pipelines")
    void deduplicatesDatasourcesAcrossPipelines() {
      UUID dsId = UUID.randomUUID();
      DataSource shared = dataSource(dsId, ConnectorType.MQTT);

      Pipeline p1 = pipeline(UUID.randomUUID(), shared);
      Pipeline p2 = pipeline(UUID.randomUUID(), shared);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(p1, p2));

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishCreateRequested(dataSet);

      ObjectMapper mapper = new JsonMapper();
      var payload = mapper.readTree(jsonCaptor.getValue());
      var datasources = payload.get("datasources");
      assertThat(datasources).isNotNull();
      assertThat(datasources.size()).as("Shared datasource should appear only once").isEqualTo(1);
      assertThat(datasources.get(0).get("id").asString()).isEqualTo(dsId.toString());
    }
  }

  @Nested
  @DisplayName("buildDatasinks() schema resolution")
  class BuildDatasinksTests {

    private DataSink postgisSink(UUID id, UUID dsvId, String tableName) {
      DataSink sink = new DataSink();
      sink.setId(id);
      sink.setDataSinkType(DataSinkType.POSTGIS);
      Map<String, Object> cfg = new HashMap<>();
      cfg.put("tableName", tableName);
      cfg.put("dataStructureVersionId", dsvId.toString());
      sink.setConfiguration(cfg);
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
    @DisplayName("carries the referenced DSV's persisted model (JSON Schema) on the sink")
    void carriesPersistedModelOnSink() {
      UUID dsvId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(sinkId, dsvId, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      DataStructureVersion version = new DataStructureVersion();
      version.setModel(
          Map.of(
              "$id",
              "urn:core:datastructure:" + dsvId,
              "title",
              "Observation",
              "definitions",
              Map.of("Observation", Map.of("type", "object"))));

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));

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
      assertThat(ds.get("id").asString()).isEqualTo(sinkId.toString());
      assertThat(ds.get("type").asString()).isEqualTo("POSTGIS");
      assertThat(ds.get("configuration").get("tableName").asString())
          .isEqualTo("sensor_observations");
      assertThat(ds.get("dataStructure").get("title").asString()).isEqualTo("Observation");
      assertThat(ds.get("dataStructure").get("definitions").has("Observation")).isTrue();
    }

    @Test
    @DisplayName("fails the publish when a referenced DSV cannot be resolved")
    void failsWhenSchemaUnresolved() {
      UUID dsvId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(UUID.randomUUID(), dsvId, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("fails the publish when a referenced DSV carries no model")
    void failsWhenReferencedVersionHasNoModel() {
      UUID dsvId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(UUID.randomUUID(), dsvId, "sensor_observations");

      DataStructureVersion version = new DataStructureVersion(); // model is null
      DataSet dataSet = datasetWithPipeline(pipeline);
      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));

      assertThatThrownBy(() -> publisher.publishCreateRequested(dataSet))
          .isInstanceOf(InvalidInputException.class);
    }

    @Test
    @DisplayName("fails the publish when a referenced DSV carries an empty model")
    void failsWhenReferencedVersionHasEmptyModel() {
      UUID dsvId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(UUID.randomUUID(), dsvId, "sensor_observations");

      DataStructureVersion version = new DataStructureVersion();
      version.setModel(Map.of());
      DataSet dataSet = datasetWithPipeline(pipeline);
      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));

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
      UUID dsvId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(sinkId, dsvId, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      DataStructureVersion version = new DataStructureVersion();
      version.setModel(Map.of("$id", "urn:core:datastructure:" + dsvId));

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));

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
      assertThat(ds.get("id").asString()).isEqualTo(sinkId.toString());
      assertThat(ds.get("type").asString()).isEqualTo("POSTGIS");
      assertThat(ds.get("configuration").get("tableName").asString())
          .isEqualTo("sensor_observations");
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
  }

  @Nested
  @DisplayName("toPipelineEntry() per-pipeline source/sink association")
  class PipelineAssociationTests {

    @Test
    @DisplayName("pipeline entries carry their own sorted dataSourceIds and dataSinkIds")
    void pipelineEntriesCarrySourceAndSinkIds() {
      DataSource dsA =
          dataSource(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"), ConnectorType.MQTT);
      DataSource dsB =
          dataSource(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"), ConnectorType.MQTT);
      Pipeline pipeline = pipeline(UUID.randomUUID(), dsB, dsA);

      DataSink sinkA =
          postgisSink(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000003"), "table_a");
      DataSink sinkB =
          postgisSink(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000004"), "table_b");
      when(dataSinkRepository.findByPipelineId(pipeline.getId())).thenReturn(List.of(sinkB, sinkA));

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(pipeline));

      ArgumentCaptor<String> jsonCaptor = stubKafkaSend();
      publisher.publishCreateRequested(dataSet);

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("dataPipelines").get(0);
      var sourceIds = entry.get("dataSourceIds");
      var sinkIds = entry.get("dataSinkIds");
      assertThat(sourceIds).isNotNull();
      assertThat(sinkIds).isNotNull();
      assertThat(sourceIds.size()).isEqualTo(2);
      assertThat(sinkIds.size()).isEqualTo(2);
      assertThat(sourceIds.get(0).asString()).isEqualTo(dsA.getId().toString());
      assertThat(sourceIds.get(1).asString()).isEqualTo(dsB.getId().toString());
      assertThat(sinkIds.get(0).asString()).isEqualTo(sinkA.getId().toString());
      assertThat(sinkIds.get(1).asString()).isEqualTo(sinkB.getId().toString());
    }

    @Test
    @DisplayName("DELETE entries carry empty id lists")
    void deleteEntriesCarryEmptyIdLists() {
      Pipeline removed = pipeline(UUID.randomUUID());

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setProjectId("proj-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelineIds(List.of());
      dataSet.setPipelines(Set.of());

      ArgumentCaptor<String> jsonCaptor = stubKafkaSend();
      publisher.publishUpdateRequested(dataSet, Set.of(removed));

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("dataPipelines").get(0);
      assertThat(entry.get("action").asString()).isEqualTo("DELETE");
      assertThat(entry.get("dataSourceIds").size()).isZero();
      assertThat(entry.get("dataSinkIds").size()).isZero();
    }

    @Test
    @DisplayName("a pipeline with a null dataSources relation publishes an empty id list")
    void nullDataSourcesRelationPublishesEmptyIdList() {
      // buildDatasources() already treats a null relation as valid; the association list must not
      // abort the publication either
      Pipeline pipeline = pipeline(UUID.randomUUID());
      pipeline.setDataSources(null);

      DataSet dataSet = new DataSet();
      dataSet.setId(UUID.randomUUID());
      dataSet.setName("test");
      dataSet.setOpenDataAccess(false);
      dataSet.setPipelines(Set.of(pipeline));

      ArgumentCaptor<String> jsonCaptor = stubKafkaSend();
      publisher.publishCreateRequested(dataSet);

      var entry = new JsonMapper().readTree(jsonCaptor.getValue()).get("dataPipelines").get(0);
      assertThat(entry.get("dataSourceIds").size()).isZero();
    }
  }

  @Nested
  @DisplayName("namedApis serialization in saga triggers (#1311)")
  class NamedApisSerializationTests {

    private NamedApi api(String name, String slug, String routeId) {
      NamedApi api = new NamedApi();
      api.setName(name);
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
      // Saga contract carries only slug/standard/version: human-readable name and description
      // stay portal-backend-private.
      assertThat(bySlug.get("traffic").get("name")).as("name must not leak across saga").isNull();
      assertThat(bySlug.get("traffic").get("description"))
          .as("description must not leak across saga")
          .isNull();
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
}

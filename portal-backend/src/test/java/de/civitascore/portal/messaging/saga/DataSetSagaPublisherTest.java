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
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.service.DataStructureVersionService;
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
  @Mock private DataStructureVersionService dataStructureVersionService;

  private DataSetSagaPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher =
        new DataSetSagaPublisher(
            kafkaTemplate,
            new JsonMapper(),
            new SagaProperties("test.saga.trigger", 5),
            dataSinkRepository,
            dataStructureVersionRepository,
            dataStructureVersionService);
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

  @Nested
  @DisplayName("buildDatasources() deduplication")
  class BuildDatasourcesTests {

    @Test
    @DisplayName("deduplicates datasources shared across multiple pipelines")
    void deduplicatesDatasourcesAcrossPipelines() throws Exception {
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
    @DisplayName(
        "resolves the referenced DSV JSON Schema from Model Atlas and carries it on the sink")
    void resolvesSchemaFromModelAtlas() throws Exception {
      UUID dsvId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(sinkId, dsvId, "sensor_observations");
      DataSet dataSet = datasetWithPipeline(pipeline);

      DataStructureVersion version = new DataStructureVersion();
      version.setModelAtlasUri("atlas://dsv/" + dsvId);

      when(dataSinkRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));
      when(dataStructureVersionService.resolveJsonSchemaByAtlasUri("atlas://dsv/" + dsvId))
          .thenReturn(
              Optional.of(
                  Map.of(
                      "$id",
                      "urn:core:datastructure:" + dsvId,
                      "title",
                      "Observation",
                      "definitions",
                      Map.of("Observation", Map.of("type", "object")))));

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
    @DisplayName("FROST sink without a DSV reference carries a null dataStructure, no failure")
    void frostSinkHasNoSchema() throws Exception {
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
    void deleteTriggerCarriesDatasinks() throws Exception {
      UUID dsvId = UUID.randomUUID();
      UUID sinkId = UUID.randomUUID();
      Pipeline pipeline = pipeline(UUID.randomUUID());
      DataSink sink = postgisSink(sinkId, dsvId, "sensor_observations");

      DataStructureVersion version = new DataStructureVersion();
      version.setModelAtlasUri("atlas://dsv/" + dsvId);

      when(dataSinkRepository.findByPipelineId(pipeline.getId())).thenReturn(List.of(sink));
      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(version));
      when(dataStructureVersionService.resolveJsonSchemaByAtlasUri("atlas://dsv/" + dsvId))
          .thenReturn(Optional.of(Map.of("$id", "urn:core:datastructure:" + dsvId)));

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));

      publisher.publishDeleteRequested(datasetWithPipeline(pipeline));

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
  @DisplayName("buildPipelineDiff() classification")
  class BuildPipelineDiffTests {

    @Test
    @DisplayName("new pipelines get ADD, existing get UPDATE, removed get DELETE")
    void classifiesPipelineActionsCorrectly() throws Exception {
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

    private ArgumentCaptor<String> stubKafkaSend() {
      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(anyString(), anyString(), jsonCaptor.capture()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));
      return jsonCaptor;
    }

    private void assertNamedApisInPayload(String json) throws Exception {
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
    void createTriggerCarriesNamedApis() throws Exception {
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
    void updateTriggerCarriesNamedApisAndRouteIds() throws Exception {
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
    void deleteTriggerCarriesNamedApisAndRouteIds() throws Exception {
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
    void deleteTriggerOmitsEntriesWithoutRouteId() throws Exception {
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
    void updateTriggerOmitsEntriesWithoutRouteId() throws Exception {
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
    void emptyNamedApisOmitted() throws Exception {
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
    void routeIdsOmittedWhenNoneSet() throws Exception {
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
    void payloadRoundTripsRouteIds() throws Exception {
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
    void legacyRouteIdKeyIsIgnored() throws Exception {
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
    void nullRouteIdsRoundTrip() throws Exception {
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

package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayName("DataSetSagaPublisher artifacts against the embedded Model Forge")
class DataSetSagaPublisherIntegrationTest extends BaseKeycloakIntegrationTest {

  @Autowired private PortalTestDataFactory portalData;
  @Autowired private ModelRegistryGateway modelRegistryGateway;
  @Autowired private DataSetRepository dataSetRepository;
  @Autowired private DataSinkRepository dataSinkRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SagaProperties sagaProperties;
  @Autowired private PipelineClosureValidationProperties closureProperties;
  @Autowired private TransactionTemplate transactionTemplate;

  @AfterEach
  void cleanup() {
    portalData.cleanAll();
  }

  private DataStructureVersion structureVersion(Map<String, Object> model) {
    DataStructureVersion version = portalData.dataStructureVersion(portalData.dataStructure());
    portalData.attachModel(version, model);
    return version;
  }

  private static Map<String, Object> node(String id, String kind, String refField, String ref) {
    return Map.of("id", id, "kind", kind, refField, ref);
  }

  private static Map<String, Object> edge(String id, String source, String target) {
    return Map.of("id", id, "source", source, "target", target);
  }

  @SuppressWarnings("unchecked")
  private JsonNode publishCreate(UUID dataSetId) {
    KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    ArgumentCaptor<String> trigger = ArgumentCaptor.forClass(String.class);
    when(kafkaTemplate.send(anyString(), anyString(), trigger.capture()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new SendResult<>(null, new RecordMetadata(null, 0, 0, 0, 0, 0))));
    DataSetSagaPublisher publisher =
        new DataSetSagaPublisher(
            kafkaTemplate,
            objectMapper,
            sagaProperties,
            dataSinkRepository,
            modelRegistryGateway,
            closureProperties);
    transactionTemplate.executeWithoutResult(
        status ->
            publisher.publishCreateRequested(dataSetRepository.findById(dataSetId).orElseThrow()));
    return objectMapper.readTree(trigger.getValue());
  }

  private static List<String> nodeRefs(JsonNode pipelineEntry) {
    List<String> refs = new ArrayList<>();
    for (JsonNode node : pipelineEntry.get("data").get("nodes")) {
      for (String field : List.of("sourceRef", "mappingRef", "sinkRef")) {
        if (node.hasNonNull(field) && !node.get(field).asString().isBlank()) {
          refs.add(node.get(field).asString());
        }
      }
    }
    return refs;
  }

  @Test
  @DisplayName(
      "every node reference, every element and the current configurations are keys; the walk"
          + " stops at Data structures")
  void artifactsAreKeyedByTheUrnsTheAdapterLooksUp() {
    DataStructureVersion position = structureVersion(portalData.dataStructureVersionModel("Pos"));
    Map<String, Object> observationModel = portalData.dataStructureVersionModel("Observation");
    observationModel.put("properties", Map.of("position", Map.of("$ref", position.getModelUrn())));
    DataStructureVersion observation = structureVersion(observationModel);
    DataStructureVersion reading =
        structureVersion(portalData.dataStructureVersionModel("Reading"));

    DataSet dataSet = portalData.dataSet();
    DataSource dataSource =
        portalData.attachSourceConfiguration(
            portalData.dataSource(b -> b.connectorType(ConnectorType.MQTT)),
            Map.of(
                "urls", List.of("mqtt://broker:1883"),
                "topics", List.of("readings/+"),
                "element", reading.getModelUrn()));
    Pipeline pipeline = portalData.pipeline(dataSet, b -> b.dataSources(Set.of(dataSource)));
    DataSink sink =
        portalData.attachSinkConfiguration(
            portalData.dataSink(dataSet, pipeline, s -> s.setDataSinkType(DataSinkType.POSTGIS)),
            Map.of("tableName", "observations", "element", observation.getModelUrn()));
    String mapping =
        modelRegistryGateway
            .storePayload(
                PayloadKind.MAPPING,
                Optional.empty(),
                UUID.randomUUID(),
                "Reading to Observation",
                Map.of(
                    "source", reading.getModelUrn(),
                    "target", observation.getModelUrn(),
                    "fields", Map.of()),
                null)
            .versionedUrn();
    String pinnedSource = dataSource.getConfigurationUrn();
    portalData.attachPipelineDefinition(
        pipeline,
        Map.of(
            "nodes",
            List.of(
                node("src", "source", "sourceRef", pinnedSource),
                node("map", "mapping", "mappingRef", mapping),
                node("sink", "sink", "sinkRef", sink.getConfigurationUrn())),
            "edges",
            List.of(edge("e1", "src", "map"), edge("e2", "map", "sink"))),
        null);
    String currentSource =
        portalData
            .attachSourceConfiguration(
                dataSource,
                Map.of(
                    "urls", List.of("mqtt://broker:1883"),
                    "topics", List.of("readings/v2/+"),
                    "element", reading.getModelUrn()))
            .getConfigurationUrn();

    JsonNode trigger = publishCreate(dataSet.getId());

    JsonNode artifacts = trigger.get("artifacts");
    JsonNode entry = trigger.get("dataPipelines").get(0);
    assertThat(currentSource).isNotEqualTo(pinnedSource);
    assertThat(nodeRefs(entry)).hasSize(3).allMatch(artifacts::has);
    assertThat(entry.get("dataSourceIds").get(0).asString()).isEqualTo(currentSource);
    assertThat(artifacts.has(currentSource)).isTrue();
    for (JsonNode artifact : artifacts) {
      if (artifact.hasNonNull("element")) {
        assertThat(artifacts.has(artifact.get("element").asString()))
            .as("the element %s", artifact.get("element"))
            .isTrue();
      }
    }
    assertThat(artifacts.has(observation.getModelUrn())).isTrue();
    assertThat(artifacts.has(position.getModelUrn()))
        .as("a Data structure only another Data structure references")
        .isFalse();
  }
}

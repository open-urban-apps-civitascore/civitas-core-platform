package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.civitascore.portal.config.BaseKeycloakIntegrationTest;
import de.civitascore.portal.config.PortalTestDataFactory;
import de.civitascore.portal.configuration.PipelineClosureValidationProperties;
import de.civitascore.portal.configuration.SagaProperties;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.service.validation.PipelineClosureValidator;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
  @Autowired private PipelineClosureValidator pipelineClosureValidator;
  @Autowired private AllowedScopes allowedScopes;

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

  /** The release check, for a caller with Platform-wide access. */
  private void validateRelease(UUID dataSetId) {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                PrincipalUserDetails.builder().userId(UUID.randomUUID()).build(), null, List.of()));
    try {
      allowedScopes.setWildcard();
      transactionTemplate.executeWithoutResult(
          status ->
              pipelineClosureValidator.validate(
                  dataSetRepository.findById(dataSetId).orElseThrow().getPipelines()));
    } finally {
      SecurityContextHolder.clearContext();
      RequestContextHolder.resetRequestAttributes();
    }
  }

  private static Map<String, Object> mqttConfiguration(DataStructureVersion element) {
    return Map.of(
        "urls", List.of("mqtt://broker:1883"),
        "topics", List.of("readings/+"),
        "element", element.getModelUrn());
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

  @Test
  @DisplayName(
      "a source node that names its Data source by :latest follows it to its current Data"
          + " structure, in the release check and in the artifacts")
  void latestSourceRef_afterTheDataSourceMovesToAnotherStructure_followsTheCurrentConfiguration() {
    DataStructureVersion abandoned =
        structureVersion(portalData.dataStructureVersionModel("Reading"));
    DataStructureVersion released =
        portalData.attachModel(
            portalData.dataStructureVersion(
                portalData.dataStructure(b -> b.dataStructureStatus(DataStructureStatus.AVAILABLE)),
                b -> b.dataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE)),
            portalData.dataStructureVersionModel("Reading, released"));
    DataSet dataSet = portalData.dataSet();
    DataSource dataSource =
        portalData.attachSourceConfiguration(
            portalData.dataSource(
                b ->
                    b.connectorType(ConnectorType.MQTT)
                        .dataSourceStatus(DataSourceStatus.AVAILABLE)
                        .dataStructureVersion(abandoned)),
            mqttConfiguration(abandoned));
    String firstSource = dataSource.getConfigurationUrn();
    Pipeline pipeline = portalData.pipeline(dataSet, b -> b.dataSources(Set.of(dataSource)));
    portalData.attachPipelineDefinition(
        pipeline,
        Map.of(
            "nodes",
            List.of(
                node(
                    "src",
                    "source",
                    "sourceRef",
                    dataSource.getConfigurationLogicalUrn() + ":latest")),
            "edges",
            List.of()),
        null);
    dataSource.setDataStructureVersion(released);
    String currentSource =
        portalData
            .attachSourceConfiguration(dataSource, mqttConfiguration(released))
            .getConfigurationUrn();

    assertThatCode(() -> validateRelease(dataSet.getId())).doesNotThrowAnyException();
    JsonNode artifacts = publishCreate(dataSet.getId()).get("artifacts");

    assertThat(currentSource).isNotEqualTo(firstSource);
    assertThat(List.copyOf(artifacts.propertyNames()))
        .containsExactlyInAnyOrder(currentSource, released.getModelUrn());
  }
}

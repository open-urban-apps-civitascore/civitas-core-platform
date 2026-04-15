package de.civitascore.portal.messaging.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import java.util.List;
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
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSetSagaPublisher Tests")
class DataSetSagaPublisherTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplate;

  private DataSetSagaPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new DataSetSagaPublisher(kafkaTemplate, new JsonMapper());
    ReflectionTestUtils.setField(publisher, "triggerTopic", "test.saga.trigger");
    ReflectionTestUtils.setField(publisher, "publishTimeoutSeconds", 5);
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
      when(kafkaTemplate.send(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              jsonCaptor.capture()))
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
      dataSet.setRouteId("route-1");
      dataSet.setServiceId("svc-1");
      dataSet.setPipelineIds(List.of("pipe-1"));
      dataSet.setPipelines(Set.of(existingPipeline, newPipeline));

      Set<Pipeline> previousPipelines = Set.of(existingPipeline, removedPipeline);

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      when(kafkaTemplate.send(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              jsonCaptor.capture()))
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
}

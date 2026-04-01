package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DataSet Entity Tests")
class DataSetEntityTest {

  private DataSet dataSetWithId(UUID id) {
    DataSet dataSet = new DataSet();
    dataSet.setId(id);
    dataSet.setName("DataSet-" + id.toString().substring(0, 8));
    return dataSet;
  }

  private Distribution distributionWithId(UUID id) {
    Distribution distribution = new Distribution();
    distribution.setId(id);
    return distribution;
  }

  private Pipeline pipelineWithId(UUID id) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id);
    pipeline.setName("Pipeline-" + id.toString().substring(0, 8));
    return pipeline;
  }

  @Nested
  @DisplayName("clearInfrastructureFields()")
  class ClearInfrastructureFieldsTests {

    @Test
    @DisplayName("Should clear all infrastructure fields")
    void shouldClearAllInfrastructureFields() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      dataSet.setProjectId("project-1");
      dataSet.setFrostBaseUrl("http://frost.example.com");
      dataSet.setRouteId("route-1");
      dataSet.setServiceId("service-1");
      dataSet.setPublicUrl("http://public.example.com");
      dataSet.setPipelineIds(List.of("pipeline-1", "pipeline-2"));

      dataSet.clearInfrastructureFields();

      assertThat(dataSet.getProjectId()).isNull();
      assertThat(dataSet.getFrostBaseUrl()).isNull();
      assertThat(dataSet.getRouteId()).isNull();
      assertThat(dataSet.getServiceId()).isNull();
      assertThat(dataSet.getPublicUrl()).isNull();
      assertThat(dataSet.getPipelineIds()).isNull();
    }

    @Test
    @DisplayName("Should handle already null fields without error")
    void shouldHandleAlreadyNullFields() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());

      dataSet.clearInfrastructureFields();

      assertThat(dataSet.getProjectId()).isNull();
      assertThat(dataSet.getFrostBaseUrl()).isNull();
      assertThat(dataSet.getRouteId()).isNull();
      assertThat(dataSet.getServiceId()).isNull();
      assertThat(dataSet.getPublicUrl()).isNull();
      assertThat(dataSet.getPipelineIds()).isNull();
    }
  }

  @Nested
  @DisplayName("setDistributions()")
  class SetDistributionsTests {

    @Test
    @DisplayName("Should clear and add new distributions")
    void shouldClearAndAddDistributions() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      Distribution old = distributionWithId(UUID.randomUUID());
      dataSet.setDistributions(Set.of(old));

      Distribution new1 = distributionWithId(UUID.randomUUID());
      Distribution new2 = distributionWithId(UUID.randomUUID());
      dataSet.setDistributions(Set.of(new1, new2));

      assertThat(dataSet.getDistributions()).containsExactlyInAnyOrder(new1, new2);
      assertThat(dataSet.getDistributions()).doesNotContain(old);
    }

    @Test
    @DisplayName("Should clear distributions when null is passed")
    void shouldClearWhenNull() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      Distribution dist = distributionWithId(UUID.randomUUID());
      dataSet.setDistributions(Set.of(dist));

      dataSet.setDistributions(null);

      assertThat(dataSet.getDistributions()).isEmpty();
    }
  }

  @Nested
  @DisplayName("setPipelines()")
  class SetPipelinesTests {

    @Test
    @DisplayName("Should clear and add new pipelines")
    void shouldClearAndAddPipelines() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      Pipeline old = pipelineWithId(UUID.randomUUID());
      dataSet.setPipelines(Set.of(old));

      Pipeline new1 = pipelineWithId(UUID.randomUUID());
      Pipeline new2 = pipelineWithId(UUID.randomUUID());
      dataSet.setPipelines(Set.of(new1, new2));

      assertThat(dataSet.getPipelines()).containsExactlyInAnyOrder(new1, new2);
      assertThat(dataSet.getPipelines()).doesNotContain(old);
    }
  }
}

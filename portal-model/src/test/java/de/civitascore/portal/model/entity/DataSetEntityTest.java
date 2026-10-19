package de.civitascore.portal.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import de.civitascore.portal.model.embedded.ApiStandard;
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

  private Pipeline pipelineWithId(UUID id) {
    Pipeline pipeline = new Pipeline();
    pipeline.setId(id);
    pipeline.setName("Pipeline-" + id.toString().substring(0, 8));
    return pipeline;
  }

  @Nested
  @DisplayName("clearRouteAndPipelineInfrastructure()")
  class ClearRouteAndPipelineInfrastructureTests {

    @Test
    @DisplayName("Should clear route/pipeline fields and per-API route IDs, keep the sink")
    void shouldClearRouteAndPipelineKeepSink() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      dataSet.setProjectId("project-1");
      dataSet.setFrostBaseUrl("http://frost.example.com");
      dataSet.setServiceId("service-1");
      dataSet.setPublicUrl("http://public.example.com");
      dataSet.setPipelineIds(List.of("pipeline-1", "pipeline-2"));

      NamedApi traffic = new NamedApi();
      traffic.setName("Traffic Sensor Readings");
      traffic.setSlug("traffic");
      traffic.setStandard(ApiStandard.STA);
      traffic.setRouteId("route-1");
      NamedApi weather = new NamedApi();
      weather.setName("Weather Sensor Readings");
      weather.setSlug("weather");
      weather.setStandard(ApiStandard.STA);
      weather.setRouteId("route-2");
      dataSet.setNamedApis(Set.of(traffic, weather));

      dataSet.clearRouteAndPipelineInfrastructure();

      // Route/consumer-access layer is torn down.
      assertThat(dataSet.getServiceId()).isNull();
      assertThat(dataSet.getPublicUrl()).isNull();
      assertThat(dataSet.getPipelineIds()).isNull();
      assertThat(dataSet.getNamedApis()).allSatisfy(api -> assertThat(api.getRouteId()).isNull());
      // Data-holding sink references survive an unrelease.
      assertThat(dataSet.getProjectId()).isEqualTo("project-1");
      assertThat(dataSet.getFrostBaseUrl()).isEqualTo("http://frost.example.com");
      assertThat(dataSet.getNamedApis())
          .as("named-API entries are preserved on unrelease")
          .hasSize(2);
    }

    @Test
    @DisplayName("Should handle already null fields without error")
    void shouldHandleAlreadyNullFields() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());

      dataSet.clearRouteAndPipelineInfrastructure();

      assertThat(dataSet.getServiceId()).isNull();
      assertThat(dataSet.getPublicUrl()).isNull();
      assertThat(dataSet.getPipelineIds()).isNull();
      assertThat(dataSet.getNamedApis()).isEmpty();
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

  @Nested
  @DisplayName("setNamedApis()")
  class SetNamedApisTests {

    private NamedApi namedApi(String slug) {
      NamedApi api = new NamedApi();
      api.setName("API " + slug);
      api.setSlug(slug);
      api.setStandard(ApiStandard.STA);
      return api;
    }

    @Test
    @DisplayName("Should clear and add new named APIs")
    void shouldClearAndAddNamedApis() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      NamedApi old = namedApi("old");
      dataSet.setNamedApis(Set.of(old));

      NamedApi traffic = namedApi("traffic");
      NamedApi weather = namedApi("weather");
      dataSet.setNamedApis(Set.of(traffic, weather));

      assertThat(dataSet.getNamedApis()).containsExactlyInAnyOrder(traffic, weather);
      assertThat(dataSet.getNamedApis()).doesNotContain(old);
    }

    @Test
    @DisplayName("Should set parent dataSet FK on each entry (orphan-removal contract)")
    void shouldSetParentReferenceOnEachEntry() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      NamedApi traffic = namedApi("traffic");
      NamedApi weather = namedApi("weather");
      assertThat(traffic.getDataSet()).isNull();
      assertThat(weather.getDataSet()).isNull();

      dataSet.setNamedApis(Set.of(traffic, weather));

      assertThat(traffic.getDataSet()).isSameAs(dataSet);
      assertThat(weather.getDataSet()).isSameAs(dataSet);
    }

    @Test
    @DisplayName("Should clear named APIs when null is passed")
    void shouldClearWhenNull() {
      DataSet dataSet = dataSetWithId(UUID.randomUUID());
      dataSet.setNamedApis(Set.of(namedApi("traffic")));

      dataSet.setNamedApis(null);

      assertThat(dataSet.getNamedApis()).isEmpty();
    }
  }
}

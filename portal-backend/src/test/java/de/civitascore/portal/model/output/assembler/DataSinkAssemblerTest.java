package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.FrostConfigurationOutput;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DataSinkAssemblerTest {

  @Mock private DataSinkMapper dataSinkMapper;
  @Mock private ModelRegistryGateway modelRegistryGateway;
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;

  @InjectMocks private DataSinkAssembler assembler;

  /**
   * Pins a registry-stored configuration on the sink and stubs the gateway read for it. A null or
   * empty configuration means nothing was ever stored (FROST): the pin stays null.
   */
  private void applyConfiguration(DataSink entity, Map<String, Object> configuration) {
    if (configuration == null || configuration.isEmpty()) {
      return;
    }
    String urn = "urn:core:platform:civitas:data-sink:common:sink-" + UUID.randomUUID() + ":1.0.0";
    entity.setConfigurationUrn(urn);
    lenient()
        .when(modelRegistryGateway.fetchPayload(urn))
        .thenReturn(Optional.of(new ModelRegistryGateway.RegistryDocument(configuration, null)));
  }

  private DataSink sinkWithPipeline(DataSinkType type, Map<String, Object> configuration) {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("ds");

    Pipeline pipeline = new Pipeline();
    pipeline.setId(UUID.randomUUID());
    pipeline.setName("pl");
    pipeline.setDataSet(dataSet);

    DataSink entity = new DataSink();
    entity.setDataSet(dataSet);
    entity.setPipeline(pipeline);
    entity.setDataSinkType(type);
    applyConfiguration(entity, configuration);
    return entity;
  }

  private DataSink sinkWithoutPipeline(DataSinkType type, Map<String, Object> configuration) {
    DataSet dataSet = new DataSet();
    dataSet.setId(UUID.randomUUID());
    dataSet.setName("ds");

    DataSink entity = new DataSink();
    entity.setDataSet(dataSet);
    entity.setDataSinkType(type);
    applyConfiguration(entity, configuration);
    return entity;
  }

  @Nested
  @DisplayName("enrichDto() — FROST")
  class FrostEnrichment {

    @Test
    @DisplayName("Should set an empty FrostConfiguration")
    void shouldSetEmptyFrostConfiguration() {
      DataSink entity = sinkWithPipeline(DataSinkType.FROST, Map.of());

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(FrostConfigurationOutput.class);
    }

    @Test
    @DisplayName("Should set FrostConfiguration even when entity configuration is null")
    void shouldHandleNullConfigurationForFrost() {
      DataSink entity = sinkWithPipeline(DataSinkType.FROST, null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(FrostConfigurationOutput.class);
    }

    @Test
    @DisplayName("Should echo the referenced element URN back onto the output")
    void shouldEchoElement() {
      String elementUrn = "urn:core:platform:civitas:element:common:target:1.0.0";
      DataSink entity = sinkWithPipeline(DataSinkType.FROST, Map.of("element", elementUrn));

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(FrostConfigurationOutput.class);
      FrostConfigurationOutput config = (FrostConfigurationOutput) result.getConfiguration();
      assertThat(config.getElement()).isEqualTo(elementUrn);
    }
  }

  @Nested
  @DisplayName("enrichDto() — POSTGIS")
  class PostgisEnrichment {

    @Test
    @DisplayName("Should build PostgisConfigurationOutput with tableName and element URN")
    void shouldBuildPostgisConfigurationWithElement() {
      String elementUrn = "urn:core:platform:civitas:element:common:observation:1.0.0";
      DataSink entity =
          sinkWithPipeline(
              DataSinkType.POSTGIS, Map.of("tableName", "traffic_data", "element", elementUrn));

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isEqualTo("traffic_data");
      assertThat(config.getElement()).isEqualTo(elementUrn);
    }

    @Test
    @DisplayName("Should resolve the element URN to a nested structure-version summary")
    void shouldResolveStructureVersionSummary() {
      String elementUrn =
          "urn:core:standard:openurbanapps:datastructure:mobility:verkehrsmessung:70dn2lp8jo:1.0.0";
      DataStructureVersion version = new DataStructureVersion();
      DataStructureVersionSummaryDTO summary = new DataStructureVersionSummaryDTO();
      summary.setId(UUID.randomUUID());
      summary.setDataStructureId(UUID.randomUUID());
      when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(elementUrn))
          .thenReturn(Optional.of(version));
      when(dataStructureVersionMapper.toSummary(version)).thenReturn(summary);
      DataSink entity =
          sinkWithPipeline(DataSinkType.POSTGIS, Map.of("tableName", "t", "element", elementUrn));

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getDataStructureVersion()).isSameAs(summary);
    }

    @Test
    @DisplayName("Should leave the summary absent when the element URN resolves to no version")
    void shouldLeaveSummaryAbsentOnDanglingElement() {
      String elementUrn = "urn:core:platform:civitas:datastructure:common:gone:x:1.0.0";
      when(dataStructureVersionRepository.findFirstByModelUrnStartingWith(elementUrn))
          .thenReturn(Optional.empty());
      DataSink entity =
          sinkWithPipeline(DataSinkType.POSTGIS, Map.of("tableName", "t", "element", elementUrn));

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      // The listing must survive a dangling reference; only the summary is missing.
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getElement()).isEqualTo(elementUrn);
      assertThat(config.getDataStructureVersion()).isNull();
    }

    @Test
    @DisplayName("Should return empty PostgisConfigurationOutput when entity configuration is null")
    void shouldHandleNullConfigurationForPostgis() {
      DataSink entity = sinkWithPipeline(DataSinkType.POSTGIS, null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isNull();
      assertThat(config.getElement()).isNull();
    }
  }

  @Nested
  @DisplayName("enrichDto() — null type")
  class NullType {

    @Test
    @DisplayName("Should leave configuration null when dataSinkType is null")
    void shouldLeaveConfigurationNullWhenTypeIsNull() {
      DataSink entity = sinkWithPipeline(null, null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isNull();
    }
  }

  @Nested
  @DisplayName("enrichDto() — inUse")
  class InUseFlag {

    @Test
    @DisplayName("Should set inUse=true when the DataSink has a linked pipeline")
    void shouldSetInUseTrueWhenPipelineLinked() {
      DataSink entity = sinkWithPipeline(DataSinkType.FROST, Map.of());

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.isInUse()).isTrue();
    }

    @Test
    @DisplayName("Should set inUse=false when the DataSink has no linked pipeline")
    void shouldSetInUseFalseWhenNoPipeline() {
      DataSink entity = sinkWithoutPipeline(DataSinkType.FROST, Map.of());

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.isInUse()).isFalse();
    }
  }
}

package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import de.civitascore.portal.model.output.FrostConfigurationOutput;
import de.civitascore.portal.model.output.PostgisConfigurationOutput;
import de.civitascore.portal.model.output.summary.DataStructureVersionSummaryDTO;
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
  @Mock private DataStructureVersionRepository dataStructureVersionRepository;
  @Mock private DataStructureVersionMapper dataStructureVersionMapper;

  @InjectMocks private DataSinkAssembler assembler;

  @Nested
  @DisplayName("enrichDto() — FROST")
  class FrostEnrichment {

    @Test
    @DisplayName("Should set an empty FrostConfiguration")
    void shouldSetEmptyFrostConfiguration() {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.FROST);
      entity.setConfiguration(Map.of());

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(FrostConfigurationOutput.class);
      verifyNoInteractions(dataStructureVersionRepository);
    }

    @Test
    @DisplayName("Should set FrostConfiguration even when entity configuration is null")
    void shouldHandleNullConfigurationForFrost() {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.FROST);
      entity.setConfiguration(null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(FrostConfigurationOutput.class);
      verifyNoInteractions(dataStructureVersionRepository);
    }
  }

  @Nested
  @DisplayName("enrichDto() — POSTGIS")
  class PostgisEnrichment {

    @Test
    @DisplayName("Should build PostgisOutputConfiguration with tableName and resolved DSV summary")
    void shouldBuildPostgisConfigurationWithDsv() {
      UUID dsvId = UUID.randomUUID();

      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      entity.setConfiguration(
          Map.of("tableName", "traffic_data", "dataStructureVersionId", dsvId.toString()));

      DataStructureVersion dsv = new DataStructureVersion();
      DataStructureVersionSummaryDTO dsvSummary = new DataStructureVersionSummaryDTO();
      dsvSummary.setId(dsvId);
      dsvSummary.setVersion("1.0.0");

      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.of(dsv));
      when(dataStructureVersionMapper.toSummary(dsv)).thenReturn(dsvSummary);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isEqualTo("traffic_data");
      assertThat(config.getDataStructureVersion()).isSameAs(dsvSummary);
      verify(dataStructureVersionRepository).findById(dsvId);
    }

    @Test
    @DisplayName("Should set tableName but leave DSV null when the DSV ID cannot be resolved")
    void shouldLeaveDataStructureVersionNullWhenNotFound() {
      UUID dsvId = UUID.randomUUID();

      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      entity.setConfiguration(
          Map.of("tableName", "traffic_data", "dataStructureVersionId", dsvId.toString()));

      when(dataStructureVersionRepository.findById(dsvId)).thenReturn(Optional.empty());

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isEqualTo("traffic_data");
      assertThat(config.getDataStructureVersion()).isNull();
    }

    @Test
    @DisplayName(
        "Should leave DSV null and not query the repository when dataStructureVersionId is not a valid UUID")
    void shouldLeaveDataStructureVersionNullWhenDsvIdIsMalformed() {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      entity.setConfiguration(
          Map.of("tableName", "traffic_data", "dataStructureVersionId", "not-a-uuid"));

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isEqualTo("traffic_data");
      assertThat(config.getDataStructureVersion()).isNull();
      verifyNoInteractions(dataStructureVersionRepository);
    }

    @Test
    @DisplayName("Should return empty PostgisOutputConfiguration when entity configuration is null")
    void shouldHandleNullConfigurationForPostgis() {
      DataSink entity = new DataSink();
      entity.setDataSinkType(DataSinkType.POSTGIS);
      entity.setConfiguration(null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isInstanceOf(PostgisConfigurationOutput.class);
      PostgisConfigurationOutput config = (PostgisConfigurationOutput) result.getConfiguration();
      assertThat(config.getTableName()).isNull();
      assertThat(config.getDataStructureVersion()).isNull();
      verifyNoInteractions(dataStructureVersionRepository);
    }
  }

  @Nested
  @DisplayName("enrichDto() — null type")
  class NullType {

    @Test
    @DisplayName("Should leave configuration null when dataSinkType is null")
    void shouldLeaveConfigurationNullWhenTypeIsNull() {
      DataSink entity = new DataSink();
      entity.setDataSinkType(null);

      DataSinkOutputDTO result = assembler.enrichDto(new DataSinkOutputDTO(), entity);

      assertThat(result.getConfiguration()).isNull();
      verifyNoInteractions(dataStructureVersionRepository);
    }
  }
}

package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourceAssembler Tests")
class DataSourceAssemblerTest {

  @Mock private DataSourceMapper dataSourceMapper;
  @Mock private ConnectorHandlerRegistry connectorHandlerRegistry;
  @Mock private PipelineRepository pipelineRepository;

  private DataSourceAssembler assembler() {
    return new DataSourceAssembler(dataSourceMapper, connectorHandlerRegistry, pipelineRepository);
  }

  @Nested
  @DisplayName("enrichDto — datapoolScope")
  class EnrichDtoScopeTests {

    @Test
    @DisplayName("ALL scope: datapoolIds is empty, type is ALL")
    void allScope_setsTypeAndNoIds() {
      DataSource entity = DataSource.builder().build();
      entity.setId(UUID.randomUUID());
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      when(pipelineRepository.existsByDataSourcesId(entity.getId())).thenReturn(false);

      DataSourceOutputDTO result = assembler().enrichDto(new DataSourceOutputDTO(), entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.ALL);
      assertThat(result.getDatapoolScope().getDatapoolIds()).isEmpty();
    }

    @Test
    @DisplayName("NONE scope: datapoolIds is empty, type is NONE")
    void noneScope_setsTypeAndNoIds() {
      DataSource entity = DataSource.builder().build();
      entity.setId(UUID.randomUUID());
      entity.setDatapoolScopeType(DatapoolScopeType.NONE);

      when(pipelineRepository.existsByDataSourcesId(entity.getId())).thenReturn(false);

      DataSourceOutputDTO result = assembler().enrichDto(new DataSourceOutputDTO(), entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.NONE);
      assertThat(result.getDatapoolScope().getDatapoolIds()).isEmpty();
    }

    @Test
    @DisplayName("SPECIFIC scope: datapoolIds are extracted from scopedDataPools")
    void specificScope_extractsDatapoolIds() {
      UUID poolId1 = UUID.randomUUID();
      UUID poolId2 = UUID.randomUUID();
      DataPool pool1 = new DataPool();
      pool1.setId(poolId1);
      DataPool pool2 = new DataPool();
      pool2.setId(poolId2);

      DataSource entity = DataSource.builder().build();
      entity.setId(UUID.randomUUID());
      entity.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      entity.getScopedDataPools().add(pool1);
      entity.getScopedDataPools().add(pool2);

      when(pipelineRepository.existsByDataSourcesId(entity.getId())).thenReturn(false);

      DataSourceOutputDTO result = assembler().enrichDto(new DataSourceOutputDTO(), entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(result.getDatapoolScope().getDatapoolIds())
          .containsExactlyInAnyOrder(poolId1, poolId2);
    }

    @Test
    @DisplayName("inUse flag is correctly set from PipelineRepository")
    void inUseFlag_isSetFromPipelineRepository() {
      DataSource entity = DataSource.builder().build();
      entity.setId(UUID.randomUUID());
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      when(pipelineRepository.existsByDataSourcesId(entity.getId())).thenReturn(true);

      DataSourceOutputDTO result = assembler().enrichDto(new DataSourceOutputDTO(), entity);

      assertThat(result.isInUse()).isTrue();
    }
  }

  @Nested
  @DisplayName("toInput — datapoolScope")
  class ToInputScopeTests {

    @Test
    @DisplayName("ALL scope: type is ALL, datapoolIds is empty")
    void allScope_setsTypeAndNoIds() {
      DataSource entity = DataSource.builder().build();
      entity.setDatapoolScopeType(DatapoolScopeType.ALL);

      when(dataSourceMapper.toInput(entity)).thenReturn(new DataSourceInputDTO());

      DataSourceInputDTO result = assembler().toInput(entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.ALL);
      assertThat(result.getDatapoolScope().getDatapoolIds()).isEmpty();
    }

    @Test
    @DisplayName("NONE scope: type is NONE, datapoolIds is empty")
    void noneScope_setsTypeAndNoIds() {
      DataSource entity = DataSource.builder().build();
      entity.setDatapoolScopeType(DatapoolScopeType.NONE);

      when(dataSourceMapper.toInput(entity)).thenReturn(new DataSourceInputDTO());

      DataSourceInputDTO result = assembler().toInput(entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.NONE);
      assertThat(result.getDatapoolScope().getDatapoolIds()).isEmpty();
    }

    @Test
    @DisplayName("SPECIFIC scope: datapoolIds are extracted from scopedDataPools")
    void specificScope_extractsDatapoolIds() {
      UUID poolId = UUID.randomUUID();
      DataPool pool = new DataPool();
      pool.setId(poolId);

      DataSource entity = DataSource.builder().build();
      entity.setDatapoolScopeType(DatapoolScopeType.SPECIFIC);
      entity.getScopedDataPools().add(pool);

      when(dataSourceMapper.toInput(entity)).thenReturn(new DataSourceInputDTO());

      DataSourceInputDTO result = assembler().toInput(entity);

      assertThat(result.getDatapoolScope()).isNotNull();
      assertThat(result.getDatapoolScope().getType()).isEqualTo(DatapoolScopeType.SPECIFIC);
      assertThat(result.getDatapoolScope().getDatapoolIds()).containsExactly(poolId);
    }
  }
}

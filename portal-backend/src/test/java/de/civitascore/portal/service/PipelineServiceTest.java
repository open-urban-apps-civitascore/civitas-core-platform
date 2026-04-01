package de.civitascore.portal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

  @Mock private PipelineRepository pipelineRepository;
  @Mock private PipelineMapper pipelineMapper;
  @Mock private DataSetRepository dataSetRepository;
  @Mock private DataSourceRepository dataSourceRepository;

  @InjectMocks private PipelineService pipelineService;

  @Nested
  @DisplayName("DataSource Linking Validation")
  class DataSourceLinkingValidation {

    @Test
    @DisplayName("Should reject linking a DRAFT datasource to a pipeline")
    void shouldRejectDraftDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

      DataSource draftDataSource = new DataSource();
      draftDataSource.setId(dataSourceId);
      draftDataSource.setName("draft-source");
      draftDataSource.setDataSourceStatus(DataSourceStatus.DRAFT);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      Pipeline entity = new Pipeline();
      entity.setName("test-pipeline");

      when(pipelineMapper.toEntity(any())).thenReturn(entity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dataSourceId)))
          .thenReturn(List.of(draftDataSource));

      assertThatThrownBy(() -> pipelineService.create(input))
          .isInstanceOf(InvalidInputException.class)
          .hasMessageContaining("AVAILABLE status");
    }

    @Test
    @DisplayName("Should accept linking an AVAILABLE datasource to a pipeline")
    void shouldAcceptAvailableDataSource() {
      UUID dataSetId = UUID.randomUUID();
      UUID dataSourceId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

      DataSource availableDataSource = new DataSource();
      availableDataSource.setId(dataSourceId);
      availableDataSource.setName("available-source");
      availableDataSource.setDataSourceStatus(DataSourceStatus.AVAILABLE);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("test-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(Set.of(dataSourceId));

      Pipeline pipelineEntity = new Pipeline();
      pipelineEntity.setId(UUID.randomUUID());
      pipelineEntity.setName("test-pipeline");
      pipelineEntity.setDataSet(dataSet);
      pipelineEntity.setDataSources(new HashSet<>());

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(dataSourceRepository.findAllById(Set.of(dataSourceId)))
          .thenReturn(List.of(availableDataSource));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Pipeline result = pipelineService.create(input);

      assertThat(result.getDataSources()).hasSize(1);
    }

    @Test
    @DisplayName("Should allow creating a pipeline without datasources")
    void shouldAllowNullDataSourceIds() {
      UUID dataSetId = UUID.randomUUID();

      DataSet dataSet = new DataSet();
      dataSet.setId(dataSetId);
      dataSet.setDataSetStatus(DataSetStatus.DRAFT);

      PipelineInputDTO input = new PipelineInputDTO();
      input.setName("provide-only-pipeline");
      input.setDataSetId(dataSetId);
      input.setDataSourceIds(null);

      Pipeline pipelineEntity = new Pipeline();
      pipelineEntity.setId(UUID.randomUUID());
      pipelineEntity.setName("provide-only-pipeline");
      pipelineEntity.setDataSet(dataSet);
      pipelineEntity.setDataSources(new HashSet<>());

      when(pipelineMapper.toEntity(any())).thenReturn(pipelineEntity);
      when(dataSetRepository.findById(dataSetId)).thenReturn(Optional.of(dataSet));
      when(pipelineRepository.findAllByNameAndDataSetId(any(), any())).thenReturn(Set.of());
      when(pipelineRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

      Pipeline result = pipelineService.create(input);

      assertThat(result).isNotNull();
    }
  }
}

package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineAssembler")
class PipelineAssemblerTest {

  @Mock private PipelineMapper pipelineMapper;
  @Mock private DataSinkRepository dataSinkRepository;
  @Mock private DataSourceRepository dataSourceRepository;

  @InjectMocks private PipelineAssembler assembler;

  private DataSink sinkWithId(UUID id) {
    DataSink sink = new DataSink();
    sink.setId(id);
    return sink;
  }

  private DataSource sourceWithId(UUID id) {
    DataSource source = new DataSource();
    source.setId(id);
    return source;
  }

  @Test
  @DisplayName("enrichDto populates dataSinkIds and dataSourceIds from their repositories")
  void enrichDto_populatesDataSinkAndDataSourceIds() {
    UUID pipelineId = UUID.randomUUID();
    UUID sinkId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);
    pipeline.setDataSources(Set.of(sourceWithId(sourceId)));

    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sinkWithId(sinkId)));
    when(dataSourceRepository.findIdsByPipelineId(pipelineId)).thenReturn(List.of(sourceId));

    PipelineOutputDTO dto = new PipelineOutputDTO();
    PipelineOutputDTO result = assembler.enrichDto(dto, pipeline);

    assertThat(result.getDataSinkIds()).containsExactly(sinkId);
    assertThat(result.getDataSourceIds()).containsExactly(sourceId);
  }

  @Test
  @DisplayName("toInput populates dataSinkIds and dataSourceIds from their repositories")
  void toInput_populatesDataSinkAndDataSourceIds() {
    UUID pipelineId = UUID.randomUUID();
    UUID sinkId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);
    pipeline.setDataSources(Set.of(sourceWithId(sourceId)));

    PipelineInputDTO pipelineInput = new PipelineInputDTO();

    when(pipelineMapper.toInput(pipeline)).thenReturn(pipelineInput);
    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sinkWithId(sinkId)));
    when(dataSourceRepository.findIdsByPipelineId(pipelineId)).thenReturn(List.of(sourceId));

    PipelineInputDTO result = assembler.toInput(pipeline);

    assertThat(result.getDataSinkIds()).containsExactly(sinkId);
    assertThat(result.getDataSourceIds()).containsExactly(sourceId);
  }
}

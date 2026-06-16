package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.PipelineOutputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import java.util.List;
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

  @InjectMocks private PipelineAssembler assembler;

  private DataSink sinkWithId(UUID id) {
    DataSink sink = new DataSink();
    sink.setId(id);
    return sink;
  }

  @Test
  @DisplayName("enrichDto populates dataSinkIds from repository")
  void enrichDto_populatesDataSinkIdsFromRepository() {
    UUID pipelineId = UUID.randomUUID();
    UUID sinkId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);

    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sinkWithId(sinkId)));

    PipelineOutputDTO dto = new PipelineOutputDTO();
    PipelineOutputDTO result = assembler.enrichDto(dto, pipeline);

    assertThat(result.getDataSinkIds()).containsExactly(sinkId);
  }

  @Test
  @DisplayName("toInput populates dataSinkIds from repository")
  void toInput_populatesDataSinkIds() {
    UUID pipelineId = UUID.randomUUID();
    UUID sinkId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);

    PipelineInputDTO pipelineInput = new PipelineInputDTO();

    when(pipelineMapper.toInput(pipeline)).thenReturn(pipelineInput);
    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sinkWithId(sinkId)));

    PipelineInputDTO result = assembler.toInput(pipeline);

    assertThat(result.getDataSinkIds()).containsExactly(sinkId);
  }
}

package de.civitascore.portal.model.output.assembler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
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
  @Mock private DataSinkAssembler dataSinkAssembler;
  @Mock private DataSinkRepository dataSinkRepository;

  @InjectMocks private PipelineAssembler assembler;

  @Test
  @DisplayName("enrichDto populates dataSinks from repository")
  void enrichDto_populatesDataSinksFromRepository() {
    UUID pipelineId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);

    DataSink sink = new DataSink();
    DataSinkOutputDTO sinkDto = new DataSinkOutputDTO();

    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sink));
    when(dataSinkAssembler.toOutput(sink)).thenReturn(sinkDto);

    PipelineOutputDTO dto = new PipelineOutputDTO();
    PipelineOutputDTO result = assembler.enrichDto(dto, pipeline);

    assertThat(result.getDataSinks()).containsExactly(sinkDto);
  }

  @Test
  @DisplayName("toInput includes DataSink inputs from repository")
  void toInput_includesDataSinkInputs() {
    UUID pipelineId = UUID.randomUUID();
    Pipeline pipeline = new Pipeline();
    pipeline.setId(pipelineId);

    DataSink sink = new DataSink();
    DataSinkInputDTO sinkInput = new DataSinkInputDTO();

    PipelineInputDTO pipelineInput = new PipelineInputDTO();

    when(pipelineMapper.toInput(pipeline)).thenReturn(pipelineInput);
    when(dataSinkRepository.findByPipelineId(pipelineId)).thenReturn(List.of(sink));
    when(dataSinkAssembler.<DataSinkInputDTO>toInput(sink)).thenReturn(sinkInput);

    PipelineInputDTO result = assembler.toInput(pipeline);

    assertThat(result.getDataSinks()).containsExactly(sinkInput);
  }
}

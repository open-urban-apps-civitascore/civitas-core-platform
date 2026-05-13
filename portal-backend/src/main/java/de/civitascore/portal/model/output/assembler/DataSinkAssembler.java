package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.output.DataSinkOutputDTO;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSink} entities to {@link DataSinkOutputDTO}. Participates in
 * the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataSinkAssembler implements BaseAssembler<DataSink, DataSinkOutputDTO, UUID> {

  private final DataSinkMapper dataSinkMapper;

  /** {@inheritDoc} Delegates to the {@link DataSinkMapper} for basic field mapping. */
  @Override
  public DataSinkOutputDTO mapToBaseDto(DataSink entity) {
    return dataSinkMapper.toOutput(entity);
  }

  /** {@inheritDoc} Converts a DataSink entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSink entity) {
    return (I) dataSinkMapper.toInput(entity);
  }
}

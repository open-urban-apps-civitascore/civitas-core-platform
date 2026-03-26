package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.mapper.UserMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.output.DataSpaceOutputDTO;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSpace} entities to {@link DataSpaceOutputDTO}. Participates
 * in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataSpaceAssembler implements BaseAssembler<DataSpace, DataSpaceOutputDTO, UUID> {

  private final DataSpaceMapper dataSpaceMapper;
  private final UserMapper userMapper;

  /**
   * {@inheritDoc} Maps data space fields including owner, parent, and child data space summaries.
   */
  @Override
  public DataSpaceOutputDTO mapToBaseDto(DataSpace entity) {
    DataSpaceOutputDTO output = dataSpaceMapper.toOutput(entity);

    // Map owner
    if (entity.getOwner() != null) {
      output.setOwner(userMapper.toSummary(entity.getOwner()));
    }

    // Map parentDataSpace
    if (entity.getParentDataSpace() != null) {
      output.setParentDataSpace(dataSpaceMapper.toSummary(entity.getParentDataSpace()));
    }

    // Map childDataSpaces
    if (entity.getChildDataSpaces() != null && !entity.getChildDataSpaces().isEmpty()) {
      output.setChildDataSpaces(
          entity.getChildDataSpaces().stream()
              .map(dataSpaceMapper::toSummary)
              .collect(Collectors.toList()));
    }

    return output;
  }

  /** {@inheritDoc} Converts a data space entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSpace entity) {
    return (I) dataSpaceMapper.toInput(entity);
  }
}

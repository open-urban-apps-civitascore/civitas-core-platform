package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataPool} entities to {@link DataPoolOutputDTO}. Enriches the
 * output with the list of assigned datasets.
 */
@Component
@RequiredArgsConstructor
public class DataPoolAssembler implements BaseAssembler<DataPool, DataPoolOutputDTO, UUID> {

  private final DataPoolMapper dataPoolMapper;
  private final DataSetRepository dataSetRepository;

  @Override
  public DataPoolOutputDTO mapToBaseDto(DataPool entity) {
    return dataPoolMapper.toOutput(entity);
  }

  @Override
  public DataPoolOutputDTO enrichDto(DataPoolOutputDTO dto, DataPool entity) {
    List<String> datasetIds =
        dataSetRepository.findAllByDataPoolId(entity.getId()).stream()
            .map(ds -> ds.getId().toString())
            .toList();
    dto.setDatasets(datasetIds);
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataPool entity) {
    return (I) dataPoolMapper.toInput(entity);
  }
}

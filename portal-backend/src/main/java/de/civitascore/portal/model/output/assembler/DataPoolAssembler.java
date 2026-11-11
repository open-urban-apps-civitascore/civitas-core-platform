package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataPoolMapper;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.input.DataPoolInputDTO;
import de.civitascore.portal.model.output.DataPoolOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataPool} entities to {@link DataPoolOutputDTO}. Enriches the
 * output with the list of assigned datasets.
 */
@Component
@RequiredArgsConstructor
public class DataPoolAssembler implements BaseAssembler<DataPool, DataPoolOutputDTO> {

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

  public DataPoolInputDTO toInput(DataPool entity) {
    return dataPoolMapper.toInput(entity);
  }
}

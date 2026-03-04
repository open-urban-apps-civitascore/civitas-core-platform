package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSourceAssembler implements BaseAssembler<DataSource, DataSourceOutputDTO, UUID> {

  private final DataSourceMapper dataSourceMapper;
  private final ConnectorHandlerRegistry connectorHandlerRegistry;
  private final DataSetRepository dataSetRepository;

  @Override
  public DataSourceOutputDTO mapToBaseDto(DataSource entity) {
    return dataSourceMapper.toOutput(entity);
  }

  @Override
  public DataSourceOutputDTO enrichDto(DataSourceOutputDTO dto, DataSource entity) {
    dto.setInUse(
        dataSetRepository.existsByPipelinesDataSourcesIdAndDataSetStatusIn(
            entity.getId(), List.of(DataSetStatus.READY, DataSetStatus.AVAILABLE)));
    return dto;
  }

  @Override
  public DataSourceOutputDTO postProcessOutput(DataSourceOutputDTO dto, DataSource entity) {
    if (dto.getConfiguration() != null && entity.getConnectorType() != null) {
      ConnectorHandler handler =
          connectorHandlerRegistry.getHandlerOrThrow(entity.getConnectorType());
      dto.setConfiguration(handler.prepareForOutput(dto.getConfiguration()));
    }
    return dto;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSource entity) {
    return (I) dataSourceMapper.toInput(entity);
  }
}

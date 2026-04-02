package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSource} entities to {@link DataSourceOutputDTO}. Participates
 * in the template method pattern defined by {@link BaseAssembler}.
 */
@Component
@RequiredArgsConstructor
public class DataSourceAssembler implements BaseAssembler<DataSource, DataSourceOutputDTO, UUID> {

  private final DataSourceMapper dataSourceMapper;
  private final ConnectorHandlerRegistry connectorHandlerRegistry;
  private final PipelineRepository pipelineRepository;

  /** {@inheritDoc} Delegates to the {@link DataSourceMapper} for basic field mapping. */
  @Override
  public DataSourceOutputDTO mapToBaseDto(DataSource entity) {
    return dataSourceMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag based on whether any READY or AVAILABLE dataset
   * references this data source.
   */
  @Override
  public DataSourceOutputDTO enrichDto(DataSourceOutputDTO dto, DataSource entity) {
    dto.setInUse(pipelineRepository.existsByDataSourcesId(entity.getId()));
    return dto;
  }

  /**
   * {@inheritDoc} Redacts sensitive connector configuration fields via the appropriate {@link
   * ConnectorHandler}.
   */
  @Override
  public DataSourceOutputDTO postProcessOutput(DataSourceOutputDTO dto, DataSource entity) {
    if (dto.getConfiguration() != null && entity.getConnectorType() != null) {
      ConnectorHandler handler =
          connectorHandlerRegistry.getHandlerOrThrow(entity.getConnectorType());
      dto.setConfiguration(handler.prepareForOutput(dto.getConfiguration()));
    }
    return dto;
  }

  /** {@inheritDoc} Converts a data source entity back to its input DTO for PATCH operations. */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSource entity) {
    return (I) dataSourceMapper.toInput(entity);
  }
}

package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.DatapoolScopeOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembler for converting {@link DataSource} entities to {@link DataSourceOutputDTO}. Participates
 * in the template method pattern defined by {@link BaseAssembler}. The connector configuration is
 * read back from the Model Forge registry via the source's {@code configurationUrn} pin.
 */
@Component
@RequiredArgsConstructor
public class DataSourceAssembler implements BaseAssembler<DataSource, DataSourceOutputDTO, UUID> {

  private final DataSourceMapper dataSourceMapper;
  private final ConnectorHandlerRegistry connectorHandlerRegistry;
  private final PipelineRepository pipelineRepository;
  private final ModelRegistryGateway modelRegistryGateway;

  /** {@inheritDoc} Delegates to the {@link DataSourceMapper} for basic field mapping. */
  @Override
  public DataSourceOutputDTO mapToBaseDto(DataSource entity) {
    return dataSourceMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} flag, populates the {@code datapoolScope} from the
   * entity's scope type and associated Datapools, and serves the configuration from the registry
   * pin.
   */
  @Override
  public DataSourceOutputDTO enrichDto(DataSourceOutputDTO dto, DataSource entity) {
    dto.setInUse(pipelineRepository.existsByDataSourcesId(entity.getId()));
    dto.setDatapoolScope(buildDatapoolScopeOutput(entity));
    dto.setConfiguration(fetchConfiguration(entity));
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

  /**
   * {@inheritDoc} Converts a data source entity back to its input DTO for PATCH operations. The
   * configuration carries the raw (encrypted) registry document, as it previously carried the raw
   * entity column.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSource entity) {
    DataSourceInputDTO dto = dataSourceMapper.toInput(entity);
    dto.setDatapoolScope(buildDatapoolScopeInput(entity));
    dto.setConfiguration(fetchConfiguration(entity));
    return (I) dto;
  }

  /**
   * Reads the source's configuration document back from the registry; {@code null} when no
   * configuration is stored.
   */
  private Map<String, Object> fetchConfiguration(DataSource entity) {
    if (entity.getConfigurationUrn() == null) {
      return null;
    }
    return modelRegistryGateway
        .fetchPayload(entity.getConfigurationUrn())
        .map(ModelRegistryGateway.RegistryDocument::content)
        .orElse(null);
  }

  private DatapoolScopeOutputDTO buildDatapoolScopeOutput(DataSource entity) {
    DatapoolScopeOutputDTO scope = new DatapoolScopeOutputDTO();
    scope.setType(entity.getDatapoolScopeType());
    if (entity.getDatapoolScopeType() == DatapoolScopeType.SPECIFIC) {
      scope.setDatapoolIds(entity.getScopedDataPools().stream().map(DataPool::getId).toList());
    } else {
      scope.setDatapoolIds(List.of());
    }
    return scope;
  }

  private DatapoolScopeInputDTO buildDatapoolScopeInput(DataSource entity) {
    DatapoolScopeInputDTO scope = new DatapoolScopeInputDTO();
    scope.setType(entity.getDatapoolScopeType());
    if (entity.getDatapoolScopeType() == DatapoolScopeType.SPECIFIC) {
      List<UUID> ids = entity.getScopedDataPools().stream().map(DataPool::getId).toList();
      scope.setDatapoolIds(ids);
    } else {
      scope.setDatapoolIds(List.of());
    }
    return scope;
  }
}

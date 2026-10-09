package de.civitascore.portal.model.output.assembler;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.connector.ConnectorJson;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.model.output.DataSourceOutputDTO;
import de.civitascore.portal.model.output.DatapoolScopeOutputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.service.ArtifactUsageLookup;
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
  private final ArtifactUsageLookup artifactUsageLookup;
  private final ModelRegistryGateway modelRegistryGateway;

  /** {@inheritDoc} Delegates to the {@link DataSourceMapper} for basic field mapping. */
  @Override
  public DataSourceOutputDTO mapToBaseDto(DataSource entity) {
    return dataSourceMapper.toOutput(entity);
  }

  /**
   * {@inheritDoc} Sets the {@code inUse} and {@code inUseByReleased} flags, populates the {@code
   * datapoolScope} from the entity's scope type and associated Datapools, and serves the connector
   * from the registry pin.
   */
  @Override
  public DataSourceOutputDTO enrichDto(DataSourceOutputDTO dto, DataSource entity) {
    ArtifactUsageLookup.ArtifactUsage usage = artifactUsageLookup.of(entity);
    dto.setInUse(usage.inUse());
    dto.setInUseByReleased(usage.inUseByReleased());
    dto.setDatapoolScope(buildDatapoolScopeOutput(entity));
    dto.setConnector(fetchConnector(entity));
    dto.setConfigurationUrn(entity.getConfigurationUrn());
    return dto;
  }

  /**
   * {@inheritDoc} Converts a data source entity back to its input DTO for PATCH operations. The
   * connector is left out: the controller derives it from the patch and the stored connector.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <I> I toInput(DataSource entity) {
    DataSourceInputDTO dto = dataSourceMapper.toInput(entity);
    dto.setDatapoolScope(buildDatapoolScopeInput(entity));
    return (I) dto;
  }

  /**
   * The connector of the source's connector type, read from its registry document. Without a stored
   * document every field of the connector is empty.
   */
  private ConnectorConfiguration fetchConnector(DataSource entity) {
    if (entity.getConnectorType() == null) {
      return null;
    }
    Map<String, Object> document =
        entity.getConfigurationUrn() == null
            ? null
            : modelRegistryGateway
                .fetchPayload(entity.getConfigurationUrn())
                .map(ModelRegistryGateway.RegistryDocument::content)
                .orElse(null);
    return ConnectorJson.readStored(
        entity.getConnectorType(), document == null ? Map.of() : document);
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

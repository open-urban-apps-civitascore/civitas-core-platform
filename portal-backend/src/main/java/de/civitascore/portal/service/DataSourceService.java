package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.connector.ConnectorConfiguration;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
import de.civitascore.portal.model.input.DatapoolScopeInputDTO;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.AssignmentChangeAuthorizer;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.service.connector.ConnectorValidator;
import de.civitascore.portal.service.connector.ConnectorViolation;
import de.civitascore.portal.service.connector.DataSourceConnectorStore;
import de.civitascore.portal.service.validation.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.util.ConnectorValidationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Service for managing {@link DataSource} entities through their full lifecycle (DRAFT to
 * AVAILABLE). Handles the connector, data structure version linking, and release/unrelease status
 * transitions. {@link DataSourceConnectorStore} keeps the connector and {@link ConnectorValidator}
 * validates it.
 */
@Service
@RequiredArgsConstructor
public class DataSourceService
    extends BaseDataEntityService<DataSource, DataSourceInputDTO, DataSourceMetaInputDTO> {

  private final DataSourceRepository dataSourceRepository;
  private final DataSourceMapper dataSourceMapper;
  private final AssignmentFactory assignmentFactory;
  private final AssignmentChangeAuthorizer assignmentChangeAuthorizer;
  private final DataStructureVersionService dataStructureVersionService;
  private final PipelineRepository pipelineRepository;
  private final DataPoolRepository dataPoolRepository;
  private final DataSourceConnectorStore connectorStore;
  private final ConnectorValidator connectorValidator;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;
  private final DataSourceDatapoolScopeValidator datapoolScopeValidator;
  private final ArtifactUsageLookup artifactUsageLookup;

  @Override
  protected DataSourceRepository getRepository() {
    return dataSourceRepository;
  }

  @Override
  protected DataSourceMapper getMapper() {
    return dataSourceMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSource.class.getSimpleName();
  }

  @Override
  protected AssignmentFactory getAssignmentFactory() {
    return assignmentFactory;
  }

  @Override
  protected AssignmentChangeAuthorizer getAssignmentChangeAuthorizer() {
    return assignmentChangeAuthorizer;
  }

  @Override
  protected ReleasableStatus getEntityStatus(DataSource entity) {
    return entity.getDataSourceStatus();
  }

  @Override
  protected void setEntityStatus(DataSource entity, ReleasableStatus status) {
    entity.setDataSourceStatus((DataSourceStatus) status);
  }

  @Override
  protected ReleasableStatus getDraftStatus() {
    return DataSourceStatus.DRAFT;
  }

  @Override
  protected ReleasableStatus getAvailableStatus() {
    return DataSourceStatus.AVAILABLE;
  }

  /**
   * Restricts the generic CRUD update to DRAFT data sources. A released (AVAILABLE) data source may
   * only be changed through {@link #updateReleasedMeta(UUID, DataSourceMetaInputDTO)}.
   *
   * @param id the data source ID
   * @param input the update input
   * @return the updated data source
   * @throws InvalidInputException if the data source is not in DRAFT status
   */
  @Override
  @Transactional
  public DataSource update(UUID id, DataSourceInputDTO input) {
    DataSource existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSourceStatus() != DataSourceStatus.DRAFT) {
      throw new InvalidInputException(
          getEntityName(),
          id,
          "DataSource can only be updated in DRAFT status, current status: "
              + existingEntity.getDataSourceStatus()
              + ". Use the released metadata endpoint instead.");
    }
    return super.update(id, input);
  }

  /** Links the authorized data structure version after DTO-to-entity conversion. */
  @Override
  protected DataSource postConvertToEntity(DataSource entity, DataSourceInputDTO input) {
    if (input.getDataStructureVersionId() != null) {
      entity.setDataStructureVersion(resolveAuthorizedVersion(input.getDataStructureVersionId()));
    } else {
      entity.setDataStructureVersion(null);
    }
    if (input.getDatapoolScope() != null) {
      applyDatapoolScope(entity, input.getDatapoolScope());
    }

    if (entity.getId() != null) {
      connectorStore.write(entity, input.getConnector());
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSource postCreate(DataSource saved, DataSourceInputDTO input) {
    connectorStore.write(saved, input.getConnector());
    return super.postCreate(saved, input);
  }

  @Override
  protected DataSourceInputDTO preProcessCreateInput(DataSourceInputDTO input) {
    requireValidConnector(input.getConnector(), ConnectorValidator.Stage.DRAFT);
    return super.preProcessCreateInput(input);
  }

  @Override
  protected DataSourceInputDTO preProcessUpdateInput(
      DataSourceInputDTO input, DataSource existingEntity) {
    requireValidConnector(input.getConnector(), ConnectorValidator.Stage.DRAFT);
    validateConnectorTypeChange(input, existingEntity);
    validateDataStructureVersionChange(input, existingEntity);
    return input;
  }

  /**
   * The connector after a JSON merge patch of the stored connector.
   *
   * @param existing the data source before the patch
   * @param connectorPatch the {@code connector} member of the patch, or a missing node
   * @return the patched connector, or null if the data source has none afterwards
   * @throws de.civitascore.portal.model.connector.InvalidConnectorException if the patched
   *     connector cannot be read
   */
  public ConnectorConfiguration patchConnector(DataSource existing, JsonNode connectorPatch) {
    return connectorStore.patch(existing, connectorPatch);
  }

  private void validateConnectorTypeChange(DataSourceInputDTO input, DataSource existingEntity) {
    if (existingEntity.getDataSourceStatus() == DataSourceStatus.AVAILABLE
        && input.getConnector() != null
        && existingEntity.getConnectorType() != input.getConnector().connectorType()) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot change connector type of an AVAILABLE data source");
    }
  }

  /**
   * Validates the connector configuration before saving if the data source is in AVAILABLE status.
   *
   * @param entity the data source entity to validate
   * @return the validated entity
   * @throws InvalidInputException if the configuration is invalid for an AVAILABLE data source
   */
  @Override
  protected DataSource preSave(DataSource entity) {
    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      validateConfiguration(entity);
    }
    return entity;
  }

  @Override
  protected void validateRelease(DataSource entity) {
    if (entity.getConnectorType() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Connector type must be set before releasing");
    }

    if (entity.getDataStructureVersion() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Data structure version must be set before releasing");
    }

    validateDataStructureVersionAvailable(entity.getDataStructureVersion());
    validateConfiguration(entity);
  }

  @Override
  protected void validateUnrelease(DataSource entity) {
    artifactUsageLookup.of(entity).requireNoReleasedReferrer(getEntityName(), entity.getId());
  }

  private void validateNotInUse(UUID id) {
    if (pipelineRepository.existsByDataSourcesId(id)) {
      throw new ResourceInUseException(
          getEntityName(), id, "Cannot delete DataSource because it is referenced by a Pipeline.");
    }
  }

  /**
   * Updates the metadata of an AVAILABLE data source: name, description, assignments and datapool
   * scope.
   *
   * @param id the data source ID
   * @param input the metadata
   * @return the updated data source
   * @throws InvalidInputException if the data source is not AVAILABLE
   */
  @Override
  @Transactional
  public DataSource updateReleasedMeta(UUID id, DataSourceMetaInputDTO input) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Only data sources in AVAILABLE status can have metadata updated");
    }

    entity.setName(input.getName());
    entity.setDescription(input.getDescription());
    replaceAssignments(entity, input.getAssignments());
    if (input.getDatapoolScope() != null) {
      applyDatapoolScope(entity, input.getDatapoolScope());
    }

    return save(entity);
  }

  @Override
  public DataSourceMetaInputDTO toMetaInput(DataSource entity) {
    return dataSourceMapper.toMetaInput(entity);
  }

  /**
   * Re-asserts the DataSource→DataPool scope rule after this DataSource's own scope was set, for
   * every dataset it already feeds; a data source without an ID feeds none yet. Narrowing a bound
   * DataSource (e.g. {@code ALL → SPECIFIC} excluding a pool it is linked into, or {@code → NONE})
   * would otherwise reach the same persisted state the pipeline-write validation rejects, without
   * any path re-checking it. Each referencing pipeline is validated against its own dataset's
   * datapool; the managed DataSource already carries the new scope.
   *
   * @param dataSource the DataSource whose scope has just been changed
   * @throws de.civitascore.portal.util.DataSourceScopeViolationException if it is now out of scope
   *     for any dataset it feeds
   */
  private void revalidateLinkedDatasetsAgainstNewScope(DataSource dataSource) {
    if (dataSource.getId() == null) {
      return;
    }
    List<DataSource> sources = List.of(dataSource);
    pipelineRepository
        .findByDataSourcesId(dataSource.getId())
        .forEach(
            pipeline ->
                datapoolScopeValidator.validate(sources, pipeline.getDataSet().getDataPool()));
  }

  /**
   * Prevents deletion of data sources in AVAILABLE status. The data source must be unreleased
   * first.
   *
   * @param id the data source ID
   * @return the data source entity to be deleted
   * @throws InvalidInputException if the data source is in AVAILABLE status
   */
  @Override
  protected DataSource preProcessDelete(UUID id) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Cannot delete a released data source. Unrelease it first.");
    }
    // A draft may be referenced too, and postDelete drops the Model Forge artifact before the
    // foreign key aborts the commit, which would leave the surviving row pinned to a dead URN.
    validateNotInUse(id);

    return entity;
  }

  /**
   * After the data source row is deleted, delete the backing configuration artifact from Model
   * Forge in the same transaction. No-op when no configuration was ever stored.
   *
   * @param entity the deleted data source
   */
  @Override
  protected void postDelete(DataSource entity) {
    if (entity != null) {
      connectorStore.delete(entity);
    }
  }

  private void applyDatapoolScope(DataSource entity, DatapoolScopeInputDTO scope) {
    requireScopeTypePresent(scope, entity.getId());
    entity.setDatapoolScopeType(scope.getType());
    entity.getScopedDataPools().clear();
    if (scope.getType() == DatapoolScopeType.SPECIFIC) {
      entity.getScopedDataPools().addAll(resolveSpecificDatapools(scope));
    }
    revalidateLinkedDatasetsAgainstNewScope(entity);
  }

  private void requireScopeTypePresent(DatapoolScopeInputDTO scope, UUID entityId) {
    if (scope.getType() == null) {
      throw new InvalidInputException(
          getEntityName(), entityId, "datapoolScope.type must not be null");
    }
  }

  private List<DataPool> resolveSpecificDatapools(DatapoolScopeInputDTO scope) {
    requireDatapoolIdsNotEmpty(scope);
    // The datapool ids reference a DATAPOOL-scoped entity, which the DATASOURCE-typed route header
    // cannot cover — authorize the caller against them before binding.
    scopeAccessAuthorizer.authorizeReferences(ScopeType.DATAPOOL, scope.getDatapoolIds());
    List<DataPool> resolvedPools = dataPoolRepository.findAllById(scope.getDatapoolIds());
    validateAllDatapoolsFound(scope.getDatapoolIds(), resolvedPools);
    return resolvedPools;
  }

  private void requireDatapoolIdsNotEmpty(DatapoolScopeInputDTO scope) {
    if (scope.getDatapoolIds() == null || scope.getDatapoolIds().isEmpty()) {
      throw new InvalidInputException(
          getEntityName(),
          (UUID) null,
          "At least one datapoolId is required for scope type SPECIFIC");
    }
  }

  private void validateAllDatapoolsFound(List<UUID> requestedIds, List<DataPool> foundPools) {
    if (foundPools.size() != requestedIds.size()) {
      Set<UUID> foundIds = foundPools.stream().map(DataPool::getId).collect(Collectors.toSet());
      List<UUID> missingIds = requestedIds.stream().filter(id -> !foundIds.contains(id)).toList();
      throw new ResourceNotFoundException(DataPool.class.getSimpleName(), missingIds);
    }
  }

  private void validateConfiguration(DataSource entity) {
    if (entity.getConnectorType() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Connector type is required for validation");
    }
    if (entity.getConfigurationUrn() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Configuration is required for releasing");
    }

    requireValidConnector(connectorStore.read(entity), ConnectorValidator.Stage.RELEASE);
  }

  private void requireValidConnector(
      ConnectorConfiguration connector, ConnectorValidator.Stage stage) {
    List<ConnectorViolation> violations = connectorValidator.validate(connector, stage);
    if (!violations.isEmpty()) {
      throw new ConnectorValidationException(violations);
    }
  }

  /**
   * Resolves a data structure version by ID, authorizing the caller against its parent data
   * structure. The version id in the request body references a DATASTRUCTURE-scoped entity, which
   * the DATASOURCE-typed route header cannot cover; assignments scope on the parent structure, so
   * authorization targets {@code dataStructure.id}, not the version id.
   */
  private DataStructureVersion resolveAuthorizedVersion(UUID versionId) {
    DataStructureVersion dsv = dataStructureVersionService.findByIdOrThrow(versionId);
    // A missing version (findByIdOrThrow → 404) and an unauthorized one must be indistinguishable
    // from the outside, otherwise the 404-vs-403 difference is an existence oracle over version
    // ids. Map the denial onto the same not-found response; ScopeAccessAuthorizer has already
    // logged the real authorization denial server-side for audit.
    try {
      scopeAccessAuthorizer.authorizeReferences(
          ScopeType.DATASTRUCTURE, Set.of(dsv.getDataStructure().getId()));
    } catch (AccessDeniedException e) {
      throw new ResourceNotFoundException(getEntityName(), versionId);
    }
    return dsv;
  }

  private void validateDataStructureVersionAvailable(DataStructureVersion dsv) {
    if (dsv.getDataStructureVersionStatus() != DataStructureVersionStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(),
          dsv.getId(),
          "DataStructureVersion must be in AVAILABLE status for an AVAILABLE DataSource");
    }
    if (dsv.getDataStructure().getDataStructureStatus() != DataStructureStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(),
          dsv.getId(),
          "The parent DataStructure must be in AVAILABLE status for an AVAILABLE DataSource");
    }
  }

  private void validateDataStructureVersionChange(
      DataSourceInputDTO input, DataSource existingEntity) {
    if (existingEntity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      return;
    }
    UUID existingDsvId =
        existingEntity.getDataStructureVersion() != null
            ? existingEntity.getDataStructureVersion().getId()
            : null;
    if (input.getDataStructureVersionId() != null
        && !input.getDataStructureVersionId().equals(existingDsvId)) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot change data structure version of an AVAILABLE data source");
    }
    if (input.getDataStructureVersionId() == null && existingDsvId != null) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot remove data structure version from an AVAILABLE data source");
    }
  }
}

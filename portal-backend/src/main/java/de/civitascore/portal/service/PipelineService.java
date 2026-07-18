package de.civitascore.portal.service;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DatapoolScopeType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Pipeline} entities, which define data processing pipelines belonging
 * to a {@link DataSet}. Handles dataset and datasource relationship resolution, unique name
 * validation within a dataset, and version incrementing on updates.
 */
@Slf4j
@Service
public class PipelineService extends BaseService<Pipeline, PipelineInputDTO> {

  private final PipelineRepository pipelineRepository;
  private final PipelineMapper pipelineMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSourceRepository dataSourceRepository;
  private final DataSinkService dataSinkService;
  private final DataSinkRepository dataSinkRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;

  public PipelineService(
      PipelineRepository pipelineRepository,
      PipelineMapper pipelineMapper,
      DataSetRepository dataSetRepository,
      DataSourceRepository dataSourceRepository,
      DataSinkService dataSinkService,
      DataSinkRepository dataSinkRepository,
      ModelRegistryGateway modelRegistryGateway,
      ScopeAccessAuthorizer scopeAccessAuthorizer) {
    this.pipelineRepository = pipelineRepository;
    this.pipelineMapper = pipelineMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataSourceRepository = dataSourceRepository;
    this.dataSinkService = dataSinkService;
    this.dataSinkRepository = dataSinkRepository;
    this.modelRegistryGateway = modelRegistryGateway;
    this.scopeAccessAuthorizer = scopeAccessAuthorizer;
  }

  @Override
  protected PipelineRepository getRepository() {
    return pipelineRepository;
  }

  @Override
  protected PipelineMapper getMapper() {
    return pipelineMapper;
  }

  @Override
  protected String getEntityName() {
    return Pipeline.class.getSimpleName();
  }

  /**
   * Finds a pipeline by ID and verifies that it belongs to the specified dataset.
   *
   * @param id the pipeline ID
   * @param dataSetId the expected parent dataset ID
   * @return the pipeline entity
   * @throws ResourceNotFoundException if the pipeline does not exist or does not belong to the
   *     given dataset
   */
  public Pipeline findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    Pipeline pipeline = findByIdOrThrow(id);
    if (!dataSetId.equals(pipeline.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return pipeline;
  }

  /**
   * Validates that an update does not attempt to move a pipeline to a different dataset.
   *
   * @param input the pipeline update input
   * @param existingEntity the current pipeline entity
   * @return the validated input
   * @throws ResourceNotFoundException if the input specifies a different dataset ID
   */
  @Override
  protected PipelineInputDTO preProcessUpdateInput(
      PipelineInputDTO input, Pipeline existingEntity) {
    if (input.getDataSetId() != null
        && existingEntity.getDataSet() != null
        && !input.getDataSetId().equals(existingEntity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), existingEntity.getId());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Resolves the parent dataset and data source references after DTO-to-entity conversion.
   * Validates that the dataset and all referenced data sources exist.
   *
   * @param entity the pipeline entity
   * @param input the pipeline input DTO
   * @return the entity with resolved dataset and data source relationships
   * @throws ResourceNotFoundException if the dataset is not found
   * @throws InvalidInputException if any data source ID is not found
   */
  @Override
  protected Pipeline postConvertToEntity(Pipeline entity, PipelineInputDTO input) {
    Optional.ofNullable(input.getDataSetId())
        .flatMap(dataSetRepository::findById)
        .ifPresentOrElse(
            entity::setDataSet,
            () -> {
              throw new ResourceNotFoundException(
                  DataSet.class.getSimpleName(), input.getDataSetId());
            });

    if (input.getDataSourceIds() != null && !input.getDataSourceIds().isEmpty()) {
      // Authorize before revealing existence: a "not found" answer for an unauthorized caller
      // would otherwise turn this endpoint into an existence oracle over the DataSource table.
      scopeAccessAuthorizer.authorizeReferences(ScopeType.DATASOURCE, input.getDataSourceIds());
      List<DataSource> dataSources = dataSourceRepository.findAllById(input.getDataSourceIds());
      if (dataSources.size() != input.getDataSourceIds().size()) {
        throw new InvalidInputException(
            "Pipeline", "dataSourceIds", "One or more DataSource IDs not found");
      }
      dataSources.forEach(this::validateDataSourceLinkable);
      validateDataSourcesInScope(dataSources, entity.getDataSet());
      entity.setDataSources(new HashSet<>(dataSources));
    } else {
      entity.setDataSources(null);
    }

    storeDefinitionInRegistry(entity, input);

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Stores the pipeline definition (editor-built graph plus the React Flow layout merged in as
   * {@code x-ui-styles}) in the Model Forge registry and mirrors the assigned pin onto the shell.
   * When the input carries neither model nor styles, the content pin is cleared (a full update may
   * clear the definition); the logical URN is kept so a later store versions the same artifact.
   *
   * @param entity the pipeline entity
   * @param input the input DTO carrying model and styles
   */
  private void storeDefinitionInRegistry(Pipeline entity, PipelineInputDTO input) {
    boolean hasModel = input.getModel() != null && !input.getModel().isEmpty();
    boolean hasStyles = input.getStyles() != null && !input.getStyles().isEmpty();
    if (!hasModel && !hasStyles) {
      entity.setModelUrn(null);
      return;
    }
    if (entity.getModelUrn() != null
        && modelRegistryGateway.isUnchanged(
            entity.getModelUrn(), input.getModel(), input.getStyles())) {
      // Unchanged content keeps the existing pin — a metadata-only update or PATCH round-trip
      // must not mint a new registry version.
      return;
    }
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.PIPELINE,
            Optional.ofNullable(entity.getModelLogicalUrn()),
            entity.getName(),
            input.getModel(),
            input.getStyles());
    if (entity.getModelLogicalUrn() == null) {
      entity.setModelLogicalUrn(pin.logicalUrn());
    }
    entity.setModelUrn(pin.versionedUrn());
  }

  /**
   * Validates pipeline name uniqueness within the parent dataset and increments the version number
   * on updates.
   *
   * @param entity the pipeline entity to validate
   * @return the entity with version incremented (for updates)
   * @throws UniqueConstraintViolationException if another pipeline with the same name exists in the
   *     same dataset
   */
  @Override
  protected Pipeline preSave(Pipeline entity) {
    validateUniqueName(entity);
    if (entity.getId() != null) {
      entity.setVersion(entity.getVersion() + 1);
    }
    return super.preSave(entity);
  }

  /**
   * Rewires the {@code pipeline_id} FK on the DataSinks listed in {@code input.dataSinkIds}: any
   * DataSink previously linked to this pipeline that is no longer in the list is detached; any
   * DataSink in the list that is not yet linked to this pipeline is attached. Cross-dataset and
   * cross-pipeline references are rejected.
   */
  @Override
  protected Pipeline postSave(Pipeline saved, PipelineInputDTO input) {
    Set<UUID> requestedIds =
        input.getDataSinkIds() != null ? new HashSet<>(input.getDataSinkIds()) : Set.of();
    UUID pipelineId = saved.getId();
    UUID dataSetId = saved.getDataSet().getId();

    if (!requestedIds.isEmpty()) {
      List<DataSink> requested = dataSinkRepository.findAllById(requestedIds);
      if (requested.size() != requestedIds.size()) {
        throw new InvalidInputException(
            "Pipeline", "dataSinkIds", "One or more DataSink IDs not found");
      }
      for (DataSink sink : requested) {
        if (!dataSetId.equals(sink.getDataSet().getId())) {
          throw new InvalidInputException(
              "Pipeline",
              "dataSinkIds",
              "DataSink " + sink.getId() + " belongs to a different DataSet");
        }
        if (sink.getPipeline() != null && !pipelineId.equals(sink.getPipeline().getId())) {
          throw new InvalidInputException(
              "Pipeline",
              "dataSinkIds",
              "DataSink " + sink.getId() + " is already attached to another Pipeline");
        }
      }
      for (DataSink sink : requested) {
        if (sink.getPipeline() == null) {
          sink.setPipeline(saved);
          dataSinkRepository.save(sink);
        }
      }
    }

    dataSinkRepository.findByPipelineId(pipelineId).stream()
        .filter(sink -> !requestedIds.contains(sink.getId()))
        .forEach(
            sink -> {
              sink.setPipeline(null);
              dataSinkRepository.save(sink);
            });

    return saved;
  }

  private void validateDataSourcesInScope(List<DataSource> dataSources, DataSet dataSet) {
    List<DataSource> offendingDataSources =
        new ArrayList<>(
            dataSources.stream()
                .filter(ds -> ds.getDatapoolScopeType() == DatapoolScopeType.NONE)
                .toList());

    DataPool dataPool = dataSet.getDataPool();
    if (dataPool != null) {
      offendingDataSources.addAll(
          dataSources.stream().filter(ds -> !isPermittedForDataPool(ds, dataPool)).toList());
    } else {
      // A pool-less dataset belongs to no datapool, so a SPECIFIC-scoped datasource (confined to
      // its
      // scopedDataPools) must NOT be usable here — otherwise its pool-confined data could be routed
      // into a pool-less (and possibly openDataAccess=public) dataset, defeating the restriction.
      offendingDataSources.addAll(
          dataSources.stream()
              .filter(ds -> ds.getDatapoolScopeType() == DatapoolScopeType.SPECIFIC)
              .toList());
    }

    if (!offendingDataSources.isEmpty()) {
      throw new DataSourceScopeViolationException(
          offendingDataSources.stream().map(DataSource::getId).collect(Collectors.toList()));
    }
  }

  private boolean isPermittedForDataPool(DataSource dataSource, DataPool dataPool) {
    if (dataSource.getDatapoolScopeType() != DatapoolScopeType.SPECIFIC) {
      return true;
    }
    return dataSource.getScopedDataPools().stream()
        .anyMatch(scopedPool -> scopedPool.getId().equals(dataPool.getId()));
  }

  private void validateDataSourceLinkable(DataSource dataSource) {
    if (dataSource.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(),
          dataSource.getId(),
          "DataSource must be in AVAILABLE status to be linked to a Pipeline");
    }
  }

  private void validateUniqueName(Pipeline entity) {
    pipelineRepository
        .findAllByNameAndDataSetId(entity.getName(), entity.getDataSet().getId())
        .stream()
        .filter(result -> !result.getId().equals(entity.getId()))
        .findFirst()
        .ifPresent(
            _ -> {
              throw new UniqueConstraintViolationException(
                  Pipeline.class.getSimpleName(),
                  "name",
                  entity.getName(),
                  "datasetId",
                  entity.getDataSet().getId().toString());
            });
  }

  /**
   * Prevents deletion of pipelines that belong to a non-DRAFT dataset. Detaches associated
   * DataSinks (they survive the pipeline deletion).
   *
   * @param id the pipeline ID to delete
   * @return the pipeline entity to be deleted
   * @throws ResourceNotFoundException if the pipeline does not exist
   * @throws InvalidInputException if the parent dataset is not in DRAFT status
   */
  @Override
  protected Pipeline preProcessDelete(UUID id) {
    Pipeline pipeline =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (pipeline.getDataSet() != null
        && pipeline.getDataSet().getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException(
          "Pipeline",
          id,
          "Cannot delete pipeline associated with a dataset that is not in DRAFT status.");
    }

    dataSinkService.unlinkByPipelineId(id);

    return pipeline;
  }

  /**
   * After the pipeline row is deleted, delete the backing definition artifact from Model Forge in
   * the same transaction. No-op when no definition was ever stored. (Datasets cascade-delete their
   * pipelines via JPA without this hook; the orphaned registry artifacts are harmless append-only
   * history — see concept 6.7.)
   *
   * @param entity the deleted pipeline
   */
  @Override
  protected void postDelete(Pipeline entity) {
    if (entity != null && entity.getModelLogicalUrn() != null) {
      modelRegistryGateway.deletePayload(entity.getModelLogicalUrn());
    }
  }
}

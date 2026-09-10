package de.civitascore.portal.service;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSourceStatus;
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
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.security.dto.PrincipalUserDetails;
import de.civitascore.portal.util.DataSourceScopeViolationException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Pipeline} entities, which define data processing pipelines belonging
 * to a {@link DataSet}. Handles dataset and datasource relationship resolution, unique name
 * validation within a dataset, and version incrementing on updates.
 */
@Slf4j
@Service
public class PipelineService extends DataSetOwnedService<Pipeline, PipelineInputDTO> {

  private final PipelineRepository pipelineRepository;
  private final PipelineMapper pipelineMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSourceRepository dataSourceRepository;
  private final DataSinkService dataSinkService;
  private final DataSinkRepository dataSinkRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final DataSourceDatapoolScopeValidator datapoolScopeValidator;
  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  public PipelineService(
      PipelineRepository pipelineRepository,
      PipelineMapper pipelineMapper,
      DataSetRepository dataSetRepository,
      DataSourceRepository dataSourceRepository,
      DataSinkService dataSinkService,
      DataSinkRepository dataSinkRepository,
      ModelRegistryGateway modelRegistryGateway,
      DataSourceDatapoolScopeValidator datapoolScopeValidator,
      ObjectProvider<AllowedScopes> allowedScopesProvider,
      DataSetMutationGuard dataSetMutationGuard) {
    super(dataSetMutationGuard);
    this.pipelineRepository = pipelineRepository;
    this.pipelineMapper = pipelineMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataSourceRepository = dataSourceRepository;
    this.dataSinkService = dataSinkService;
    this.dataSinkRepository = dataSinkRepository;
    this.modelRegistryGateway = modelRegistryGateway;
    this.datapoolScopeValidator = datapoolScopeValidator;
    this.allowedScopesProvider = allowedScopesProvider;
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
   * Resolves the parent dataset and data source references after DTO-to-entity conversion.
   *
   * @param entity the pipeline entity
   * @param input the pipeline input DTO
   * @return the entity with resolved dataset and data source relationships
   * @throws ResourceNotFoundException if the dataset is not found
   * @throws org.springframework.security.access.AccessDeniedException if no scope header is present
   * @throws DataSourceScopeViolationException if a referenced data source is not usable by this
   *     dataset's pipelines — it does not exist, is not AVAILABLE, or is not released for the
   *     dataset's datapool. The three cases are deliberately indistinguishable.
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
      // Referencing a DataSource is authorized by the Use relationship, not by DATASOURCE_READ: the
      // dataset's datapool decides, and OPA has already enforced the caller's permission on the
      // dataset. The scope header must still be present — its absence means the request reached the
      // backend without passing OPA at all.
      requireScopeHeader();
      List<DataSource> dataSources = dataSourceRepository.findAllById(input.getDataSourceIds());
      resolveUsableReferences(entity.getDataSet(), input.getDataSourceIds(), dataSources);
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
    // A PIPELINE artifact's content IS the CORE model; the React Flow layout only decorates it as
    // x-ui-styles. Without a model there is nothing to store or validate — a draft may still carry
    // layout but no graph yet — so clear the content pin rather than shipping a null/empty model to
    // Model Forge, which would reject it against pipeline.schema.json.
    if (!hasModel) {
      entity.setModelUrn(null);
      return;
    }
    if (entity.getModelUrn() != null
        && modelRegistryGateway.isUnchanged(
            entity.getModelUrn(), input.getModel(), input.getStyles())) {
      // Unchanged content keeps the existing pin — a metadata-only update or PATCH round-trip must
      // not mint a new registry version.
      return;
    }
    // Pass-through: the FRONTEND delivers a clean, schema-valid CORE Pipeline document (nodes keyed
    // by
    // `kind` with sourceRef/sinkRef/mappingRef CORE URNs). The backend is a thin shell — it stores
    // the
    // model verbatim (Model Forge validates it against pipeline.schema.json and stamps $schema +
    // id) and
    // never parses the pipeline's contents. The ReactFlow editor layout rides along as x-ui-styles.
    // Link the pipeline into its DataSet's manifest (Model Forge adds it and its whole reference
    // closure — sources/sinks/mappings/structures — as dataset-ref members). The pipeline belongs
    // to
    // exactly one DataSet; its manifest is created when the dataset is created.
    String dataSetUrn =
        entity.getDataSet() != null ? entity.getDataSet().getManifestLogicalUrn() : null;
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.PIPELINE,
            Optional.ofNullable(entity.getModelLogicalUrn()),
            entity.getName(),
            input.getModel(),
            input.getStyles(),
            dataSetUrn);
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

  /**
   * Runs the datapool-confinement check and records who established the relationship.
   *
   * <p>Establishing a Use relationship no longer passes through {@code ScopeAccessAuthorizer},
   * which used to log every grant and denial with the acting user. This is now the only place the
   * decision is attributable, so both outcomes are logged here rather than left to the exception
   * handler.
   */
  private void resolveUsableReferences(
      DataSet dataSet, Collection<UUID> requestedIds, List<DataSource> found) {
    Map<UUID, DataSource> byId =
        found.stream().collect(Collectors.toMap(DataSource::getId, Function.identity()));
    List<UUID> notUsable =
        requestedIds.stream().distinct().filter(id -> !isUsable(byId.get(id), dataSet)).toList();

    UUID dataPoolId = dataSet.getDataPool() == null ? null : dataSet.getDataPool().getId();
    if (!notUsable.isEmpty()) {
      log.warn(
          "DataSource reference denied for user {} on dataset {} (datapool {}): requested={} notUsable={}",
          Encode.forJava(actingUserId()),
          dataSet.getId(),
          dataPoolId,
          requestedIds,
          notUsable);
      throw DataSourceScopeViolationException.notUsableInPipeline(notUsable);
    }
    log.info(
        "DataSource reference granted for user {} on dataset {} (datapool {}): {}",
        Encode.forJava(actingUserId()),
        dataSet.getId(),
        dataPoolId,
        requestedIds);
  }

  /**
   * Whether a referenced DataSource may feed this dataset's pipelines: it must exist, be AVAILABLE,
   * and be released for the dataset's datapool. A nonexistent id ({@code null} here) is treated the
   * same as an unusable one so the three cases stay indistinguishable to the caller.
   */
  private boolean isUsable(DataSource dataSource, DataSet dataSet) {
    return dataSource != null
        && dataSource.getDataSourceStatus() == DataSourceStatus.AVAILABLE
        && datapoolScopeValidator.isPermitted(dataSource, dataSet.getDataPool());
  }

  /**
   * Denies when no scope header is present, i.e. the request reached the backend without passing
   * APISIX/OPA. Defence in depth: the datapool rule below is not an authorization check on the
   * caller, so this is the only caller-facing guard left on this path.
   */
  private void requireScopeHeader() {
    if (!allowedScopesProvider.getObject().isHeaderPresent()) {
      log.warn(
          "DataSource reference denied for user {}: no scope header present (direct backend access)",
          Encode.forJava(actingUserId()));
      throw new AccessDeniedException("Missing scope information for DataSource reference");
    }
  }

  /**
   * The acting user's id for the audit lines, or {@code unknown} when no principal is resolvable.
   */
  private String actingUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof PrincipalUserDetails principal
        && principal.getUserId() != null) {
      return principal.getUserId().toString();
    }
    return "unknown";
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

  /** Detaches the pipeline's DataSinks, which survive the deletion. */
  @Override
  protected void onDelete(Pipeline pipeline) {
    dataSinkService.unlinkByPipelineId(pipeline.getId());
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

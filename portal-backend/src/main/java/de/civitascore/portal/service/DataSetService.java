package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.ApiStandard;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.security.DataSourceDatapoolScopeValidator;
import de.civitascore.portal.util.DataSetNotEditableException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.SagaInFlightException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataSet} entities through their full lifecycle: DRAFT, READY, and
 * AVAILABLE. Orchestrates staging (DRAFT → READY: validating pipeline configuration), releasing
 * (READY → AVAILABLE: triggering infrastructure provisioning via sagas), and the corresponding
 * reverse operations (unstage, unrelease). Handles saga completion and failure callbacks to
 * reconcile dataset state.
 */
@Slf4j
@Service
public class DataSetService extends BaseDataEntityService<DataSet, DataSetInputDTO> {

  // Stable identifiers for drift events emitted by applyInfrastructureResult.
  private static final String DRIFT_ORPHAN_SLUGS = "orphan-slugs";
  private static final String DRIFT_UNPROVISIONED_SLUGS = "unprovisioned-slugs";
  private static final String DRIFT_REPLACED_ROUTEID = "replaced-routeid";
  private static final String DRIFT_UNEXPECTED_ROUTEIDS = "unexpected-routeids";

  private final DataSetRepository dataSetRepository;
  private final DataSinkRepository dataSinkRepository;
  private final LayerRepository layerRepository;
  private final DataSetMapper dataSetMapper;
  private final DataPoolRepository dataPoolRepository;

  private final AssignmentFactory assignmentFactory;

  private final DataSetSagaPublisher sagaPublisher;

  private final PipelineRuntimeStatusService pipelineRuntimeStatusService;

  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  private final DataSourceDatapoolScopeValidator datapoolScopeValidator;
  private final ModelRegistryGateway modelRegistryGateway;

  private final DataSetMutationGuard dataSetMutationGuard;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSinkRepository dataSinkRepository,
      LayerRepository layerRepository,
      DataSetMapper dataSetMapper,
      DataPoolRepository dataPoolRepository,
      AssignmentFactory assignmentFactory,
      DataSetSagaPublisher sagaPublisher,
      PipelineRuntimeStatusService pipelineRuntimeStatusService,
      ObjectProvider<AllowedScopes> allowedScopesProvider,
      DataSourceDatapoolScopeValidator datapoolScopeValidator,
      ModelRegistryGateway modelRegistryGateway,
      DataSetMutationGuard dataSetMutationGuard) {
    this.dataSetRepository = dataSetRepository;
    this.dataSinkRepository = dataSinkRepository;
    this.layerRepository = layerRepository;
    this.dataSetMapper = dataSetMapper;
    this.dataPoolRepository = dataPoolRepository;
    this.assignmentFactory = assignmentFactory;
    this.sagaPublisher = sagaPublisher;
    this.pipelineRuntimeStatusService = pipelineRuntimeStatusService;
    this.allowedScopesProvider = allowedScopesProvider;
    this.datapoolScopeValidator = datapoolScopeValidator;
    this.modelRegistryGateway = modelRegistryGateway;
    this.dataSetMutationGuard = dataSetMutationGuard;
  }

  /**
   * Authorizes placing a dataset into the given target datapool (F4). OPA grants the dataset write
   * but cannot authorize the TARGET pool — it never sees the request body — so the backend enforces
   * it here using the per-request {@link AllowedScopes}: a TENANT (wildcard) caller may use any
   * pool, otherwise the target pool must be among the caller's authorized pools
   * (X-Allowed-Pool-Ids).
   *
   * @param datapoolId the target datapool the dataset is being placed into (non-null)
   * @throws AccessDeniedException if the caller is not authorized for the target pool
   */
  private void authorizeTargetPool(UUID datapoolId) {
    AllowedScopes scopes = allowedScopesProvider.getObject();
    if (scopes.isWildcard() || scopes.getPoolIds().contains(datapoolId)) {
      return;
    }
    throw new AccessDeniedException(
        "Not authorized to place a dataset into datapool " + datapoolId);
  }

  /**
   * Re-asserts the DataSource→DataPool scope rule for every DataSource across the dataset's
   * existing pipelines against the dataset's current datapool. The rule is enforced at
   * pipeline-write time against the pool the dataset had then; moving the dataset into a different
   * pool afterwards would otherwise leave a pipeline holding a DataSource that is out of scope for
   * the new pool. Rejecting here (and as a backstop before staging/release) keeps a dataset from
   * ever being released while carrying an out-of-scope DataSource.
   *
   * @param dataSet the dataset whose pipelines' DataSources are checked against {@code
   *     dataSet.getDataPool()}
   * @throws de.civitascore.portal.util.DataSourceScopeViolationException if any is out of scope
   */
  private void revalidatePipelineDataSourcesAgainstPool(DataSet dataSet) {
    Set<DataSource> dataSources =
        dataSet.getPipelines().stream()
            .flatMap(p -> p.getDataSources() == null ? Stream.empty() : p.getDataSources().stream())
            .collect(Collectors.toSet());
    if (!dataSources.isEmpty()) {
      datapoolScopeValidator.validate(dataSources, dataSet.getDataPool());
    }
  }

  @Override
  protected DataSetRepository getRepository() {
    return dataSetRepository;
  }

  @Override
  protected DataSetMapper getMapper() {
    return dataSetMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSet.class.getSimpleName();
  }

  @Override
  protected AssignmentFactory getAssignmentFactory() {
    return assignmentFactory;
  }

  @Override
  protected ReleasableStatus getEntityStatus(DataSet entity) {
    return entity.getDataSetStatus();
  }

  @Override
  protected void setEntityStatus(DataSet entity, ReleasableStatus status) {
    entity.setDataSetStatus((DataSetStatus) status);
  }

  @Override
  protected ReleasableStatus getDraftStatus() {
    return DataSetStatus.DRAFT;
  }

  @Override
  protected ReleasableStatus getAvailableStatus() {
    return DataSetStatus.AVAILABLE;
  }

  /**
   * Reconciles the entity's {@code namedApis} collection with the incoming input. A {@code null}
   * input list means "field omitted from the patch body" — leave the entity untouched. An empty
   * list clears the collection. A non-empty list is the new source of truth: entries are matched by
   * slug; existing rows are updated in place (preserving {@code id} and {@code routeId}, both of
   * which are server-managed), entries with new slugs are added, and entries whose slug is absent
   * from the input are removed (orphan removal).
   *
   * <p>The in-place update avoids the orphan-removal + unique-constraint flush-order foot-gun: a
   * DELETE+INSERT of a row with the same {@code (dataset_id, slug)} pair in the same flush would
   * violate {@code uk_named_api_dataset_slug}.
   */
  @Override
  protected DataSet postConvertToEntity(DataSet entity, DataSetInputDTO input) {
    UUID currentPoolId = entity.getDataPool() != null ? entity.getDataPool().getId() : null;
    if (input.getDatapoolId() != null) {
      DataPool dataPool =
          dataPoolRepository
              .findById(input.getDatapoolId())
              .orElseThrow(() -> new ResourceNotFoundException("DataPool", input.getDatapoolId()));
      // Authorize only when the dataset is actually being placed into a DIFFERENT pool. A PATCH
      // re-sends the dataset's existing datapoolId (BaseController#patchInput merges the current
      // state), so an update that leaves the pool unchanged must NOT require pool authorization —
      // otherwise a caller holding a direct dataset grant (scope id) but no pool scope could no
      // longer edit a dataset that happens to sit in a pool.
      if (!input.getDatapoolId().equals(currentPoolId)) {
        authorizeTargetPool(input.getDatapoolId());
      }
      entity.setDataPool(dataPool);
    } else if (input.isDatapoolIdPresent()) {
      // Only an explicit null clears the pool. An omitted field leaves it untouched, so a partial
      // write (a rename via PUT) cannot drop the dataset out of its pool as a side effect.
      entity.setDataPool(null);
    }

    // Re-assert scope against the resolved pool unconditionally: decoupling it from the pool-change
    // decision keeps the guard from silently lapsing if any future mutation path is added here. The
    // pool-change condition gates only authorization above.
    revalidatePipelineDataSourcesAgainstPool(entity);

    List<NamedApiInputDTO> incoming = input.getNamedApis();
    if (incoming != null) {
      // Validate slug uniqueness up front (before touching the entity): two NamedApi rows with the
      // same slug would otherwise hit the DB unique constraint as an opaque 500, and the slug is
      // the
      // per-named-API route key, so duplicates are ambiguous downstream. Fail with a business 400.
      Set<String> incomingSlugs = new HashSet<>();
      for (NamedApiInputDTO dto : incoming) {
        if (!incomingSlugs.add(dto.getSlug())) {
          throw new InvalidInputException(
              "namedApis",
              entity.getId(),
              "Duplicate named-API slug '"
                  + dto.getSlug()
                  + "' — named-API slugs must be unique within a dataset");
        }
      }
      long owsCount = incoming.stream().filter(dto -> dto.getStandard() == ApiStandard.OWS).count();
      if (owsCount > 1) {
        throw new InvalidInputException(
            "namedApis",
            entity.getId(),
            "A dataset can expose at most one OWS named API; got "
                + owsCount
                + ". Workspace and route target both derive from the dataset id, so they would all"
                + " serve the same layers from the same workspace, leaving no way to tell which"
                + " API a layer belongs to.");
      }
      Map<String, NamedApi> existingBySlug = new HashMap<>();
      for (NamedApi api : entity.getNamedApis()) {
        existingBySlug.put(api.getSlug(), api);
      }
      for (NamedApiInputDTO dto : incoming) {
        NamedApi existing = existingBySlug.get(dto.getSlug());
        if (existing != null) {
          existing.setName(dto.getName());
          existing.setStandard(dto.getStandard());
          existing.setVersion(dto.getVersion());
          existing.setDescription(dto.getDescription());
        } else {
          NamedApi created = dataSetMapper.toNamedApiEntity(dto);
          created.setDataSet(entity);
          entity.getNamedApis().add(created);
        }
      }
      entity.getNamedApis().removeIf(api -> !incomingSlugs.contains(api.getSlug()));
      cleanUpOrphanedOwsLayers(entity);
    }

    // A DataSet is backed by a Model Forge manifest artifact its members are linked into
    // (pipelines,
    // and transitively their sources/sinks/mappings/structures). Create it once, on first persist
    // (when no manifest is pinned yet); its title mirrors the dataset name. Model Forge validates
    // it.
    if (entity.getManifestLogicalUrn() == null) {
      ModelRegistryGateway.ModelPin pin =
          modelRegistryGateway.createDataSetManifest(entity.getName());
      entity.setManifestLogicalUrn(pin.logicalUrn());
      entity.setManifestUrn(pin.versionedUrn());
    }
    return super.postConvertToEntity(entity, input);
  }

  /**
   * Layers are published only through the dataset's OWS {@link NamedApi}, so once none remains
   * nothing serves them. Keyed on the post-reconcile state rather than on the removed entry, so
   * replacing an OWS API with a differently-slugged one keeps the layers.
   */
  private void cleanUpOrphanedOwsLayers(DataSet entity) {
    if (entity.getId() == null) {
      return;
    }
    boolean hasOwsApi =
        entity.getNamedApis().stream().anyMatch(api -> api.getStandard() == ApiStandard.OWS);
    if (!hasOwsApi) {
      int removed = layerRepository.deleteByDataSetId(entity.getId());
      if (removed > 0) {
        // The response is an ordinary 200 on the dataset, so without this the layer rows are gone
        // with no record of it anywhere.
        log.info(
            "Removed {} layer(s) of dataset {}: no OWS named API left to serve them",
            removed,
            entity.getId());
      }
    }
  }

  /**
   * Rejects updates to a dataset that is not editable. For released datasets, use
   * updateReleasedMeta instead.
   *
   * <p>Guards the public entry point rather than {@code preProcessUpdateInput}: the ready- and
   * released-metadata endpoints reach {@code super.update} through {@code updateMetaOf}, and they
   * must keep working on a non-DRAFT dataset.
   *
   * @throws DataSetNotEditableException if the dataset is not in DRAFT
   * @throws SagaInFlightException if a saga is in flight, which a DRAFT dataset still carries while
   *     an unrelease teardown runs
   */
  @Override
  public DataSet update(UUID id, DataSetInputDTO input) {
    dataSetMutationGuard.requireMutable(findByIdOrThrow(id));
    return super.update(id, input);
  }

  /**
   * Updates the editable metadata of a released dataset: {@code name}, {@code description}, {@code
   * openDataAccess}, and {@code assignments}. The {@code namedApis} set is immutable while the
   * dataset is in READY / AVAILABLE — per concept #1379 + #1384 the only path to change it is
   * unrelease → edit in DRAFT → release. For AVAILABLE datasets with existing infrastructure,
   * triggers a saga UPDATE if no saga is currently in-flight.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws InvalidInputException if the dataset is DRAFT or the input carries {@code namedApis}
   * @throws SagaInFlightException if a saga is in-flight for this dataset
   */
  @Override
  @Transactional
  public DataSet updateReleasedMeta(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() == DataSetStatus.DRAFT) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "This endpoint requires a released dataset (READY or AVAILABLE), current status: DRAFT");
    }
    return updateMetaOf(existingEntity, input);
  }

  /**
   * Restricted to READY so it can be granted with DATASET_UPDATE alone, without DATASET_RELEASE:
   * editing a staged dataset is an update, not a release.
   */
  @Transactional
  public DataSet updateReadyMeta(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() != DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "This endpoint requires a READY dataset, current status: "
              + existingEntity.getDataSetStatus());
    }
    return updateMetaOf(existingEntity, input);
  }

  private DataSet updateMetaOf(DataSet existingEntity, DataSetInputDTO input) {
    UUID id = existingEntity.getId();
    if (existingEntity.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          id,
          existingEntity.getPendingSagaType(),
          "Cannot update metadata while a saga is in-flight: "
              + existingEntity.getPendingSagaType());
    }

    if (input.getNamedApis() != null) {
      throw new InvalidInputException(
          "namedApis",
          id,
          "Named APIs cannot be changed while the dataset is "
              + existingEntity.getDataSetStatus()
              + ". Unrelease the dataset and edit it in DRAFT.");
    }

    Set<Pipeline> previousPipelines = new HashSet<>(existingEntity.getPipelines());
    DataSet updated = super.update(id, input);

    if (updated.getDataSetStatus() == DataSetStatus.AVAILABLE
        && updated.isProvisioned()
        && updated.getPendingSagaType() == null) {
      updated.setPendingSagaType(PendingSagaType.UPDATE);
      updated = dataSetRepository.save(updated);
      sagaPublisher.publishUpdateRequested(updated, previousPipelines);
    }

    return updated;
  }

  /**
   * Stages a dataset by validating it has at least one pipeline with data sources and setting
   * status to READY.
   *
   * @param id the dataset ID
   * @return the staged dataset
   * @throws InvalidInputException if dataset has no pipelines or is not in DRAFT status
   */
  @Transactional
  public DataSet stage(UUID id) {
    DataSet dataSet =
        dataSetRepository
            .findByIdWithPipelineDataSources(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException("dataSetStatus", id, "Only DRAFT datasets can be staged");
    }
    if (StringUtils.isBlank(dataSet.getName())) {
      throw new InvalidInputException("name", id, "DataSet name must not be blank");
    }
    if (StringUtils.isBlank(dataSet.getDescription())) {
      throw new InvalidInputException("description", id, "DataSet description must not be blank");
    }
    if (dataSet.getPipelines() == null || dataSet.getPipelines().isEmpty()) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must contain at least one Pipeline before staging");
    }
    boolean hasDataSource =
        dataSet.getPipelines().stream()
            .anyMatch(p -> p.getDataSources() != null && !p.getDataSources().isEmpty());
    if (!hasDataSource) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must have at least one Pipeline with DataSources");
    }

    revalidatePipelineDataSourcesAgainstPool(dataSet);

    dataSet.setDataSetStatus(DataSetStatus.READY);
    return dataSetRepository.save(dataSet);
  }

  /**
   * Unstages a dataset, reverting it from READY to DRAFT.
   *
   * <p>An in-flight UNRELEASE saga is allowed: after {@link #unrelease} the dataset is already
   * READY while its route/pipeline teardown runs, and the frontend chains unrelease + unstage to go
   * AVAILABLE → DRAFT in one step. The teardown keeps running; its completion callback leaves a
   * DRAFT dataset untouched.
   *
   * @param id the dataset ID
   * @return the unstaged dataset
   * @throws InvalidInputException if dataset is not in READY status
   * @throws SagaInFlightException if any saga other than UNRELEASE is in-flight
   */
  @Transactional
  public DataSet unstage(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be unstaged from READY status");
    }

    PendingSagaType pending = dataSet.getPendingSagaType();
    if (pending != null && pending != PendingSagaType.UNRELEASE) {
      throw new SagaInFlightException(
          id, pending, "Cannot unstage while a saga is in-flight: " + pending);
    }

    dataSet.setDataSetStatus(DataSetStatus.DRAFT);
    return dataSetRepository.save(dataSet);
  }

  /**
   * Releases a dataset, transitioning it from READY to AVAILABLE. Triggers a DATASET_CREATE saga to
   * provision infrastructure (FROST, APISIX, Redpanda). The Kafka publish is synchronous — if the
   * broker is unreachable the exception propagates before the transaction commits, rolling back the
   * status change and preventing the dual-write problem.
   *
   * @param id the dataset ID
   * @return the released dataset
   * @throws InvalidInputException if dataset is not in READY status, or if its map surface is only
   *     half configured
   * @throws SagaInFlightException if a saga is already in-flight
   */
  @Override
  @Transactional
  public DataSet release(UUID id) {
    DataSet dataSet =
        dataSetRepository
            .findByIdWithPipelineDataSources(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (dataSet.getDataSetStatus() != DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be released from READY status");
    }

    if (dataSet.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          id,
          dataSet.getPendingSagaType(),
          "Cannot release while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    revalidatePipelineDataSourcesAgainstPool(dataSet);
    verifyPublishedSurfacesAreServable(dataSet);

    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setPendingSagaType(PendingSagaType.CREATE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishCreateRequested(saved);

    return saved;
  }

  /**
   * Rejects a release whose published surfaces would be provisioned but unreachable, or routed but
   * empty. Layers, the OWS route and the FROST project are each gated independently downstream, so
   * one without its counterpart provisions half a surface and reports success.
   *
   * <p>A sink without its named API is deliberately NOT rejected: that is the state an unrelease
   * leaves behind, and the data is meant to survive it.
   */
  private void verifyPublishedSurfacesAreServable(DataSet dataSet) {
    boolean hasOwsApi =
        dataSet.getNamedApis().stream().anyMatch(api -> api.getStandard() == ApiStandard.OWS);

    if (layerRepository.existsByDataSetId(dataSet.getId()) && !hasOwsApi) {
      throw new InvalidInputException(
          "namedApis",
          dataSet.getId(),
          "DataSet has layers but no OWS named API to serve them. Add an OWS named API or remove"
              + " the layers.");
    }

    if (hasOwsApi && !hasSink(dataSet, DataSinkType.POSTGIS)) {
      throw new InvalidInputException(
          "dataSinks",
          dataSet.getId(),
          "DataSet has an OWS named API but no POSTGIS data sink to back its map service. The"
              + " route would resolve to a workspace that is never provisioned.");
    }

    boolean hasStaApi =
        dataSet.getNamedApis().stream().anyMatch(api -> api.getStandard() == ApiStandard.STA);

    if (hasStaApi && !hasSink(dataSet, DataSinkType.FROST)) {
      throw new InvalidInputException(
          "dataSinks",
          dataSet.getId(),
          "DataSet has an STA named API but no FROST data sink to back it. The route would resolve"
              + " to a FROST project that is never provisioned.");
    }
  }

  private boolean hasSink(DataSet dataSet, DataSinkType type) {
    return dataSinkRepository.existsByDataSetIdAndDataSinkType(dataSet.getId(), type);
  }

  /**
   * Unreleases a dataset by triggering a DATASET_UNRELEASE saga that tears down only the ingest and
   * consumer-access layer (NiFi pipeline + APISIX route/upstream). The data-holding sink (PostGIS
   * table, FROST project) is deliberately left intact so a later re-release reuses it.
   *
   * <p>The status transitions to READY optimistically (mirroring {@link #release}, which sets
   * AVAILABLE up front); the UNRELEASE saga then tears the route/pipeline layer down
   * asynchronously. This lets the frontend chain unrelease + unstage into a single AVAILABLE →
   * DRAFT move without waiting for the saga. A failed teardown reverts to AVAILABLE.
   *
   * @param id the dataset ID
   * @return the dataset in READY status with a pending UNRELEASE saga
   * @throws InvalidInputException if dataset is not AVAILABLE
   * @throws SagaInFlightException if a saga is already in-flight
   */
  @Override
  @Transactional
  public DataSet unrelease(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.AVAILABLE) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be unreleased from AVAILABLE status");
    }

    if (dataSet.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          id,
          dataSet.getPendingSagaType(),
          "Cannot unrelease while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    dataSet.setDataSetStatus(DataSetStatus.READY);
    dataSet.setPendingSagaType(PendingSagaType.UNRELEASE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishUnreleaseRequested(saved);

    return saved;
  }

  /**
   * Explicitly adds a reusable artifact (by CORE URN) to this dataset's manifest — the "Beides"
   * explicit-assignment path, independent of any Pipeline that uses it. Model Forge maintains the
   * manifest (a {@code dataset-ref} membership edge).
   */
  @Transactional
  public void linkMember(UUID datasetId, String memberUrn) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    if (dataSet.getManifestLogicalUrn() == null) {
      throw new InvalidInputException(
          "DataSet", datasetId, "DataSet has no manifest to link members into");
    }
    if (memberUrn == null || memberUrn.isBlank()) {
      throw new InvalidInputException("member", datasetId, "member artifact URN is required");
    }
    modelRegistryGateway.linkToDataSet(dataSet.getManifestLogicalUrn(), memberUrn);
  }

  /** Explicitly removes an artifact (by CORE URN) from this dataset's manifest. */
  @Transactional
  public void unlinkMember(UUID datasetId, String memberUrn) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    if (dataSet.getManifestLogicalUrn() != null && memberUrn != null && !memberUrn.isBlank()) {
      modelRegistryGateway.unlinkFromDataSet(dataSet.getManifestLogicalUrn(), memberUrn);
    }
  }

  /**
   * Handles a completed saga result. Updates infrastructure fields and transitions state based on
   * the saga type that was pending.
   */
  @Transactional
  public void handleSagaCompleted(UUID datasetId, SagaResultPayload result) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    PendingSagaType pendingType = dataSet.getPendingSagaType();

    if (pendingType == null) {
      log.warn(
          "handleSagaCompleted: no pending saga for dataset {} (duplicate delivery?), skipping",
          datasetId);
      return;
    }

    warnAboutStaleFeatureTypes(datasetId, result);

    switch (pendingType) {
      case CREATE -> {
        applyInfrastructureResult(dataSet, result);
        markProvisioned(dataSet);
        pipelineRuntimeStatusService.markDeploymentSucceeded(deployedPipelineIds(dataSet, result));
        log.info("Saga CREATE completed for dataset {}, infrastructure provisioned", datasetId);
      }
      case UPDATE -> {
        applyInfrastructureResult(dataSet, result);
        markProvisioned(dataSet);
        pipelineRuntimeStatusService.markDeploymentSucceeded(deployedPipelineIds(dataSet, result));
        log.info("Saga UPDATE completed for dataset {}", datasetId);
      }
      case UNRELEASE -> {
        dataSet.clearRouteAndPipelineInfrastructure();
        // unrelease already set READY optimistically; only flip a teardown not preceded by the
        // optimistic set (still AVAILABLE). A user move to DRAFT mid-teardown (the AVAILABLE →
        // DRAFT chain) is left untouched.
        if (dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE) {
          dataSet.setDataSetStatus(DataSetStatus.READY);
        }
        log.info(
            "Saga UNRELEASE completed for dataset {}, route/pipeline torn down (sink kept)",
            datasetId);
      }
      case DELETE -> {
        // Teardown of the full infrastructure (including the sink) succeeded; now remove the
        // entity itself. Returning here skips the save() below — the row no longer exists.
        // A registry refusal is recorded rather than thrown: the infrastructure this dataset
        // described is already gone, and letting the removal fail would restore the row with its
        // pending saga type set, which every later delete then refuses. A stranded artifact is
        // recoverable; a dataset that can never be deleted again is not.
        deleteWithSinks(dataSet, false);
        log.info("Saga DELETE completed for dataset {}, entity removed", datasetId);
        return;
      }
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /**
   * Handles a failed saga result. Reverts state as needed based on the pending saga type. CREATE
   * failures revert to READY; DELETE failures undo the optimistic status change back to AVAILABLE
   * only if the dataset is still READY (a user-initiated move to DRAFT is preserved); UPDATE
   * failures stay AVAILABLE.
   *
   * @param datasetId the dataset ID
   * @param failedStep the saga step that failed
   * @param error the error message
   * @param compensated whether compensation was executed
   */
  @Transactional
  public void handleSagaFailed(
      UUID datasetId, String failedStep, String error, boolean compensated) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    PendingSagaType pendingType = dataSet.getPendingSagaType();

    if (pendingType == null) {
      log.warn(
          "handleSagaFailed: no pending saga for dataset {} (duplicate/late delivery?), skipping",
          datasetId);
      return;
    }

    switch (pendingType) {
      case CREATE -> {
        dataSet.setDataSetStatus(DataSetStatus.READY);
        log.warn(
            "Saga CREATE failed for dataset {}: step={}, error={}, compensated={}."
                + " Reverted to READY",
            datasetId,
            Encode.forJava(failedStep),
            Encode.forJava(error),
            compensated);
      }
      case UPDATE ->
          log.warn(
              "Saga UPDATE failed for dataset {}: step={}, error={}, compensated={}."
                  + " Staying AVAILABLE",
              datasetId,
              Encode.forJava(failedStep),
              Encode.forJava(error),
              compensated);
      case UNRELEASE -> {
        // Undo the optimistic READY only if still READY; a user move to DRAFT mid-teardown (the
        // AVAILABLE → DRAFT chain) is preserved. A failed route/pipeline teardown must not stay
        // READY — live routes may remain.
        if (dataSet.getDataSetStatus() == DataSetStatus.READY) {
          dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
        }
        logTeardownFailure(
            "Saga UNRELEASE failed for dataset {}: step={}, error={}, compensated={}."
                + " Reverted to AVAILABLE — route/pipeline teardown incomplete, stale resources"
                + " may exist",
            datasetId,
            failedStep,
            error,
            compensated);
      }
      case DELETE ->
          logTeardownFailure(
              "Saga DELETE failed for dataset {}: step={}, error={}, compensated={}."
                  + " Staying AVAILABLE — stale resources may exist",
              datasetId,
              failedStep,
              error,
              compensated);
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /**
   * A saga reports feature types it could not unpublish. They keep being served for layers the
   * dataset no longer has, and no portal query can find them — the layer table is already correct.
   */
  private void warnAboutStaleFeatureTypes(UUID datasetId, SagaResultPayload result) {
    if (result.staleFeatureTypes() == null || result.staleFeatureTypes().isEmpty()) {
      return;
    }
    log.warn(
        "Dataset {} still serves {} feature type(s) for removed layers: {}",
        datasetId,
        result.staleFeatureTypes().size(),
        Encode.forJava(String.join(", ", result.staleFeatureTypes())));
  }

  /**
   * Logs a teardown-saga failure. An uncompensated failure is confirmed leaked infrastructure, not
   * a transient — signal it at ERROR so it is not lost among ordinary warnings.
   */
  private void logTeardownFailure(
      String msg, UUID datasetId, String failedStep, String error, boolean compensated) {
    if (compensated) {
      log.warn(msg, datasetId, Encode.forJava(failedStep), Encode.forJava(error), true);
    } else {
      log.error(msg, datasetId, Encode.forJava(failedStep), Encode.forJava(error), false);
    }
  }

  /**
   * Deletes a dataset. An AVAILABLE dataset must be unreleased first — its ingest and consumer
   * access are still live, so it cannot be deleted directly. Otherwise: a dataset that was never
   * released is removed immediately, and one that still holds provisioned infrastructure from a
   * prior release goes through a DATASET_DELETE saga that tears it down; the entity is removed once
   * the saga completes (see {@link #handleSagaCompleted}).
   *
   * <p>{@code provisioned} is the discriminator: a completed CREATE saga sets it, whichever sinks
   * the dataset has.
   */
  @Override
  @Transactional
  public void deleteById(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "Cannot delete an AVAILABLE dataset. Unrelease it first (POST /datasets/{id}/unrelease)");
    }

    if (!dataSet.isProvisioned()) {
      deleteWithSinks(dataSet, true);
      return;
    }

    if (dataSet.getPendingSagaType() != null) {
      throw new SagaInFlightException(
          id,
          dataSet.getPendingSagaType(),
          "Cannot delete while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    dataSet.setPendingSagaType(PendingSagaType.DELETE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishDeleteRequested(saved);
  }

  /**
   * Removes a dataset together with its DataSinks. A DataSink is owned by the dataset (its {@code
   * dataset_id} FK is non-null) but the dataset has no cascading collection for it, and a released
   * sink additionally carries a {@code pipeline_id} FK into one of the cascade-removed pipelines.
   * Deleting the sinks first — after detaching them from their pipeline — clears both FKs before
   * the dataset delete cascades into the pipelines, avoiding the FK violation that would otherwise
   * roll the transaction back.
   *
   * <p>Going through the repository deliberately bypasses the layer guard that rejects a standalone
   * {@code DELETE /datasinks/{id}}: that guard protects a sink whose dataset lives on, whereas here
   * the whole aggregate goes away.
   *
   * <p>Flushing here keeps a constraint violation inside this call instead of surfacing it at
   * commit, after a caller has already logged the removal as done.
   *
   * <p>This is the only place a dataset row is removed — a provisioned dataset keeps its row until
   * its teardown saga reports back — so the manifest cleanup belongs here rather than in the {@code
   * postDelete} hook, which a two-phase delete never reaches.
   *
   * <p>Registry cleanup runs in reference order, because the registry refuses to delete an artifact
   * anything still points at: the manifest holds the membership edges, and each pipeline holds the
   * edges onto the sink configurations it writes through.
   *
   * @param failOnRefusal whether a refused removal fails the whole delete. True while the caller
   *     can still be told. False once the teardown saga has destroyed the infrastructure, where
   *     failing would restore a dataset no later delete can remove.
   */
  private void deleteWithSinks(DataSet dataSet, boolean failOnRefusal) {
    // The registry artifacts this DataSet owns, read while its rows still exist. A pipeline row is
    // removed by a JPA cascade that does not run its service hook, so its artifact is removed here
    // instead, together with the mappings no other pipeline uses.
    List<String> pipelineUrns =
        dataSet.getPipelines().stream()
            .map(Pipeline::getModelLogicalUrn)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    List<DataSink> sinks = dataSinkRepository.findByDataSetId(dataSet.getId());
    List<String> sinkConfigurationUrns =
        sinks.stream()
            .map(DataSink::getConfigurationLogicalUrn)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    sinks.forEach(
        sink -> {
          sink.setPipeline(null);
          dataSinkRepository.delete(sink);
        });
    dataSetRepository.delete(dataSet);
    dataSetRepository.flush();

    // The manifest goes first: it holds the dataset-ref edges onto the members. Then the pipeline
    // artifacts, each taking the mappings no other pipeline uses, and only then the sink
    // configurations those pipelines pointed at.
    if (dataSet.getManifestLogicalUrn() != null) {
      removeOwnedArtifacts(List.of(dataSet.getManifestLogicalUrn()), true, failOnRefusal);
    }
    removeOwnedArtifacts(pipelineUrns, true, failOnRefusal);
    removeOwnedArtifacts(sinkConfigurationUrns, false, failOnRefusal);
  }

  /**
   * Removes registry artifacts a deleted DataSet owned, each taking the artifacts it owns with it
   * when {@code cascade} is set. An artifact a second DataSet also lists is refused by the
   * registry; one that only this DataSet listed is removed and unlinked.
   *
   * <p>With {@code failOnRefusal} a refusal rolls the whole delete back, rows included, so the
   * dataset never disappears while a model it owned survives with nothing left to reach it. Without
   * it the refusal is recorded and the removal stands — see {@link #deleteWithSinks}.
   */
  private void removeOwnedArtifacts(
      List<String> logicalUrns, boolean cascade, boolean failOnRefusal) {
    for (String logicalUrn : logicalUrns) {
      try {
        modelRegistryGateway.deleteArtifact(logicalUrn, cascade);
      } catch (RuntimeException e) {
        if (failOnRefusal) {
          throw e;
        }
        log.error(
            "Could not remove artifact {} of a deleted DataSet, it is now unreferenced: {}",
            Encode.forJava(logicalUrn),
            Encode.forJava(e.getMessage()));
      }
    }
  }

  /**
   * Applies infrastructure fields from the saga result payload to the dataset entity. Uses
   * PATCH-style semantics: only non-null fields in the result are applied. This is intentional
   * because partial saga results (e.g., UPDATE saga that only touches APISIX) should not null out
   * fields set by earlier steps.
   *
   * <p><b>Drift policy:</b> divergence between the saga result and the entity (orphan slugs,
   * unprovisioned slugs, replaced routeIds) is logged as drift events but does not fail the saga.
   * Throwing here would not undo the upstream APISIX/FROST mutations and would cause Kafka
   * redelivery to retry indefinitely without the orchestrator being able to compensate. Real
   * compensation (fail-saga + cleanup) is not yet implemented.
   */
  /**
   * Marks the dataset provisioned once a provisioning saga has completed, so a later delete knows
   * it must tear infrastructure down. Deliberately independent of which sinks the dataset has:
   * FROST is provisioned only for datasets carrying a FROST sink, so a project id is no longer a
   * reliable proxy. Never resets it: the flag survives an unrelease and is only dropped when the
   * row is removed on DELETE.
   */
  private void markProvisioned(DataSet dataSet) {
    dataSet.setProvisioned(true);
  }

  /**
   * The pipeline ids a completed saga reports, narrowed to those still attached to the dataset. An
   * UPDATE result also carries the ids the same saga tore down, and marking a removed pipeline as
   * successfully deployed would leave a permanently healthy status on a flow that no longer exists.
   */
  private List<String> deployedPipelineIds(DataSet dataSet, SagaResultPayload result) {
    if (result.pipelineIds() == null) {
      return List.of();
    }
    Set<String> attached =
        dataSet.getPipelines().stream()
            .map(Pipeline::getId)
            .filter(Objects::nonNull)
            .map(UUID::toString)
            .collect(Collectors.toSet());
    return result.pipelineIds().stream().filter(attached::contains).toList();
  }

  private void applyInfrastructureResult(DataSet dataSet, SagaResultPayload result) {
    if (result.projectId() != null) {
      dataSet.setProjectId(result.projectId());
    }
    if (result.baseUrl() != null) {
      dataSet.setFrostBaseUrl(result.baseUrl());
    }
    if (!dataSet.getNamedApis().isEmpty()) {
      boolean resultHasRouteIds = result.routeIds() != null && !result.routeIds().isEmpty();
      Set<String> entitySlugs =
          dataSet.getNamedApis().stream().map(NamedApi::getSlug).collect(Collectors.toSet());
      Set<String> resultSlugs = resultHasRouteIds ? result.routeIds().keySet() : Set.of();

      List<String> orphanSlugs =
          resultSlugs.stream().filter(slug -> !entitySlugs.contains(slug)).toList();
      if (!orphanSlugs.isEmpty()) {
        log.error(
            "drift={} dataset={} slugs={} — saga returned routeIds for slugs not on the entity;"
                + " APISIX routes are likely leaked.",
            DRIFT_ORPHAN_SLUGS,
            dataSet.getId(),
            Encode.forJava(orphanSlugs.toString()));
      }

      List<String> unprovisionedSlugs =
          entitySlugs.stream().filter(slug -> !resultSlugs.contains(slug)).toList();
      if (!unprovisionedSlugs.isEmpty()) {
        log.error(
            "drift={} dataset={} slugs={} — saga did not return routeIds for these entity slugs;"
                + " user-facing endpoints will 404.",
            DRIFT_UNPROVISIONED_SLUGS,
            dataSet.getId(),
            Encode.forJava(unprovisionedSlugs.toString()));
      }

      if (resultHasRouteIds) {
        dataSet
            .getNamedApis()
            .forEach(
                api -> {
                  String incomingRouteId = result.routeIds().get(api.getSlug());
                  if (incomingRouteId == null) {
                    // Already logged as unprovisioned above; preserve current routeId.
                    return;
                  }
                  String currentRouteId = api.getRouteId();
                  if (currentRouteId != null && !currentRouteId.equals(incomingRouteId)) {
                    log.error(
                        "drift={} dataset={} slug={} previous={} incoming={} — prior APISIX route"
                            + " is likely leaked.",
                        DRIFT_REPLACED_ROUTEID,
                        dataSet.getId(),
                        Encode.forJava(api.getSlug()),
                        Encode.forJava(currentRouteId),
                        Encode.forJava(incomingRouteId));
                  }
                  api.setRouteId(incomingRouteId);
                });
      }
    } else if (result.routeIds() != null && !result.routeIds().isEmpty()) {
      log.error(
          "drift={} dataset={} slugs={} — saga returned routeIds but entity has no namedApis;"
              + " APISIX routes are likely leaked.",
          DRIFT_UNEXPECTED_ROUTEIDS,
          dataSet.getId(),
          Encode.forJava(result.routeIds().keySet().toString()));
    }
    if (result.serviceId() != null) {
      dataSet.setServiceId(result.serviceId());
    }
    if (result.publicUrl() != null) {
      dataSet.setPublicUrl(result.publicUrl());
    }
    if (result.pipelineIds() != null) {
      dataSet.setPipelineIds(result.pipelineIds());
    }
  }
}

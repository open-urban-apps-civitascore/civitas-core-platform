package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataPool;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.model.input.NamedApiInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataPoolRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.security.AllowedScopes;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final DataSetMapper dataSetMapper;
  private final DataPoolRepository dataPoolRepository;

  private final AssignmentFactory assignmentFactory;

  private final DataSetSagaPublisher sagaPublisher;

  private final ObjectProvider<AllowedScopes> allowedScopesProvider;

  private final ModelRegistryGateway modelRegistryGateway;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSetMapper dataSetMapper,
      DataPoolRepository dataPoolRepository,
      AssignmentFactory assignmentFactory,
      DataSetSagaPublisher sagaPublisher,
      ObjectProvider<AllowedScopes> allowedScopesProvider,
      ModelRegistryGateway modelRegistryGateway) {
    this.dataSetRepository = dataSetRepository;
    this.dataSetMapper = dataSetMapper;
    this.dataPoolRepository = dataPoolRepository;
    this.assignmentFactory = assignmentFactory;
    this.sagaPublisher = sagaPublisher;
    this.allowedScopesProvider = allowedScopesProvider;
    this.modelRegistryGateway = modelRegistryGateway;
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
    } else {
      entity.setDataPool(null);
    }

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
   * After the dataset row is deleted, delete its Model Forge manifest artifact. A DataSet groups
   * its members, it does not own them, so the members are kept; Model Forge drops the manifest's
   * outgoing {@code dataset-ref} edges, leaving former members as orphans (findable via the orphan
   * query).
   */
  @Override
  protected void postDelete(DataSet entity) {
    if (entity != null && entity.getManifestLogicalUrn() != null) {
      modelRegistryGateway.deleteDataSet(entity.getManifestLogicalUrn());
    }
  }

  /**
   * Override update to ensure it can only be called for DRAFT datasets. For released datasets, use
   * updateReleasedMeta instead.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws InvalidInputException if trying to update a non-DRAFT dataset
   */
  @Override
  public DataSet update(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "DataSet can only be updated in DRAFT status, current status: "
              + existingEntity.getDataSetStatus());
    }
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
   * @throws ResourceInUseException if a saga is in-flight for this dataset
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

    if (existingEntity.getPendingSagaType() != null) {
      throw new ResourceInUseException(
          "DataSet",
          id,
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
        && updated.getProjectId() != null
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
    DataSet dataSet = findByIdOrThrow(id);

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

    dataSet.setDataSetStatus(DataSetStatus.READY);
    return dataSetRepository.save(dataSet);
  }

  /**
   * Unstages a dataset, reverting it from READY to DRAFT.
   *
   * @param id the dataset ID
   * @return the unstaged dataset
   * @throws InvalidInputException if dataset is not in READY status
   */
  @Transactional
  public DataSet unstage(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be unstaged from READY status");
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
   * @throws InvalidInputException if dataset is not in READY status
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

    dataSet.setDataSetStatus(DataSetStatus.AVAILABLE);
    dataSet.setPendingSagaType(PendingSagaType.CREATE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishCreateRequested(saved);

    return saved;
  }

  /**
   * Unreleases a dataset by triggering a DATASET_DELETE saga to tear down infrastructure. On saga
   * completion, the dataset transitions from AVAILABLE to READY.
   *
   * @param id the dataset ID
   * @return the dataset with pending DELETE saga
   * @throws InvalidInputException if dataset is not AVAILABLE
   * @throws ResourceInUseException if a saga is already in-flight
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
      throw new ResourceInUseException(
          "DataSet",
          id,
          "Cannot unrelease while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    dataSet.setPendingSagaType(PendingSagaType.DELETE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishDeleteRequested(saved);

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
   * CORE URNs of artifacts of the given type ({@code mapping}/{@code pipeline}/{@code datasource}/
   * {@code datasink}/{@code datastructure}) that belong to no dataset — the "orphans by type"
   * query.
   */
  public List<String> orphans(String type) {
    return modelRegistryGateway.orphanUrns(type);
  }

  /**
   * Handles a completed saga result. Updates infrastructure fields and transitions state based on
   * the saga type that was pending.
   */
  @Transactional
  public void handleSagaCompleted(UUID datasetId, SagaResultPayload result) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    PendingSagaType pendingType = dataSet.getPendingSagaType();

    if (pendingType == PendingSagaType.DELETE) {
      dataSet.clearInfrastructureFields();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      log.info("Saga DELETE completed for dataset {}, reverted to READY", datasetId);
    } else if (pendingType == PendingSagaType.CREATE) {
      applyInfrastructureResult(dataSet, result);
      log.info("Saga CREATE completed for dataset {}, infrastructure provisioned", datasetId);
    } else if (pendingType == PendingSagaType.UPDATE) {
      applyInfrastructureResult(dataSet, result);
      log.info("Saga UPDATE completed for dataset {}", datasetId);
    } else {
      log.warn(
          "handleSagaCompleted: no pending saga for dataset {} (duplicate delivery?), skipping",
          datasetId);
      return;
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /**
   * Handles a failed saga result. Reverts state as needed based on the pending saga type. For
   * CREATE failures the dataset reverts to READY; for UPDATE and DELETE failures it stays
   * AVAILABLE.
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

    if (pendingType == PendingSagaType.CREATE) {
      dataSet.setDataSetStatus(DataSetStatus.READY);
      log.warn(
          "Saga CREATE failed for dataset {}: step={}, error={}, compensated={}. Reverted to READY",
          datasetId,
          Encode.forJava(failedStep),
          Encode.forJava(error),
          compensated);
    } else if (pendingType == PendingSagaType.UPDATE) {
      log.warn(
          "Saga UPDATE failed for dataset {}: step={}, error={}, compensated={}. Staying AVAILABLE",
          datasetId,
          Encode.forJava(failedStep),
          Encode.forJava(error),
          compensated);
    } else if (pendingType == PendingSagaType.DELETE) {
      log.warn(
          "Saga DELETE failed for dataset {}: step={}, error={}, compensated={}. "
              + "Staying AVAILABLE — stale resources may exist",
          datasetId,
          Encode.forJava(failedStep),
          Encode.forJava(error),
          compensated);
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /**
   * Deletes a DRAFT dataset immediately. READY datasets cannot be deleted — mark as draft first.
   * For AVAILABLE datasets the controller routes through {@link #triggerDeleteSaga} instead,
   * returning 202 Accepted for the asynchronous teardown.
   */
  @Override
  @Transactional
  public void deleteById(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() == DataSetStatus.DRAFT) {
      super.deleteById(id);
      return;
    }

    if (dataSet.getDataSetStatus() == DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "Cannot delete a READY dataset. Unstage it first (POST /datasets/{id}/unstage)");
    }

    throw new InvalidInputException(
        "dataSetStatus",
        id,
        "Cannot delete an AVAILABLE dataset. Unrelease it first (POST /datasets/{id}/unrelease) to tear down infrastructure");
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

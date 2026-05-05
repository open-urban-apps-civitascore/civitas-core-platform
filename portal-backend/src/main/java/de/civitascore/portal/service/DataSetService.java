package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.messaging.saga.SagaResultPayload;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.NamedApi;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataSet} entities through their full lifecycle: DRAFT, READY, and
 * AVAILABLE. Orchestrates staging (DRAFT → READY: generating distributions from pipeline APIs),
 * releasing (READY → AVAILABLE: triggering infrastructure provisioning via sagas), and the
 * corresponding reverse operations (unstage, unrelease). Handles saga completion and failure
 * callbacks to reconcile dataset state.
 */
@Slf4j
@Service
public class DataSetService extends BaseDataEntityService<DataSet, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;

  private final AssignmentFactory assignmentFactory;
  private final DistributionService distributionService;

  private final DataSetSagaPublisher sagaPublisher;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSetMapper dataSetMapper,
      AssignmentFactory assignmentFactory,
      DistributionService distributionService,
      DataSetSagaPublisher sagaPublisher) {
    this.dataSetRepository = dataSetRepository;
    this.dataSetMapper = dataSetMapper;
    this.assignmentFactory = assignmentFactory;
    this.distributionService = distributionService;
    this.sagaPublisher = sagaPublisher;
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
   * Updates only the metadata (name, description) of a released dataset. Cannot modify
   * persistenceId or pipelines. For AVAILABLE datasets with existing infrastructure, triggers a
   * saga UPDATE if no saga is currently in-flight.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws InvalidInputException if trying to update a DRAFT dataset
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
   * Stages a dataset by validating it has at least one pipeline, generating distributions from
   * pipeline APIs, and setting status to READY.
   *
   * @param id the dataset ID
   * @return the staged dataset
   * @throws InvalidInputException if dataset has no pipelines or is not in DRAFT status
   */
  @Transactional
  public DataSet stage(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    // Validate status is DRAFT
    if (dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException("dataSetStatus", id, "Only DRAFT datasets can be staged");
    }

    // Validate name not blank
    if (StringUtils.isBlank(dataSet.getName())) {
      throw new InvalidInputException("name", id, "DataSet name must not be blank");
    }

    // Validate description not blank
    if (StringUtils.isBlank(dataSet.getDescription())) {
      throw new InvalidInputException("description", id, "DataSet description must not be blank");
    }

    // Validate that dataset has at least one pipeline
    if (dataSet.getPipelines() == null || dataSet.getPipelines().isEmpty()) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must contain at least one Pipeline before staging");
    }

    // Each pipeline must have either datasources (feed-in) or APIs (provide)
    boolean hasDataSourceOrApi =
        dataSet.getPipelines().stream()
            .anyMatch(
                p ->
                    (p.getDataSources() != null && !p.getDataSources().isEmpty())
                        || (p.getApis() != null && !p.getApis().isEmpty()));
    if (!hasDataSourceOrApi) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must have at least one Pipeline with DataSources or APIs");
    }

    generateDistributions(dataSet);

    dataSet.setDataSetStatus(DataSetStatus.READY);
    return dataSetRepository.save(dataSet);
  }

  /**
   * Unstages a dataset, reverting it from READY to DRAFT. Removes auto-generated distributions.
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

    dataSet.getDistributions().removeIf(Distribution::getAutoGenerated);
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
      dataSet.getDistributions().removeIf(Distribution::getAutoGenerated);
      log.info("Saga DELETE completed for dataset {}, reverted to READY", datasetId);
    } else if (pendingType == PendingSagaType.CREATE) {
      applyInfrastructureResult(dataSet, result);
      boolean hasAutoGenerated =
          dataSet.getDistributions().stream().anyMatch(Distribution::getAutoGenerated);
      if (!hasAutoGenerated) {
        generateDistributions(dataSet);
      }
      updateDistributionUrls(dataSet);
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

  // Stable identifiers for drift events emitted by applyInfrastructureResult.
  private static final String DRIFT_ORPHAN_SLUGS = "orphan-slugs";
  private static final String DRIFT_UNPROVISIONED_SLUGS = "unprovisioned-slugs";
  private static final String DRIFT_REPLACED_ROUTEID = "replaced-routeid";

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
    if (result.routeIds() != null && !result.routeIds().isEmpty()) {
      Set<String> entitySlugs =
          dataSet.getNamedApis().stream().map(NamedApi::getSlug).collect(Collectors.toSet());
      Set<String> resultSlugs = result.routeIds().keySet();

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

      dataSet
          .getNamedApis()
          .forEach(
              api -> {
                String incomingRouteId = result.routeIds().get(api.getSlug());
                if (incomingRouteId == null) {
                  // Either the slug is absent from the result map (already logged as
                  // unprovisioned above) or the orchestrator sent an explicit null. In both
                  // cases, preserve the current routeId on the entity.
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

  private void generateDistributions(DataSet dataSet) {
    dataSet.getPipelines().stream()
        .filter(pipeline -> pipeline.getApis() != null)
        .flatMap(pipeline -> pipeline.getApis().stream())
        .distinct()
        .forEach(
            apiPath -> {
              Distribution distribution =
                  distributionService.createFromApiUrlAndDataSet(apiPath, dataSet);
              dataSet.getDistributions().add(distribution);
            });
  }

  private void updateDistributionUrls(DataSet dataSet) {
    if (dataSet.getPublicUrl() == null) {
      return;
    }
    String baseUrl = StringUtils.stripEnd(dataSet.getPublicUrl(), "/");
    for (Distribution dist : dataSet.getDistributions()) {
      if (Boolean.TRUE.equals(dist.getAutoGenerated())
          && dist.getAccessUrl() != null
          && !dist.getAccessUrl().startsWith(dataSet.getPublicUrl())) {
        dist.setAccessUrl(baseUrl + dist.getAccessUrl());
      }
    }
  }
}

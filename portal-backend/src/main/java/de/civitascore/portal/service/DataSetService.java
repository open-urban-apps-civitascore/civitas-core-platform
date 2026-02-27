package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class DataSetService extends BaseDataEntityService<DataSet, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;

  private final ScopedAssignmentBuilderService assignmentBuilderService;
  private final DistributionService distributionService;
  private final AssignmentRepository assignmentRepository;

  @Autowired(required = false)
  private DataSetSagaPublisher sagaPublisher;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSetMapper dataSetMapper,
      ScopedAssignmentBuilderService assignmentBuilderService,
      DistributionService distributionService,
      AssignmentRepository assignmentRepository) {
    this.dataSetRepository = dataSetRepository;
    this.dataSetMapper = dataSetMapper;
    this.assignmentBuilderService = assignmentBuilderService;
    this.distributionService = distributionService;
    this.assignmentRepository = assignmentRepository;
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
  protected ScopedAssignmentBuilderService getAssignmentBuilderService() {
    return assignmentBuilderService;
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataSet along with owner and dataSpaces in a single JOIN query, preventing N+1 query problems
   * that would occur with lazy loading.
   */
  @Override
  public Optional<DataSet> findById(UUID id) {
    Optional<DataSet> entity = dataSetRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected DataSet preSave(DataSet entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(DataSet entity) {
    dataSetRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    DataSet.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

  @Override
  protected DataSetInputDTO preProcessUpdateInput(DataSetInputDTO input, DataSet existingEntity) {
    if (StringUtils.isBlank(input.getName())) {
      throw new InvalidInputException(
          "name", existingEntity.getId(), "Name cannot be null or blank");
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Override update to ensure it can only be called for DRAFT datasets. For published datasets, use
   * updatePublishedMeta instead.
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
   * Updates only the metadata (name, description) of a published dataset. Cannot modify
   * persistenceId or pipelines. For AVAILABLE datasets with existing infrastructure, triggers a
   * saga UPDATE if no saga is currently in-flight.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws InvalidInputException if trying to update a DRAFT dataset
   */
  @Transactional
  public DataSet updatePublishedMeta(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() == DataSetStatus.DRAFT) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "This endpoint requires a published dataset (READY or AVAILABLE), current status: DRAFT");
    }

    Set<Pipeline> previousPipelines = new HashSet<>(existingEntity.getPipelines());
    DataSet updated = super.update(id, input);

    if (updated.getDataSetStatus() == DataSetStatus.AVAILABLE
        && updated.getProjectId() != null
        && updated.getPendingSagaType() == null) {
      if (sagaPublisher == null) {
        log.warn(
            "Saga infrastructure not available — skipping UPDATE saga for dataset {}. "
                + "Infrastructure changes will not be propagated.",
            id);
      } else {
        updated.setPendingSagaType(PendingSagaType.UPDATE);
        updated = dataSetRepository.save(updated);
        sagaPublisher.publishUpdateRequested(updated, previousPipelines);
      }
    }

    return updated;
  }

  /**
   * Publishes a dataset by validating it has at least one pipeline, generating distributions from
   * pipeline APIs, and setting status to READY.
   *
   * @param id the dataset ID
   * @return the published dataset
   * @throws InvalidInputException if dataset has no pipelines or is already published
   */
  @Transactional
  public DataSet publish(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    // Validate status is DRAFT
    if (dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException("dataSetStatus", id, "DataSet is already published");
    }

    // Validate name not blank
    if (StringUtils.isBlank(dataSet.getName())) {
      throw new InvalidInputException("name", id, "DataSet name must not be blank");
    }

    // Validate description not blank
    if (StringUtils.isBlank(dataSet.getDescription())) {
      throw new InvalidInputException("description", id, "DataSet description must not be blank");
    }

    // Validate that dataset has at least one pipeline with data sources
    if (dataSet.getPipelines() == null || dataSet.getPipelines().isEmpty()) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must contain at least one Pipeline before publishing");
    }

    boolean hasDataSource =
        dataSet.getPipelines().stream()
            .anyMatch(p -> p.getDataSources() != null && !p.getDataSources().isEmpty());
    if (!hasDataSource) {
      throw new InvalidInputException(
          "dataSources", id, "DataSet must have at least one DataSource across its pipelines");
    }

    // Generate distributions from pipeline APIs
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

    dataSet.setDataSetStatus(DataSetStatus.READY);
    return dataSetRepository.save(dataSet);
  }

  /**
   * Unpublishes a dataset, reverting it from READY to DRAFT. Removes auto-generated distributions.
   *
   * @param id the dataset ID
   * @return the unpublished dataset
   * @throws InvalidInputException if dataset is not in READY status
   */
  @Transactional
  public DataSet unpublish(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.READY) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be unpublished from READY status");
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
   * @throws InvalidInputException if dataset is not in READY status or saga infrastructure is
   *     unavailable
   */
  @Transactional
  public DataSet release(UUID id) {
    if (sagaPublisher == null) {
      throw new InvalidInputException(
          "dataSetStatus", id, "Saga infrastructure not available — cannot release dataset");
    }

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
   * @throws InvalidInputException if dataset is not AVAILABLE, has a saga in-flight, or saga
   *     infrastructure is unavailable
   */
  @Transactional
  public DataSet unrelease(UUID id) {
    if (sagaPublisher == null) {
      throw new InvalidInputException(
          "dataSetStatus", id, "Saga infrastructure not available — cannot unrelease dataset");
    }

    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.AVAILABLE) {
      throw new InvalidInputException(
          "dataSetStatus", id, "DataSet can only be unreleased from AVAILABLE status");
    }

    if (dataSet.getPendingSagaType() != null) {
      throw new InvalidInputException(
          "pendingSagaType",
          id,
          "Cannot unrelease while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    dataSet.setPendingSagaType(PendingSagaType.DELETE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishDeleteRequested(saved);

    return saved;
  }

  /**
   * Triggers a DATASET_DELETE saga for an AVAILABLE dataset and returns the entity in its pending
   * state, allowing the controller to return {@code 202 Accepted}. This is distinct from {@link
   * #deleteById} which only handles DRAFT deletion.
   *
   * @param id the dataset ID
   * @return the dataset with pendingSagaType=DELETE
   * @throws InvalidInputException if dataset is not AVAILABLE, has a saga in-flight, or saga
   *     infrastructure is unavailable
   */
  @Transactional
  public DataSet triggerDeleteSaga(UUID id) {
    if (sagaPublisher == null) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "Saga infrastructure not available — cannot delete AVAILABLE dataset");
    }

    DataSet dataSet = findByIdOrThrow(id);

    if (dataSet.getDataSetStatus() != DataSetStatus.AVAILABLE) {
      throw new InvalidInputException(
          "dataSetStatus", id, "triggerDeleteSaga requires AVAILABLE dataset status");
    }

    if (dataSet.getPendingSagaType() != null) {
      throw new InvalidInputException(
          "pendingSagaType",
          id,
          "Cannot delete while a saga is in-flight: " + dataSet.getPendingSagaType());
    }

    dataSet.setPendingSagaType(PendingSagaType.DELETE);
    DataSet saved = dataSetRepository.save(dataSet);

    sagaPublisher.publishDeleteRequested(saved);

    log.info(
        "Delete saga triggered for AVAILABLE dataset {}. "
            + "Actual deletion will occur after saga completes",
        id);
    return saved;
  }

  /**
   * Handles a completed saga result. Updates infrastructure fields and transitions state based on
   * the saga type that was pending.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public void handleSagaCompleted(UUID datasetId, Map<String, Object> result) {
    DataSet dataSet = findByIdOrThrow(datasetId);
    PendingSagaType pendingType = dataSet.getPendingSagaType();

    if (pendingType == PendingSagaType.DELETE) {
      dataSet.clearInfrastructureFields();
      dataSet.setDataSetStatus(DataSetStatus.READY);
      dataSet.getDistributions().removeIf(Distribution::getAutoGenerated);
      log.info("Saga DELETE completed for dataset {}, reverted to READY", datasetId);
    } else if (pendingType == PendingSagaType.CREATE) {
      applyInfrastructureResult(dataSet, result);
      updateDistributionUrls(dataSet);
      log.info("Saga CREATE completed for dataset {}, infrastructure provisioned", datasetId);
    } else if (pendingType == PendingSagaType.UPDATE) {
      applyInfrastructureResult(dataSet, result);
      log.info("Saga UPDATE completed for dataset {}", datasetId);
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /** Handles a failed saga result. Reverts state as needed based on the pending saga type. */
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
          failedStep,
          error,
          compensated);
    } else if (pendingType == PendingSagaType.UPDATE) {
      log.warn(
          "Saga UPDATE failed for dataset {}: step={}, error={}, compensated={}. Staying AVAILABLE",
          datasetId,
          failedStep,
          error,
          compensated);
    } else if (pendingType == PendingSagaType.DELETE) {
      log.warn(
          "Saga DELETE failed for dataset {}: step={}, error={}, compensated={}. "
              + "Staying AVAILABLE — stale resources may exist",
          datasetId,
          failedStep,
          error,
          compensated);
    }

    dataSet.setPendingSagaType(null);
    dataSetRepository.save(dataSet);
  }

  /**
   * Deletes a DRAFT dataset immediately. READY datasets cannot be deleted — unpublish first. For
   * AVAILABLE datasets the controller routes through {@link #triggerDeleteSaga} instead, returning
   * 202 Accepted for the asynchronous teardown.
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
          "Cannot delete a READY dataset. Unpublish it first to return to DRAFT");
    }

    // AVAILABLE: caller must use triggerDeleteSaga() via the controller DELETE endpoint
    throw new InvalidInputException(
        "dataSetStatus",
        id,
        "Cannot delete an AVAILABLE dataset synchronously. Use DELETE /datasets/{id} which triggers async infrastructure teardown");
  }

  @SuppressWarnings("unchecked")
  private void applyInfrastructureResult(DataSet dataSet, Map<String, Object> result) {
    if (result.containsKey("projectId")) {
      dataSet.setProjectId((String) result.get("projectId"));
    }
    if (result.containsKey("baseUrl")) {
      dataSet.setFrostBaseUrl((String) result.get("baseUrl"));
    }
    if (result.containsKey("routeId")) {
      dataSet.setRouteId((String) result.get("routeId"));
    }
    if (result.containsKey("serviceId")) {
      dataSet.setServiceId((String) result.get("serviceId"));
    }
    if (result.containsKey("publicUrl")) {
      dataSet.setPublicUrl((String) result.get("publicUrl"));
    }
    if (result.containsKey("pipelineIds")) {
      Object pipelineIds = result.get("pipelineIds");
      if (pipelineIds instanceof List<?> list) {
        dataSet.setPipelineIds(list.stream().map(Object::toString).toList());
      }
    }
  }

  private void updateDistributionUrls(DataSet dataSet) {
    if (dataSet.getPublicUrl() == null) {
      return;
    }
    for (Distribution dist : dataSet.getDistributions()) {
      if (Boolean.TRUE.equals(dist.getAutoGenerated()) && dist.getAccessUrl() != null) {
        dist.setAccessUrl(dataSet.getPublicUrl() + dist.getAccessUrl());
      }
    }
  }
}

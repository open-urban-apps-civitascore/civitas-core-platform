package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService extends BaseService<Dashboard, DashboardInputDTO> {

  private final DashboardRepository dashboardRepository;
  private final DashboardMapper dashboardMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSetSagaPublisher sagaPublisher;

  @Override
  protected BaseRepository<Dashboard, UUID> getRepository() {
    return dashboardRepository;
  }

  @Override
  protected DtoMapper<DashboardInputDTO, ?, Dashboard> getMapper() {
    return dashboardMapper;
  }

  @Override
  protected String getEntityName() {
    return Dashboard.class.getSimpleName();
  }

  /**
   * Guards the dataset-nested routes: a dashboard reached under a foreign {dataSetId} must read as
   * absent, not as someone else's resource.
   */
  public Dashboard findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    Dashboard dashboard = findByIdOrThrow(id);
    if (dashboard.getDataSet() == null || !dataSetId.equals(dashboard.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return dashboard;
  }

  @Override
  protected Dashboard postConvertToEntity(Dashboard entity, DashboardInputDTO input) {
    DataSet dataSet =
        Optional.ofNullable(input.getDataSetId())
            .flatMap(dataSetRepository::findById)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        DataSet.class.getSimpleName(), input.getDataSetId()));
    entity.setDataSet(dataSet);
    // active is a sync status owned by the engine-sync flow, not a delete marker (CRUD delete
    // removes the row). Seed it only on creation so updates can't reset what the sync flow set.
    if (entity.getId() == null) {
      entity.setActive(true);
    }
    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DashboardInputDTO preProcessUpdateInput(
      DashboardInputDTO input, Dashboard existingEntity) {
    if (input.getDataSetId() != null
        && existingEntity.getDataSet() != null
        && !input.getDataSetId().equals(existingEntity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), existingEntity.getId());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Dashboards are freely editable on an AVAILABLE dataset (unlike named APIs), but every change
   * has to reach the engine: create/update fire a {@code DATASET_UPDATE} saga so the new dashboard
   * set rides along to the config-adapter (see the dashboard-sync event contract). Reject changes
   * while a saga is already in-flight, so callers don't have to orchestrate request ordering.
   */
  @Override
  protected Dashboard postSave(Dashboard entity, DashboardInputDTO input) {
    triggerSyncIfReleased(entity.getDataSet());
    return super.postSave(entity, input);
  }

  @Override
  protected Dashboard preProcessDelete(UUID id) {
    Dashboard entity = super.preProcessDelete(id);
    // Reject before the row is gone, so a refused delete leaves the dashboard set intact.
    if (entity != null) {
      guardNoSagaInFlight(entity.getDataSet());
    }
    return entity;
  }

  @Override
  protected void postDelete(Dashboard entity) {
    if (entity != null) {
      triggerSyncIfReleased(entity.getDataSet());
    }
    super.postDelete(entity);
  }

  /**
   * Only a live dataset (AVAILABLE with engine infrastructure) needs syncing: DRAFT/READY datasets
   * have no engine state yet, and their dashboards ride along on the release saga instead.
   *
   * @throws ResourceInUseException if a saga is already in-flight for the dataset
   */
  private void triggerSyncIfReleased(DataSet dataSet) {
    if (dataSet == null
        || dataSet.getDataSetStatus() != DataSetStatus.AVAILABLE
        || dataSet.getProjectId() == null) {
      return;
    }
    guardNoSagaInFlight(dataSet);
    dataSet.setPendingSagaType(PendingSagaType.UPDATE);
    dataSetRepository.save(dataSet);
    sagaPublisher.publishUpdateRequested(dataSet, dataSet.getPipelines());
  }

  private void guardNoSagaInFlight(DataSet dataSet) {
    if (dataSet != null
        && dataSet.getDataSetStatus() == DataSetStatus.AVAILABLE
        && dataSet.getPendingSagaType() != null) {
      throw new ResourceInUseException(
          "DataSet",
          dataSet.getId(),
          "Cannot change dashboards while a saga is in-flight: " + dataSet.getPendingSagaType());
    }
  }
}

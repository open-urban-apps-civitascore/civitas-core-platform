package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.messaging.saga.DataSetSagaPublisher;
import de.civitascore.portal.model.embedded.DashboardFilterExpansion;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.embedded.PendingSagaType;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final ModelRegistryGateway modelRegistryGateway;

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
    // MapStruct calls the Lombok builder setter with null for this option, which replaces the
    // builder default. Restore the domain default before the model is stored.
    if (entity.getFilterExpansion() == null) {
      entity.setFilterExpansion(DashboardFilterExpansion.DEFAULT);
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
    guardNoSagaInFlight(existingEntity.getDataSet());
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
    // Keep this boundary safe even when postSave is invoked outside BaseService's normal update
    // path. In particular, do not write a new Model Forge version before rejecting a concurrent
    // change to a dataset whose engine update saga is still running.
    guardNoSagaInFlight(entity.getDataSet());
    storeModel(entity);
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
      if (entity.getModelUrn() != null) {
        modelRegistryGateway.unlinkPayloadFromDataSet(
            entity.getDataSet().getManifestLogicalUrn(), entity.getModelUrn());
        modelRegistryGateway.deletePayload(entity.getModelUrn());
      }
      triggerSyncIfReleased(entity.getDataSet(), entity.getExternalDashboardId());
    }
    super.postDelete(entity);
  }

  /**
   * Stores the dashboard's user-controlled configuration in Model Forge and keeps only its URN on
   * the relational row. The row remains the engine-id and authorization binding.
   */
  private void storeModel(Dashboard entity) {
    DataSet dataSet = entity.getDataSet();
    String manifestUrn = dataSet == null ? null : dataSet.getManifestLogicalUrn();
    if (manifestUrn == null || manifestUrn.isBlank()) {
      throw new InvalidInputException(
          "DataSet",
          dataSet == null ? null : dataSet.getId(),
          "DataSet has no Model Forge manifest");
    }
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("title", entity.getName());
    if (entity.getDescription() != null) {
      content.put("description", entity.getDescription());
    }
    content.put("released", entity.isReleased());
    content.put("standaloneMode", entity.getStandaloneMode());
    content.put("showFilters", entity.isShowFilters());
    content.put("filterExpansion", entity.getFilterExpansion().name());

    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DASHBOARD,
            Optional.ofNullable(entity.getModelUrn()),
            entity.getId(),
            entity.getName(),
            content,
            null,
            manifestUrn);
    entity.setModelUrn(pin.logicalUrn());
    dashboardRepository.save(entity);
  }

  /**
   * Only a live dataset (AVAILABLE with engine infrastructure) needs syncing: DRAFT/READY datasets
   * have no engine state yet, and their dashboards ride along on the release saga instead.
   *
   * @throws ResourceInUseException if a saga is already in-flight for the dataset
   */
  private void triggerSyncIfReleased(DataSet dataSet) {
    triggerSyncIfReleased(dataSet, null);
  }

  private void triggerSyncIfReleased(DataSet dataSet, String deletedExternalDashboardId) {
    if (dataSet == null
        || dataSet.getDataSetStatus() != DataSetStatus.AVAILABLE
        || dataSet.getProjectId() == null) {
      return;
    }
    guardNoSagaInFlight(dataSet);
    dataSet.setPendingSagaType(PendingSagaType.UPDATE);
    dataSetRepository.save(dataSet);
    if (deletedExternalDashboardId == null || deletedExternalDashboardId.isBlank()) {
      sagaPublisher.publishUpdateRequested(dataSet, dataSet.getPipelines());
    } else {
      sagaPublisher.publishUpdateRequested(
          dataSet, dataSet.getPipelines(), List.of(deletedExternalDashboardId));
    }
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

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DashboardMapper;
import de.civitascore.portal.mapper.DtoMapper;
import de.civitascore.portal.model.entity.Dashboard;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DashboardInputDTO;
import de.civitascore.portal.repository.BaseRepository;
import de.civitascore.portal.repository.DashboardRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
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
   * Defense-in-depth for the engine-sync write path: this CRUD API never populates {@code sources}
   * (the input DTO has no such field), but if a future caller does, every bound source must stay
   * anchored to this dashboard's dataset — otherwise the source would authorise access through a
   * dataset the dashboard isn't bound to.
   */
  @Override
  protected Dashboard preSave(Dashboard entity) {
    UUID dataSetId = entity.getDataSet() != null ? entity.getDataSet().getId() : null;
    entity.getSources().stream()
        .filter(source -> dataSetId == null || !dataSetId.equals(source.getMappedDatasetId()))
        .findFirst()
        .ifPresent(
            source -> {
              throw new InvalidInputException(
                  getEntityName(),
                  "sources",
                  "every bound source's mappedDatasetId must equal the dashboard's dataSetId");
            });
    return super.preSave(entity);
  }
}

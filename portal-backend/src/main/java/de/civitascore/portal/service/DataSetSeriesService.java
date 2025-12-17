package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetSeriesMapper;
import de.civitascore.portal.model.entity.DataSetSeries;
import de.civitascore.portal.model.input.DataSetSeriesInputDTO;
import de.civitascore.portal.repository.DataSetSeriesRepository;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSetSeriesService extends BaseService<DataSetSeries, DataSetSeriesInputDTO> {

  private final DataSetSeriesRepository dataSetSeriesRepository;
  private final DataSetSeriesMapper dataSetSeriesMapper;

  @Override
  protected DataSetSeriesRepository getRepository() {
    return dataSetSeriesRepository;
  }

  @Override
  protected DataSetSeriesMapper getMapper() {
    return dataSetSeriesMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSetSeries.class.getSimpleName();
  }

  @Override
  protected DataSetSeries preSave(DataSetSeries entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(DataSetSeries entity) {
    dataSetSeriesRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    DataSetSeries.class.getSimpleName(), "name", entity.getName());
              }
            });
  }
}

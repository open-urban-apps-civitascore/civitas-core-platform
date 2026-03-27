package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSetSeriesMapper;
import de.civitascore.portal.model.entity.DataSetSeries;
import de.civitascore.portal.model.input.DataSetSeriesInputDTO;
import de.civitascore.portal.repository.DataSetSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Service for managing {@link DataSetSeries} entities that group related datasets. */
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
}

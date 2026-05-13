package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSink} entities nested under a parent {@link DataSet}. Handles
 * dataset and pipeline relationship resolution and dataset-scoped access.
 */
@Slf4j
@Service
public class DataSinkService extends BaseService<DataSink, DataSinkInputDTO> {

  private final DataSinkRepository dataSinkRepository;
  private final DataSinkMapper dataSinkMapper;
  private final DataSetRepository dataSetRepository;
  private final PipelineRepository pipelineRepository;

  public DataSinkService(
      DataSinkRepository dataSinkRepository,
      DataSinkMapper dataSinkMapper,
      DataSetRepository dataSetRepository,
      PipelineRepository pipelineRepository) {
    this.dataSinkRepository = dataSinkRepository;
    this.dataSinkMapper = dataSinkMapper;
    this.dataSetRepository = dataSetRepository;
    this.pipelineRepository = pipelineRepository;
  }

  @Override
  protected DataSinkRepository getRepository() {
    return dataSinkRepository;
  }

  @Override
  protected DataSinkMapper getMapper() {
    return dataSinkMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSink.class.getSimpleName();
  }

  @Override
  public Optional<DataSink> findById(UUID id) {
    return dataSinkRepository.findByIdWithRelations(id);
  }

  /**
   * Finds a DataSink by ID and verifies that it belongs to the specified dataset.
   *
   * @param id the DataSink ID
   * @param dataSetId the expected parent dataset ID
   * @return the DataSink entity
   * @throws ResourceNotFoundException if the DataSink does not exist or belongs to a different
   *     dataset
   */
  public DataSink findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    DataSink sink = findByIdOrThrow(id);
    if (!dataSetId.equals(sink.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return sink;
  }

  /**
   * Validates that an update does not attempt to move a DataSink to a different dataset.
   *
   * @throws ResourceNotFoundException if the input specifies a different dataset ID
   */
  @Override
  protected DataSinkInputDTO preProcessUpdateInput(
      DataSinkInputDTO input, DataSink existingEntity) {
    if (input.getDataSetId() != null
        && existingEntity.getDataSet() != null
        && !input.getDataSetId().equals(existingEntity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), existingEntity.getId());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Resolves the parent dataset and pipeline references after DTO-to-entity conversion.
   *
   * @throws ResourceNotFoundException if the dataset or pipeline is not found
   */
  @Override
  protected DataSink postConvertToEntity(DataSink entity, DataSinkInputDTO input) {
    Optional.ofNullable(input.getDataSetId())
        .flatMap(dataSetRepository::findById)
        .ifPresentOrElse(
            entity::setDataSet,
            () -> {
              throw new ResourceNotFoundException(
                  DataSet.class.getSimpleName(), input.getDataSetId());
            });

    Optional.ofNullable(input.getPipelineId())
        .flatMap(pipelineRepository::findById)
        .ifPresentOrElse(
            entity::setPipeline,
            () -> {
              throw new ResourceNotFoundException(
                  Pipeline.class.getSimpleName(), input.getPipelineId());
            });

    return super.postConvertToEntity(entity, input);
  }
}

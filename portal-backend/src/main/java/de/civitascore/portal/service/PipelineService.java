package de.civitascore.portal.service;

import de.civitascore.portal.mapper.PipelineMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.PipelineInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PipelineService extends BaseService<Pipeline, PipelineInputDTO> {

  private final PipelineRepository pipelineRepository;
  private final PipelineMapper pipelineMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSourceRepository dataSourceRepository;

  public PipelineService(
      PipelineRepository pipelineRepository,
      PipelineMapper pipelineMapper,
      DataSetRepository dataSetRepository,
      DataSourceRepository dataSourceRepository) {
    this.pipelineRepository = pipelineRepository;
    this.pipelineMapper = pipelineMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataSourceRepository = dataSourceRepository;
  }

  @Override
  protected PipelineRepository getRepository() {
    return pipelineRepository;
  }

  @Override
  protected PipelineMapper getMapper() {
    return pipelineMapper;
  }

  @Override
  protected String getEntityName() {
    return Pipeline.class.getSimpleName();
  }

  @Override
  public Optional<Pipeline> findById(UUID id) {
    Optional<Pipeline> entity = pipelineRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected Pipeline postConvertToEntity(Pipeline entity, PipelineInputDTO input) {
    // Set the DataSet relationship
    Optional.ofNullable(input.getDataSetId())
        .flatMap(dataSetRepository::findById)
        .ifPresentOrElse(
            entity::setDataSet,
            () -> {
              throw new ResourceNotFoundException(
                  DataSet.class.getSimpleName(), input.getDataSetId());
            });

    // Set dataSources
    Optional.ofNullable(input.getDataSourceIds())
        .map(dataSourceRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setDataSources);

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected Pipeline preSave(Pipeline entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(Pipeline entity) {
    pipelineRepository
        .findAllByNameAndDataSetId(entity.getName(), entity.getDataSet().getId())
        .stream()
        .filter(result -> !result.getId().equals(entity.getId()))
        .findFirst()
        .ifPresent(
            existing -> {
              throw new UniqueConstraintViolationException(
                  Pipeline.class.getSimpleName(),
                  "name",
                  entity.getName(),
                  "datasetId",
                  entity.getDataSet().getId().toString());
            });
  }

  @Override
  protected Pipeline preProcessDelete(UUID id) {
    Pipeline pipeline =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (pipeline.getDataSet() != null
        && pipeline.getDataSet().getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new IllegalStateException(
          "Cannot delete pipeline associated with a dataset that is not in DRAFT status.");
    }

    return pipeline;
  }
}

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Pipeline;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSink} entities nested under a parent {@link DataSet}. Handles
 * dataset and pipeline relationship resolution, dataset-scoped access, and type-specific
 * configuration validation.
 */
@Slf4j
@Service
public class DataSinkService extends BaseService<DataSink, DataSinkInputDTO> {

  private final DataSinkRepository dataSinkRepository;
  private final DataSinkMapper dataSinkMapper;
  private final DataSetRepository dataSetRepository;
  private final PipelineRepository pipelineRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  public DataSinkService(
      DataSinkRepository dataSinkRepository,
      DataSinkMapper dataSinkMapper,
      DataSetRepository dataSetRepository,
      PipelineRepository pipelineRepository,
      DataStructureVersionRepository dataStructureVersionRepository) {
    this.dataSinkRepository = dataSinkRepository;
    this.dataSinkMapper = dataSinkMapper;
    this.dataSetRepository = dataSetRepository;
    this.pipelineRepository = pipelineRepository;
    this.dataStructureVersionRepository = dataStructureVersionRepository;
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
   * Resolves the parent dataset and pipeline, validates pipeline ownership, and validates the
   * type-specific configuration.
   *
   * @throws ResourceNotFoundException if the dataset or pipeline is not found
   * @throws InvalidInputException if the pipeline belongs to a different dataset, or the
   *     configuration is invalid for the given type
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

    Pipeline pipeline =
        Optional.ofNullable(input.getPipelineId())
            .flatMap(pipelineRepository::findById)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        Pipeline.class.getSimpleName(), input.getPipelineId()));
    entity.setPipeline(pipeline);

    if (!entity.getDataSet().getId().equals(pipeline.getDataSet().getId())) {
      throw new InvalidInputException(
          "DataSink", "pipelineId", "Pipeline does not belong to the specified DataSet");
    }

    validateConfiguration(input.getDataSinkType(), input.getConfiguration());

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Guards DELETE against references from Layer and Stil entities.
   *
   * <p>The actual repository checks are stubbed here and will be connected once the Layer and Stil
   * entities are introduced in the follow-up ticket.
   *
   * @throws ResourceNotFoundException if the DataSink does not exist
   * @throws ResourceInUseException (409) if a Layer or Stil references this DataSink
   */
  @Override
  protected DataSink preProcessDelete(UUID id) {
    DataSink sink =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    // TODO: enable when Layer entity is available
    // if (layerRepository.existsByDataSinkId(id)) {
    //   throw new ResourceInUseException(getEntityName(), id,
    //       "DataSink is referenced by one or more Layers");
    // }

    // TODO: enable when Stil entity is available
    // if (stilRepository.existsByDataSinkId(id)) {
    //   throw new ResourceInUseException(getEntityName(), id,
    //       "DataSink is referenced by one or more Stils");
    // }

    return sink;
  }

  private void validateConfiguration(DataSinkType type, Map<String, Object> config) {
    switch (type) {
      case FROST -> {
        if (config != null && !config.isEmpty()) {
          throw new InvalidInputException(
              "DataSink", "configuration", "FROST sinks require an empty configuration");
        }
      }
      case POSTGIS -> validatePostgisConfiguration(config);
    }
  }

  private void validatePostgisConfiguration(Map<String, Object> config) {
    if (config == null) {
      throw new InvalidInputException(
          "DataSink", "configuration", "POSTGIS sinks require a non-null configuration");
    }

    String tableName = (String) config.get("tableName");
    if (tableName == null || tableName.isBlank()) {
      throw new InvalidInputException(
          "DataSink", "configuration.tableName", "tableName is required for POSTGIS sinks");
    }

    Object dsvIdRaw = config.get("dataStructureVersionId");
    if (dsvIdRaw == null) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "dataStructureVersionId is required for POSTGIS sinks");
    }

    UUID dsvId;
    try {
      dsvId = UUID.fromString(dsvIdRaw.toString());
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "dataStructureVersionId must be a valid UUID");
    }

    if (!dataStructureVersionRepository.existsById(dsvId)) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "DataStructureVersion not found: " + dsvId);
    }
  }
}

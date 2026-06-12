package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSink} entities. A DataSink belongs directly to a {@link DataSet};
 * its {@link de.civitascore.portal.model.entity.Pipeline Pipeline} link is optional and managed by
 * {@link PipelineService} when a Pipeline declares the DataSink in its {@code dataSinkIds}.
 */
@Slf4j
@Service
public class DataSinkService extends BaseService<DataSink, DataSinkInputDTO> {

  private final DataSinkRepository dataSinkRepository;
  private final DataSinkMapper dataSinkMapper;
  private final DataSetRepository dataSetRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final LayerRepository layerRepository;

  public DataSinkService(
      DataSinkRepository dataSinkRepository,
      DataSinkMapper dataSinkMapper,
      DataSetRepository dataSetRepository,
      DataStructureVersionRepository dataStructureVersionRepository,
      LayerRepository layerRepository) {
    this.dataSinkRepository = dataSinkRepository;
    this.dataSinkMapper = dataSinkMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataStructureVersionRepository = dataStructureVersionRepository;
    this.layerRepository = layerRepository;
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
   * Resolves the parent dataset and validates the type-specific configuration.
   *
   * @throws ResourceNotFoundException if the dataset is not found
   * @throws InvalidInputException if the configuration is invalid for the given type
   */
  @Override
  protected DataSink postConvertToEntity(DataSink entity, DataSinkInputDTO input) {
    DataSet dataSet =
        Optional.ofNullable(input.getDataSetId())
            .flatMap(dataSetRepository::findById)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        DataSet.class.getSimpleName(), input.getDataSetId()));
    entity.setDataSet(dataSet);

    validateConfiguration(input.getDataSinkType(), input.getConfiguration());

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Detaches every DataSink belonging to the given pipeline by clearing its {@code pipeline}
   * reference. DataSinks themselves survive — they remain owned by their parent dataset and can be
   * reattached to a different pipeline later.
   *
   * @param pipelineId the pipeline whose DataSinks should be detached
   */
  public void unlinkByPipelineId(UUID pipelineId) {
    dataSinkRepository
        .findByPipelineId(pipelineId)
        .forEach(
            sink -> {
              sink.setPipeline(null);
              dataSinkRepository.save(sink);
            });
  }

  /**
   * Guards DELETE against Layer references.
   *
   * @throws ResourceNotFoundException if the DataSink does not exist
   * @throws ResourceInUseException (409) if a Layer references this DataSink
   */
  @Override
  protected DataSink preProcessDelete(UUID id) {
    DataSink sink =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (layerRepository.existsByDataSinkId(id)) {
      throw new ResourceInUseException(
          getEntityName(), id, "DataSink is referenced by one or more Layers");
    }

    return sink;
  }

  private void validateConfiguration(DataSinkType type, Map<String, Object> config) {
    switch (type) {
      case FROST -> {
        if (config != null && !config.isEmpty()) {
          throw new InvalidInputException(
              "DataSink", "configuration", "FROST sinks require an absent or empty configuration");
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

    Object tableNameRaw = config.get("tableName");
    if (!(tableNameRaw instanceof String tableName) || tableName.isBlank()) {
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

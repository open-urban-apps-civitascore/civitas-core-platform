package de.civitascore.portal.service;

import de.civitascore.portal.mapper.LayerMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.Layer;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.LayerInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Layer} entities nested under a parent {@link DataSet}. Handles
 * dataset-scoped access and relationship resolution for DataSink, default Style, and alternative
 * Styles.
 */
@Slf4j
@Service
public class LayerService extends BaseService<Layer, LayerInputDTO> {

  private final LayerRepository layerRepository;
  private final LayerMapper layerMapper;
  private final DataSetRepository dataSetRepository;
  private final DataSinkRepository dataSinkRepository;
  private final StyleRepository styleRepository;

  public LayerService(
      LayerRepository layerRepository,
      LayerMapper layerMapper,
      DataSetRepository dataSetRepository,
      DataSinkRepository dataSinkRepository,
      StyleRepository styleRepository) {
    this.layerRepository = layerRepository;
    this.layerMapper = layerMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataSinkRepository = dataSinkRepository;
    this.styleRepository = styleRepository;
  }

  @Override
  protected LayerRepository getRepository() {
    return layerRepository;
  }

  @Override
  protected LayerMapper getMapper() {
    return layerMapper;
  }

  @Override
  protected String getEntityName() {
    return Layer.class.getSimpleName();
  }

  /**
   * Finds a Layer by ID and verifies that it belongs to the specified dataset.
   *
   * @throws ResourceNotFoundException if the Layer does not exist or belongs to a different dataset
   */
  public Layer findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    Layer layer = findByIdOrThrow(id);
    if (!dataSetId.equals(layer.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return layer;
  }

  /**
   * Rejects saves that would create a duplicate layerName within the same dataSink.
   *
   * @throws UniqueConstraintViolationException (409) if another Layer with the same layerName
   *     exists in the dataSink
   */
  @Override
  protected Layer preSave(Layer entity) {
    layerRepository
        .findByDataSinkIdAndLayerName(entity.getDataSink().getId(), entity.getLayerName())
        .filter(existing -> !existing.getId().equals(entity.getId()))
        .ifPresent(
            _ -> {
              throw new UniqueConstraintViolationException(
                  getEntityName(),
                  "layerName",
                  entity.getLayerName(),
                  "dataSinkId",
                  entity.getDataSink().getId().toString());
            });
    return super.preSave(entity);
  }

  /** Rejects bbox fields when bboxAutoCalculate is true. */
  @Override
  protected LayerInputDTO preProcessCreateInput(LayerInputDTO input) {
    validateBboxConsistency(input);
    return super.preProcessCreateInput(input);
  }

  /** Validates that an update does not attempt to move a Layer to a different dataset. */
  @Override
  protected LayerInputDTO preProcessUpdateInput(LayerInputDTO input, Layer existingEntity) {
    validateBboxConsistency(input);
    if (input.getDataSetId() != null
        && existingEntity.getDataSet() != null
        && !input.getDataSetId().equals(existingEntity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), existingEntity.getId());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  private void validateBboxConsistency(LayerInputDTO input) {
    if (input.isBboxAutoCalculate()
        && (input.getNativeBoundingBox() != null || input.getLatLonBoundingBox() != null)) {
      throw new InvalidInputException(
          getEntityName(),
          "bboxAutoCalculate",
          "nativeBoundingBox and latLonBoundingBox must be null when bboxAutoCalculate is true");
    }
    if (!input.isBboxAutoCalculate()
        && (input.getNativeBoundingBox() == null || input.getLatLonBoundingBox() == null)) {
      throw new InvalidInputException(
          getEntityName(),
          "bboxAutoCalculate",
          "nativeBoundingBox and latLonBoundingBox are both required when bboxAutoCalculate is false");
    }
  }

  /**
   * Resolves the parent dataset, DataSink, default Style, and alternative Styles from the input
   * DTO.
   *
   * @throws ResourceNotFoundException if the dataset, DataSink, or any referenced Style is not
   *     found
   */
  @Override
  protected Layer postConvertToEntity(Layer entity, LayerInputDTO input) {
    Optional.ofNullable(input.getDataSetId())
        .flatMap(dataSetRepository::findById)
        .ifPresentOrElse(
            entity::setDataSet,
            () -> {
              throw new ResourceNotFoundException(
                  DataSet.class.getSimpleName(), input.getDataSetId());
            });

    DataSink dataSink =
        Optional.ofNullable(input.getDataSinkId())
            .flatMap(dataSinkRepository::findById)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        DataSink.class.getSimpleName(), input.getDataSinkId()));
    entity.setDataSink(dataSink);

    if (input.getDefaultStyleId() != null) {
      Style defaultStyle =
          styleRepository
              .findById(input.getDefaultStyleId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          Style.class.getSimpleName(), input.getDefaultStyleId()));
      entity.setDefaultStyle(defaultStyle);
    } else {
      entity.setDefaultStyle(null);
    }

    entity.setAlternativeStyles(resolveAlternativeStyles(input.getAlternativeStyleIds()));

    validateStyleOwnership(entity);

    return super.postConvertToEntity(entity, input);
  }

  private void validateStyleOwnership(Layer entity) {
    UUID dataSetId = entity.getDataSet().getId();

    if (entity.getDefaultStyle() != null
        && !dataSetId.equals(entity.getDefaultStyle().getDataSet().getId())) {
      throw new InvalidInputException(
          getEntityName(),
          "defaultStyleId",
          "defaultStyle must belong to the same dataset as the Layer");
    }

    entity.getAlternativeStyles().stream()
        .filter(style -> !dataSetId.equals(style.getDataSet().getId()))
        .findFirst()
        .ifPresent(
            _ -> {
              throw new InvalidInputException(
                  getEntityName(),
                  "alternativeStyleIds",
                  "all alternativeStyles must belong to the same dataset as the Layer");
            });
  }

  private Set<Style> resolveAlternativeStyles(List<UUID> styleIds) {
    if (styleIds == null || styleIds.isEmpty()) {
      return new HashSet<>();
    }

    List<Style> found = styleRepository.findAllById(styleIds);

    Set<UUID> foundIds = found.stream().map(Style::getId).collect(Collectors.toSet());
    Set<UUID> missingIds =
        styleIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());

    if (!missingIds.isEmpty()) {
      throw new ResourceNotFoundException(
          Style.class.getSimpleName(), missingIds.iterator().next());
    }

    return new HashSet<>(found);
  }
}

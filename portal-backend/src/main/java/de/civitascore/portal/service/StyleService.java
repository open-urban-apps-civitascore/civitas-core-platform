package de.civitascore.portal.service;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Style} entities nested under a parent {@link DataSet}. Handles
 * dataset-scoped access and delete protection when a Layer references the Style as its default.
 */
@Slf4j
@Service
public class StyleService extends BaseService<Style, StyleInputDTO> {

  private final StyleRepository styleRepository;
  private final StyleMapper styleMapper;
  private final DataSetRepository dataSetRepository;
  private final LayerRepository layerRepository;

  public StyleService(
      StyleRepository styleRepository,
      StyleMapper styleMapper,
      DataSetRepository dataSetRepository,
      LayerRepository layerRepository) {
    this.styleRepository = styleRepository;
    this.styleMapper = styleMapper;
    this.dataSetRepository = dataSetRepository;
    this.layerRepository = layerRepository;
  }

  @Override
  protected StyleRepository getRepository() {
    return styleRepository;
  }

  @Override
  protected StyleMapper getMapper() {
    return styleMapper;
  }

  @Override
  protected String getEntityName() {
    return Style.class.getSimpleName();
  }

  /**
   * Finds a Style by ID and verifies that it belongs to the specified dataset.
   *
   * @throws ResourceNotFoundException if the Style does not exist or belongs to a different dataset
   */
  public Style findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    Style style = findByIdOrThrow(id);
    if (!dataSetId.equals(style.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return style;
  }

  /**
   * Rejects saves that would create a duplicate name within the same dataset.
   *
   * @throws UniqueConstraintViolationException (409) if another Style with the same name exists in
   *     the dataset
   */
  @Override
  protected Style preSave(Style entity) {
    styleRepository
        .findByDataSetIdAndName(entity.getDataSet().getId(), entity.getName())
        .filter(existing -> !existing.getId().equals(entity.getId()))
        .ifPresent(
            _ -> {
              throw new UniqueConstraintViolationException(
                  Style.class.getSimpleName(),
                  "name",
                  entity.getName(),
                  "datasetId",
                  entity.getDataSet().getId().toString());
            });
    return super.preSave(entity);
  }

  /** Validates that an update does not attempt to move a Style to a different dataset. */
  @Override
  protected StyleInputDTO preProcessUpdateInput(StyleInputDTO input, Style existingEntity) {
    if (input.getDataSetId() != null
        && existingEntity.getDataSet() != null
        && !input.getDataSetId().equals(existingEntity.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), existingEntity.getId());
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /** Resolves the parent dataset from the input DTO. */
  @Override
  protected Style postConvertToEntity(Style entity, StyleInputDTO input) {
    Optional.ofNullable(input.getDataSetId())
        .flatMap(dataSetRepository::findById)
        .ifPresentOrElse(
            entity::setDataSet,
            () -> {
              throw new ResourceNotFoundException(
                  DataSet.class.getSimpleName(), input.getDataSetId());
            });

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Guards DELETE against Layers that reference this Style as their default.
   *
   * @throws ResourceNotFoundException if the Style does not exist
   * @throws ResourceInUseException (409) if a Layer references this Style as its default Style
   */
  @Override
  protected Style preProcessDelete(UUID id) {
    Style style =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (layerRepository.existsByDefaultStyleId(id)) {
      throw new ResourceInUseException(
          getEntityName(), id, "Style is referenced by one or more Layers as the default Style");
    }

    return style;
  }
}

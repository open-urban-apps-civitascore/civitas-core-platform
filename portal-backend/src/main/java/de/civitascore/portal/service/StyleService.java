package de.civitascore.portal.service;

import de.civitascore.portal.mapper.StyleMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Style;
import de.civitascore.portal.model.input.StyleInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.repository.StyleRepository;
import de.civitascore.portal.util.InvalidInputException;
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
public class StyleService extends DataSetOwnedService<Style, StyleInputDTO> {

  private final StyleRepository styleRepository;
  private final StyleMapper styleMapper;
  private final DataSetRepository dataSetRepository;
  private final LayerRepository layerRepository;
  private final SldContentValidator sldContentValidator;

  public StyleService(
      StyleRepository styleRepository,
      StyleMapper styleMapper,
      DataSetRepository dataSetRepository,
      LayerRepository layerRepository,
      SldContentValidator sldContentValidator) {
    this.styleRepository = styleRepository;
    this.styleMapper = styleMapper;
    this.dataSetRepository = dataSetRepository;
    this.layerRepository = layerRepository;
    this.sldContentValidator = sldContentValidator;
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
   * Rejects saves that would create a duplicate name within the same dataset, or carry SLD content
   * GeoServer would refuse while publishing.
   *
   * @throws InvalidInputException (400) if the SLD declares a DOCTYPE or is not well-formed XML
   * @throws UniqueConstraintViolationException (409) if another Style with the same name exists in
   *     the dataset
   */
  @Override
  protected Style preSave(Style entity) {
    sldContentValidator.validate(entity.getSldContent());

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
   * @throws ResourceInUseException (409) if a Layer references this Style
   */
  @Override
  protected void onDelete(Style style) {
    UUID id = style.getId();
    if (layerRepository.existsByDefaultStyleIdOrAlternativeStylesId(id, id)) {
      throw new ResourceInUseException(
          getEntityName(), id, "Style is referenced by one or more Layers");
    }
  }
}

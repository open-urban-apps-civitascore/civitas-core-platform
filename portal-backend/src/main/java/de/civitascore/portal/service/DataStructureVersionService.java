package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final DataStructureVersionMapper dataStructureVersionMapper;

  private final DataStructureService dataStructureService;
  private final ModelService modelService;

  @Override
  protected DataStructureVersionRepository getRepository() {
    return dataStructureVersionRepository;
  }

  @Override
  protected DataStructureVersionMapper getMapper() {
    return dataStructureVersionMapper;
  }

  @Override
  protected String getEntityName() {
    return DataStructureVersion.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataStructureVersion along with dataStructure in a single JOIN query, preventing N+1 query
   * problems that would occur with lazy loading.
   */
  @Override
  public Optional<DataStructureVersion> findById(UUID id) {
    Optional<DataStructureVersion> entity =
        dataStructureVersionRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  public Optional<String> findModelForDataStructureVersion(DataStructureVersion entity) {
    if (StringUtils.isNotBlank(entity.getModelAtlasUri())) {
      try {
        String modelContent =
            modelService.downloadModel(entity.getModelAtlasUri(), "application/xml");
        return Optional.ofNullable(modelContent);
      } catch (Exception e) {
        // error has already been logged in ModelRestClientRequestService, so just return empty here
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  @Override
  protected DataStructureVersion postConvertToEntity(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    // Set dataStructure
    Optional.ofNullable(input.getDataStructureId())
        .map(dataStructureService::findByIdOrThrow)
        .ifPresentOrElse(
            entity::setDataStructure,
            () -> {
              throw new InvalidInputException(
                  "dataStructureId", entity.getId(), "dataStructureId cannot be null or blank");
            });

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataStructureVersionInputDTO preProcessCreateInput(DataStructureVersionInputDTO input) {
    // Set DRAFT status for newly created data structure versions
    input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    validateModelAndAtlasUri(input);

    return super.preProcessCreateInput(input);
  }

  @Override
  protected DataStructureVersionInputDTO preProcessUpdateInput(
      DataStructureVersionInputDTO input, DataStructureVersion existingEntity) {
    try {
      if (StringUtils.isBlank(input.getVersion())) {
        throw new InvalidInputException(
            "version", existingEntity.getId(), "Version cannot be null or blank");
      }

      // Prevent changes to modelAtlasUri if status is not DRAFT
      if (existingEntity.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT) {
        input.setModelAtlasUri(existingEntity.getModelAtlasUri());
        input.setVersion(existingEntity.getVersion());
        input.setStyles(new HashMap<>(existingEntity.getStyles()));

        // Also clear the model to prevent uploads for non-DRAFT versions
        input.setModel(null);
      } else {
        validateModelAndAtlasUri(input);
      }

    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  @Override
  protected DataStructureVersion postSave(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    if (StringUtils.isNotBlank(input.getModel())
        && StringUtils.isNotBlank(input.getModelAtlasUri())) {
      try {
        modelService.uploadModelString(input.getModel(), input.getModelAtlasUri());
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to upload model to Model Atlas for modelAtlasUri: " + input.getModelAtlasUri(),
            e);
      }
    }

    return super.postSave(entity, input);
  }

  private void validateModelAndAtlasUri(DataStructureVersionInputDTO input) {
    boolean hasModel = StringUtils.isNotBlank(input.getModel());
    boolean hasModelAtlasUri = StringUtils.isNotBlank(input.getModelAtlasUri());

    if (hasModel && !hasModelAtlasUri) {
      throw new InvalidInputException(
          "DataStructureVersion",
          "modelAtlasUri",
          "modelAtlasUri cannot be null or blank if model is provided");
    }

    if (!hasModel && hasModelAtlasUri) {
      throw new InvalidInputException(
          "DataStructureVersion",
          "model",
          "model cannot be null or blank if modelAtlasUri is provided");
    }
  }

  /**
   * Override update to ensure it can only be called for DRAFT versions. For published versions, use
   * updatePublishedMeta instead.
   *
   * @param id the version ID
   * @param input the update input
   * @return the updated version
   * @throws InvalidInputException if trying to update a non-DRAFT version
   */
  @Override
  public DataStructureVersion update(UUID id, DataStructureVersionInputDTO input) {
    DataStructureVersion existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "Cannot update non-DRAFT DataStructureVersion.");
    }
    return super.update(id, input);
  }

  /**
   * Updates only the metadata (version, modelName, styles) of a published data structure version.
   * Cannot modify modelAtlasUri or model.
   *
   * @param id the version ID
   * @param input the update input
   * @return the updated version
   * @throws InvalidInputException if trying to update a DRAFT version
   */
  @Transactional
  public DataStructureVersion updatePublishedMeta(UUID id, DataStructureVersionInputDTO input) {
    DataStructureVersion existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus",
          id,
          "Cannot use updatePublishedMeta for DRAFT DataStructureVersion.");
    }

    return super.update(id, input);
  }

  /**
   * Publishes a data structure version by validating it has a modelAtlasUri and setting status to
   * AVAILABLE.
   *
   * @param id the version ID
   * @return the published version
   * @throws InvalidInputException if version has no modelAtlasUri or is already published
   */
  @Transactional
  public DataStructureVersion publish(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently in DRAFT status
    if (version.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already published");
    }

    // Validate that version has a modelAtlasUri
    if (StringUtils.isBlank(version.getModelAtlasUri())) {
      throw new InvalidInputException(
          "modelAtlasUri",
          id,
          "DataStructureVersion must contain a modelAtlasUri before publishing");
    }

    version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Unpublishes a data structure version by setting status back to DRAFT. Validates that
   * unpublishing won't leave a published DataStructure without any published versions.
   *
   * @param id the version ID
   * @return the unpublished version
   * @throws InvalidInputException if version is already DRAFT or if unpublishing would leave a
   *     published DataStructure without published versions
   */
  @Transactional
  public DataStructureVersion unpublish(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently published
    if (version.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already in DRAFT status");
    }

    // Check if parent DataStructure is published (any status other than DRAFT is considered
    // published)
    if (version.getDataStructure().getDataStructureStatus() != DataStructureStatus.DRAFT) {

      // Count how many published versions this DataStructure has (excluding the current one)
      // Any version not in DRAFT status is considered published
      long publishedVersionCount =
          version.getDataStructure().getDataStructureVersions().stream()
              .filter(v -> !v.getId().equals(id))
              .filter(v -> v.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT)
              .count();

      if (publishedVersionCount == 0) {
        throw new InvalidInputException(
            "dataStructureVersionStatus",
            id,
            "Cannot unpublish this DataStructureVersion because it is the only published version of a published DataStructure. Please unpublish the DataStructure first.");
      }
    }

    version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    return dataStructureVersionRepository.save(version);
  }
}

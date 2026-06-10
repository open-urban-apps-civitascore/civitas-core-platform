package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.ExternalSystemRejectionException;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Service for managing {@link DataStructureVersion} entities through their lifecycle (DRAFT to
 * AVAILABLE). Handles model file synchronization with Model Atlas, unique version validation within
 * a data structure, and enforces constraints on versions that are in use by data sources.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private static final String MODEL_ATLAS_OBJECT_ID_FIELD = "objectId";
  private static final TypeReference<Map<String, Object>> MODEL_JSON_TYPE =
      new TypeReference<>() {};

  private final DataSourceRepository dataSourceRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  private final DataStructureService dataStructureService;
  private final ModelService modelService;

  private final DataStructureVersionMapper dataStructureVersionMapper;
  private final ObjectMapper objectMapper;

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

  /** Downloads model content from Model Atlas by its URI. Empty if the URI is blank. */
  private Optional<String> download(String modelAtlasUri, String acceptType) {
    return StringUtils.isBlank(modelAtlasUri)
        ? Optional.empty()
        : Optional.ofNullable(modelService.downloadModel(modelAtlasUri, acceptType));
  }

  /**
   * Downloads the raw XMI model from Model Atlas. Lenient: swallows failures and returns empty
   * (used as a retrievability check), so callers cannot distinguish "no model" from "Model Atlas
   * down".
   *
   * @param modelAtlasUri the Model Atlas namespace URI
   * @return the model XML content, or empty if unavailable
   */
  public Optional<String> findModelByAtlasUri(String modelAtlasUri) {
    try {
      return download(modelAtlasUri, MediaType.APPLICATION_XML_VALUE);
    } catch (RuntimeException e) {
      // error has already been logged in ModelRestClientRequestService, so just return empty here
      return Optional.empty();
    }
  }

  /**
   * Fetches the data-structure model from Model Atlas as parsed JSON. Unlike {@link
   * #findModelByAtlasUri} (raw XMI), this does not swallow failures: an outage propagates as a
   * 504/502 external-system exception, unparseable content as a 502. Empty only when the URI is
   * blank or Model Atlas returns no content.
   *
   * @param modelAtlasUri the Model Atlas namespace URI
   * @return the parsed model JSON, or empty if the URI is blank or no content exists
   */
  public Optional<Map<String, Object>> resolveModelJsonByAtlasUri(String modelAtlasUri) {
    return download(modelAtlasUri, MediaType.APPLICATION_JSON_VALUE)
        .filter(json -> !json.isBlank())
        .map(json -> parseModelJson(json, modelAtlasUri));
  }

  private Map<String, Object> parseModelJson(String json, String modelAtlasUri) {
    try {
      return objectMapper.readValue(json, MODEL_JSON_TYPE);
    } catch (JacksonException e) {
      throw new ExternalSystemRejectionException(
          "Model Atlas returned unparseable model JSON for " + modelAtlasUri, e);
    }
  }

  /**
   * Resolves and sets the parent data structure relationship after DTO-to-entity conversion.
   *
   * @param entity the data structure version entity
   * @param input the input DTO containing the data structure ID
   * @return the entity with the parent data structure set
   * @throws InvalidInputException if the data structure ID is null or the data structure is not
   *     found
   */
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

  /**
   * Sets initial DRAFT status and validates model/modelAtlasUri consistency before creating a new
   * data structure version.
   *
   * @param input the creation input
   * @return the preprocessed input with DRAFT status set
   * @throws InvalidInputException if model is provided without modelAtlasUri or vice versa
   */
  @Override
  protected DataStructureVersionInputDTO preProcessCreateInput(DataStructureVersionInputDTO input) {
    // Set DRAFT status for newly created data structure versions
    input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    validateModelAndAtlasUri(input);

    return super.preProcessCreateInput(input);
  }

  /**
   * Validates and constrains update input based on the version's current state. If the version is
   * in use by a data source, structural fields (modelAtlasUri, version, styles, model) are locked
   * and only description and modelName may change.
   *
   * @param input the update input
   * @param existingEntity the current version entity
   * @return the preprocessed input with restricted fields preserved if in use
   * @throws InvalidInputException if the version string is blank
   */
  @Override
  protected DataStructureVersionInputDTO preProcessUpdateInput(
      DataStructureVersionInputDTO input, DataStructureVersion existingEntity) {
    try {
      if (StringUtils.isBlank(input.getVersion())) {
        throw new InvalidInputException(
            "version", existingEntity.getId(), "Version cannot be null or blank");
      }

      if (existingEntity.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT
          && dataSourceRepository.existsByDataStructureVersionId(existingEntity.getId())) {
        // Version is in use: block all structural changes, allow only description and modelName
        input.setModelAtlasUri(existingEntity.getModelAtlasUri());
        input.setVersion(existingEntity.getVersion());
        input.setStyles(
            existingEntity.getStyles() != null
                ? new HashMap<>(existingEntity.getStyles())
                : new HashMap<>());
        input.setModel(null);
      } else {
        validateModelForUpdate(input);
      }

    } catch (InvalidInputException e) {
      throw e;
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Validates version uniqueness within the parent data structure before saving.
   *
   * @param entity the data structure version entity
   * @return the validated entity
   * @throws UniqueConstraintViolationException if another version with the same version string
   *     exists in the same data structure
   */
  @Override
  protected DataStructureVersion preSave(DataStructureVersion entity) {
    validateUniqueVersion(entity);
    return super.preSave(entity);
  }

  /**
   * Uploads the model content to Model Atlas after persisting the entity and stores the returned
   * external ID.
   *
   * @param entity the saved data structure version entity
   * @param input the input DTO containing model content and atlas URI
   * @return the entity, potentially updated with an external ID from Model Atlas
   * @throws ExternalSystemRejectionException if the Model Atlas upload fails
   */
  @Override
  protected DataStructureVersion postSave(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    if (StringUtils.isNotBlank(input.getModel())
        && StringUtils.isNotBlank(input.getModelAtlasUri())) {
      try {
        String response =
            modelService.uploadModelString(input.getModel(), input.getModelAtlasUri());
        parseAndSetExternalId(entity, response);
      } catch (ExternalSystemRejectionException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new ExternalSystemRejectionException("Failed to upload model to Model Atlas", e);
      }
    }

    return super.postSave(entity, input);
  }

  /**
   * Deletes the associated model from Model Atlas after the version entity has been removed from
   * the database. Failures are logged as warnings but do not propagate.
   *
   * @param entity the deleted data structure version entity
   */
  @Override
  protected void postDelete(DataStructureVersion entity) {
    if (entity != null && StringUtils.isNotBlank(entity.getModelAtlasUri())) {
      try {
        modelService.deleteModel(entity.getModelAtlasUri());
      } catch (RuntimeException e) {
        log.warn(
            "Failed to delete model from Model Atlas for modelAtlasUri: {}",
            Encode.forJava(entity.getModelAtlasUri()),
            e);
      }
    }
  }

  private void deleteOldModelIfUriChanged(String oldUri, DataStructureVersionInputDTO input) {
    String newUri = input.getModelAtlasUri();
    if (StringUtils.isNotBlank(oldUri) && !Objects.equals(oldUri, newUri)) {
      try {
        modelService.deleteModel(oldUri);
      } catch (RuntimeException e) {
        log.warn(
            "Failed to delete old model from Model Atlas for modelAtlasUri: {}",
            Encode.forJava(oldUri),
            e);
      }
    }
  }

  // externalId is non-critical metadata — modelAtlasUri is the authoritative reference for
  // fetching models. If parsing fails, the model is already uploaded and accessible via
  // modelAtlasUri; only the internal Atlas object reference is missing. This will become
  // relevant once direct Model Atlas PUT calls replace the current upload workaround.
  private void parseAndSetExternalId(DataStructureVersion entity, String uploadResponse) {
    try {
      JsonNode root = objectMapper.readTree(uploadResponse);
      if (root == null) {
        log.warn("Model Atlas upload response was null or empty");
        return;
      }
      JsonNode objectIdNode = root.get(MODEL_ATLAS_OBJECT_ID_FIELD);
      if (objectIdNode != null && !objectIdNode.isNull()) {
        entity.setExternalId(objectIdNode.asString());
        dataStructureVersionRepository.save(entity);
      }
    } catch (JacksonException e) {
      log.warn("Failed to parse externalId from Model Atlas upload response", e);
    }
  }

  private void validateUniqueVersion(DataStructureVersion entity) {
    dataStructureVersionRepository
        .findAllByDataStructureIdAndVersion(entity.getDataStructure().getId(), entity.getVersion())
        .stream()
        .filter(existing -> !existing.getId().equals(entity.getId()))
        .findAny()
        .ifPresent(
            existing -> {
              throw new UniqueConstraintViolationException(
                  DataStructureVersion.class.getSimpleName(),
                  "version",
                  "Version must be unique within the same DataStructure. Another version with the same version already exists: "
                      + existing.getId());
            });
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

  private void validateModelForUpdate(DataStructureVersionInputDTO input) {
    boolean hasModel = StringUtils.isNotBlank(input.getModel());
    boolean hasModelAtlasUri = StringUtils.isNotBlank(input.getModelAtlasUri());

    if (hasModel && !hasModelAtlasUri) {
      throw new InvalidInputException(
          "DataStructureVersion",
          "modelAtlasUri",
          "modelAtlasUri cannot be null or blank if model is provided");
    }
  }

  /**
   * Override update to ensure it can only be called for DRAFT versions. For released versions, use
   * updateReleasedMeta instead.
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
    String oldModelAtlasUri = existingEntity.getModelAtlasUri();
    DataStructureVersion result = super.update(id, input);
    deleteOldModelIfUriChanged(oldModelAtlasUri, input);
    return result;
  }

  /**
   * Updates a released data structure version. If the version is not in use by any DataSource, all
   * fields (model, modelAtlasUri, version, styles, modelName, description) can be updated. If the
   * version is in use, only description and modelName can be changed.
   *
   * @param id the version ID
   * @param input the update input
   * @return the updated version
   * @throws InvalidInputException if trying to update a DRAFT version
   * @throws ResourceInUseException if trying to update restricted fields on an in-use version
   */
  @Transactional
  public DataStructureVersion updateReleasedMeta(UUID id, DataStructureVersionInputDTO input) {
    DataStructureVersion existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus",
          id,
          "Cannot update released metadata for a DRAFT DataStructureVersion.");
    }

    String oldModelAtlasUri = existingEntity.getModelAtlasUri();
    DataStructureVersion result = super.update(id, input);
    deleteOldModelIfUriChanged(oldModelAtlasUri, input);
    return result;
  }

  /**
   * Releases a data structure version by validating it has a modelAtlasUri and setting status to
   * AVAILABLE.
   *
   * @param id the version ID
   * @return the released version
   * @throws InvalidInputException if version has no modelAtlasUri or is already released
   */
  @Transactional
  public DataStructureVersion release(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently in DRAFT status
    if (version.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already released");
    }

    // Validate that version has a modelAtlasUri
    if (StringUtils.isBlank(version.getModelAtlasUri())) {
      throw new InvalidInputException(
          "modelAtlasUri",
          id,
          "DataStructureVersion must contain a modelAtlasUri before releasing");
    }

    // Validate that the model is actually retrievable from Model Atlas
    if (findModelByAtlasUri(version.getModelAtlasUri()).isEmpty()) {
      throw new InvalidInputException(
          "modelAtlasUri",
          id,
          "Cannot release: no model found in Model Atlas for modelAtlasUri "
              + version.getModelAtlasUri());
    }

    version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Unreleases a data structure version by setting status back to DRAFT. Validates that unreleasing
   * won't leave a released DataStructure without any released versions.
   *
   * @param id the version ID
   * @return the unreleased version
   * @throws InvalidInputException if version is already DRAFT or if unreleasing would leave a
   *     released DataStructure without released versions
   */
  @Transactional
  public DataStructureVersion unrelease(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently released
    if (version.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already in DRAFT status");
    }

    validateNotInUse(id);
    validateExistenceOfOtherReleasedVersion(
        version,
        "Cannot unrelease this DataStructureVersion because it is the only released version of a released DataStructure. Please unrelease the DataStructure first.");

    version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Validates that the version is not in use by any data source and that deleting it would not
   * leave a released data structure without any released versions.
   *
   * @param id the version ID to delete
   * @return the version entity to be deleted
   * @throws ResourceInUseException if the version is referenced by a data source
   * @throws InvalidInputException if the version is the only released version of a released data
   *     structure
   */
  @Override
  protected DataStructureVersion preProcessDelete(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);
    validateNotInUse(id);
    validateExistenceOfOtherReleasedVersion(
        version,
        "Cannot delete this DataStructureVersion because it is the only released version of a released DataStructure. Please unrelease the DataStructure first.");

    return version;
  }

  private void validateNotInUse(UUID versionId) {
    if (dataSourceRepository.existsByDataStructureVersionId(versionId)) {
      throw new ResourceInUseException(
          "DataStructureVersion",
          versionId,
          "Cannot modify DataStructureVersion because it is referenced by one or more DataSources.");
    }
  }

  private void validateExistenceOfOtherReleasedVersion(
      DataStructureVersion version, String errorMessage) {
    DataStructure dataStructure = version.getDataStructure();
    if (dataStructure.getDataStructureStatus() == DataStructureStatus.DRAFT) {
      return;
    }

    boolean hasOtherReleasedVersions =
        dataStructure.getDataStructureVersions().stream()
            .filter(v -> !v.getId().equals(version.getId()))
            .anyMatch(v -> v.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT);

    if (!hasOtherReleasedVersions) {
      throw new InvalidInputException("dataStructureVersionStatus", version.getId(), errorMessage);
    }
  }
}

package de.civitascore.portal.service;

import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataStructureVersion} entities through their lifecycle (DRAFT to
 * AVAILABLE). Persists the version's JSON Schema, enforces unique version strings within a data
 * structure, and constrains versions that are in use — a version is in use when a data source or a
 * data sink references it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private final DataSourceRepository dataSourceRepository;
  private final DataSinkRepository dataSinkRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  private final DataStructureService dataStructureService;

  private final DataStructureVersionMapper dataStructureVersionMapper;

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
   * Sets initial DRAFT status before creating a new data structure version.
   *
   * @param input the creation input
   * @return the preprocessed input with DRAFT status set
   */
  @Override
  protected DataStructureVersionInputDTO preProcessCreateInput(DataStructureVersionInputDTO input) {
    // Set DRAFT status for newly created data structure versions
    input.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);

    return super.preProcessCreateInput(input);
  }

  /**
   * Validates and constrains update input based on the version's current state. If the version is
   * in use by a data source or a data sink, structural fields (model, version, styles) are locked
   * and only description and modelName may change. A released version that is not in use may have
   * its model replaced but never cleared — it must always retain a non-empty model.
   *
   * @param input the update input
   * @param existingEntity the current version entity
   * @return the preprocessed input with restricted fields preserved if in use
   * @throws InvalidInputException if the version string is blank, or if an update to a released
   *     version would clear its model
   */
  @Override
  protected DataStructureVersionInputDTO preProcessUpdateInput(
      DataStructureVersionInputDTO input, DataStructureVersion existingEntity) {
    if (StringUtils.isBlank(input.getVersion())) {
      throw new InvalidInputException(
          "version", existingEntity.getId(), "Version cannot be null or blank");
    }

    boolean isReleased =
        existingEntity.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT;

    if (isReleased && isInUse(existingEntity.getId())) {
      // Version is in use: block all structural changes, allow only description and modelName.
      // Copy the maps so the update mapper does not clear the managed entity's own collections
      // (MapStruct clears + putAll on the target map; sharing the reference would empty it).
      input.setModel(
          existingEntity.getModel() != null ? new HashMap<>(existingEntity.getModel()) : null);
      input.setVersion(existingEntity.getVersion());
      input.setStyles(
          existingEntity.getStyles() != null
              ? new HashMap<>(existingEntity.getStyles())
              : new HashMap<>());
    } else if (isReleased && (input.getModel() == null || input.getModel().isEmpty())) {
      // Released but not in use: the model may be replaced, but never cleared — a released
      // version must always retain a non-empty model.
      throw new InvalidInputException(
          "model",
          existingEntity.getId(),
          "Cannot clear the model of a released DataStructureVersion");
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
    return super.update(id, input);
  }

  /**
   * Updates a released data structure version. If the version is not in use by any DataSource, all
   * fields (model, version, styles, modelName, description) can be updated. If the version is in
   * use, only description and modelName can be changed.
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

    return super.update(id, input);
  }

  /**
   * Releases a data structure version by validating it has a model (JSON schema) and setting status
   * to AVAILABLE.
   *
   * @param id the version ID
   * @return the released version
   * @throws InvalidInputException if the version has no model or is already released
   */
  @Transactional
  public DataStructureVersion release(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently in DRAFT status
    if (version.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already released");
    }

    // Validate that the version carries a model (JSON schema)
    if (version.getModel() == null || version.getModel().isEmpty()) {
      throw new InvalidInputException(
          "model", id, "DataStructureVersion must contain a model before releasing");
    }

    validateModelId(version);

    version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Validates the model's {@code $id} — the DataStructure's stable identity and JSON-Schema {@code
   * $ref} target — is a well-formed CORE URN whose disambiguator was in fact derived from this
   * version's DataStructure id. A malformed or foreign {@code $id} would be shipped verbatim to the
   * config-adapter and break {@code $ref} resolution downstream, so it is rejected before release.
   * The {@code $id} is optional: a model authored without a UML diagram carries none.
   */
  private void validateModelId(DataStructureVersion version) {
    Object modelId = version.getModel().get("$id");
    if (modelId == null) {
      return;
    }
    UUID dataStructureId = version.getDataStructure().getId();
    String echoedModelId = StringUtils.abbreviate(modelId.toString(), 256);
    if (!CoreUrn.matchesId(modelId.toString(), dataStructureId)) {
      // The generic exception message reaches the client verbatim; internal identifiers and
      // derivation details stay out of it. The full context for diagnosis goes to the log.
      log.warn(
          "Rejected model $id on release of DataStructureVersion {}: '{}' does not match"
              + " DataStructure {} (expected disambiguator {})",
          version.getId(),
          Encode.forJava(echoedModelId),
          dataStructureId,
          CoreUrn.disambiguatorFor(dataStructureId));
      throw new InvalidInputException(
          "model.$id",
          version.getId(),
          "DataStructure model $id is not a valid CORE URN for this DataStructure: "
              + echoedModelId);
    }
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
   * Validates that the version is not in use by any data source or data sink and that deleting it
   * would not leave a released data structure without any released versions.
   *
   * @param id the version ID to delete
   * @return the version entity to be deleted
   * @throws ResourceInUseException if the version is referenced by a data source or a data sink
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
    if (isInUse(versionId)) {
      throw new ResourceInUseException(
          "DataStructureVersion",
          versionId,
          "Cannot modify DataStructureVersion because it is referenced by one or more DataSources or DataSinks.");
    }
  }

  /** A version is in use when a data source or a data sink references it. */
  private boolean isInUse(UUID versionId) {
    return dataSourceRepository.existsByDataStructureVersionId(versionId)
        || dataSinkRepository.existsByDataStructureVersionId(versionId);
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

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataStructureVersion} entities through their lifecycle (DRAFT to
 * AVAILABLE). The version's JSON Schema (and its UI styles) lives in the Model Forge registry — the
 * service stores it through the {@link ModelRegistryGateway} and mirrors the assigned pin onto the
 * shell — and constrains versions that are in use by a data source or a data sink.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private final DataSourceRepository dataSourceRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

  private final DataStructureService dataStructureService;

  private final DataStructureVersionMapper dataStructureVersionMapper;

  private final ModelRegistryGateway modelRegistryGateway;

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
    // Set dataStructure — both sides: callers composing create and release inside one
    // transaction (the bundle import) read the parent's in-memory collection in
    // validateRelease, and Hibernate never refreshes it for an already-managed entity.
    Optional.ofNullable(input.getDataStructureId())
        .map(dataStructureService::findByIdOrThrow)
        .ifPresentOrElse(
            structure -> {
              entity.setDataStructure(structure);
              structure.getDataStructureVersions().add(entity);
            },
            () -> {
              throw new InvalidInputException(
                  "dataStructureId", entity.getId(), "dataStructureId cannot be null or blank");
            });

    // Validate the model and store it in Model Forge (the version authority), mirroring the
    // assigned version + URN onto the shell. Done here rather than in preSave because model,
    // styles and the version bump live on the input DTO, which preSave does not receive.
    validateModelSchema(entity, input);
    storeModelInRegistry(entity, input);

    // The registry mints the real version string only when a model is stored. A version created
    // without a model (the UI's two-step flow) must still never carry a null version — the portal
    // frontend sorts versions with versionNumber.localeCompare and dies on null. The provisional
    // is recognizably a draft and gets overwritten by the registry pin on the first model store.
    if (entity.getVersion() == null) {
      entity.setVersion(provisionalVersion(entity.getDataStructure()));
    }

    return super.postConvertToEntity(entity, input);
  }

  private static final String PROVISIONAL_SUFFIX = "-draft";

  /**
   * The next plausible version string for a model-less draft: highest parseable sibling version
   * (provisional siblings count too, so two parallel drafts do not collide) with the patch bumped,
   * or {@code 1.0.0} for the first version — always with the {@code -draft} suffix, so it cannot be
   * mistaken for a registry-minted number.
   */
  private String provisionalVersion(DataStructure parent) {
    int[] max = null;
    if (parent != null && parent.getDataStructureVersions() != null) {
      for (DataStructureVersion sibling : parent.getDataStructureVersions()) {
        int[] parsed = parseSemver(sibling.getVersion());
        if (parsed != null && (max == null || compareSemver(parsed, max) > 0)) {
          max = parsed;
        }
      }
    }
    if (max == null) {
      return "1.0.0" + PROVISIONAL_SUFFIX;
    }
    return max[0] + "." + max[1] + "." + (max[2] + 1) + PROVISIONAL_SUFFIX;
  }

  private static int compareSemver(int[] a, int[] b) {
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }

  /** Parses {@code major.minor.patch} (a {@code -draft} suffix is tolerated); null if no match. */
  private static int[] parseSemver(String version) {
    if (version == null) {
      return null;
    }
    String bare =
        version.endsWith(PROVISIONAL_SUFFIX)
            ? version.substring(0, version.length() - PROVISIONAL_SUFFIX.length())
            : version;
    String[] parts = bare.split("\\.");
    if (parts.length != 3) {
      return null;
    }
    try {
      return new int[] {
        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])
      };
    } catch (NumberFormatException e) {
      return null;
    }
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
   * in use by a data source or a data sink, structural fields (model, styles) are locked and only
   * description and modelName may change — the input's model/styles are nulled so no new registry
   * version is stored and the existing pin is preserved. A released version that is not in use may
   * have its model replaced but never cleared — it must always retain a non-empty model.
   *
   * @param input the update input
   * @param existingEntity the current version entity
   * @return the preprocessed input with restricted fields neutralized if in use
   * @throws InvalidInputException if an update to a released version would clear its model
   */
  @Override
  protected DataStructureVersionInputDTO preProcessUpdateInput(
      DataStructureVersionInputDTO input, DataStructureVersion existingEntity) {
    boolean isReleased =
        existingEntity.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT;

    if (isReleased && isInUse(existingEntity.getId())) {
      // Version is in use: block all structural changes, allow only description and modelName.
      // An absent model means "content unchanged" — no registry write happens, the version keeps
      // its stored pin (modelUrn/version stay untouched; they are not mapped from the input).
      input.setModel(null);
      input.setStyles(null);
    } else if (isReleased && (input.getModel() == null || input.getModel().isEmpty())) {
      // Released but not in use: the model may be replaced, but never cleared — a released
      // version must always retain a non-empty model, so the full update must carry one.
      throw new InvalidInputException(
          "model",
          existingEntity.getId(),
          "Cannot clear the model of a released DataStructureVersion");
    }

    return super.preProcessUpdateInput(input, existingEntity);
  }

  /**
   * Validates the input's model as a JSON Schema via embedded Model Forge before saving.
   * portal-backend has no schema validation of its own; a malformed model is rejected here on
   * create and update. An absent/empty model is not validated (the release check enforces
   * presence).
   *
   * @param entity the data structure version entity (for error context)
   * @param input the input DTO carrying the model
   * @throws InvalidInputException if the model is present but not a valid JSON Schema
   */
  private void validateModelSchema(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    List<String> errors = modelRegistryGateway.validateSchema(input.getModel());
    if (!errors.isEmpty()) {
      throw new InvalidInputException(
          "model",
          entity.getId(),
          "Model is not a valid JSON Schema: " + String.join("; ", errors));
    }
  }

  /**
   * Stores the input's model (styles merged in as {@code x-ui-styles} by the gateway) in Model
   * Forge (the version authority) and mirrors the assigned versioned URN and version string onto
   * the shell. Skipped when the input carries no model — a draft without a model stays unpinned, an
   * update without a model keeps the version's current content pin. The parent's stable logical URN
   * is minted on the first store and reused for every later version.
   *
   * @param entity the data structure version entity
   * @param input the input DTO carrying model, styles and the requested version bump
   */
  private void storeModelInRegistry(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    if (input.getModel() == null || input.getModel().isEmpty()) {
      return;
    }
    if (entity.getModelUrn() != null
        && modelRegistryGateway.isUnchanged(
            entity.getModelUrn(), input.getModel(), input.getStyles())) {
      // Unchanged content keeps the existing pin — a metadata-only update (description,
      // modelName, PATCH round-trip) must not mint a new registry version.
      return;
    }
    DataStructure parent = entity.getDataStructure();
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storeModel(
            Optional.ofNullable(parent.getModelLogicalUrn()),
            parent.getName(),
            input.getModel(),
            input.getStyles(),
            input.getVersionBump());
    if (parent.getModelLogicalUrn() == null) {
      parent.setModelLogicalUrn(pin.logicalUrn());
    }
    entity.setModelUrn(pin.versionedUrn());
    entity.setVersion(pin.version());
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
   * fields (model, styles, modelName, description) can be updated — a new model stores a new
   * registry version and re-mirrors the pin. If the version is in use, only description and
   * modelName can be changed.
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

    // Validate that the version carries a model (a stored registry pin implies a non-empty
    // JSON Schema — the gateway never stores an empty model)
    if (version.getModelUrn() == null) {
      throw new InvalidInputException(
          "model", id, "DataStructureVersion must contain a model before releasing");
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

  /**
   * A version is in use when a data source (host FK) or a data sink references it. Sink references
   * live in the registry: a sink's configuration carries the version's model URN in its {@code
   * element} field, which Model Forge tracks as a dependency edge — so the sink dimension is
   * answered by asking the registry who depends on the version's model.
   */
  private boolean isInUse(UUID versionId) {
    if (dataSourceRepository.existsByDataStructureVersionId(versionId)) {
      return true;
    }
    String modelUrn =
        dataStructureVersionRepository
            .findById(versionId)
            .map(DataStructureVersion::getModelUrn)
            .orElse(null);
    return modelRegistryGateway.isReferencedBySink(modelUrn);
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

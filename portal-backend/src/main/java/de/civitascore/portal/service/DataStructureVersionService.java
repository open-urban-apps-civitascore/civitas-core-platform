package de.civitascore.portal.service;

import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.portal.mapper.DataStructureVersionMapper;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureVersionInputDTO;
import de.civitascore.portal.model.input.DataStructureVersionMetaInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.VersionBump;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.security.ModelReferenceAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ModelMemberUnreachableException;
import de.civitascore.portal.util.ModelMissingException;
import de.civitascore.portal.util.ModelReferenceNotAvailableException;
import de.civitascore.portal.util.ModelRootMissingException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.ArrayList;
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
 * shell — and constrains a version that something still references.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataStructureVersionService
    extends BaseService<DataStructureVersion, DataStructureVersionInputDTO> {

  private static final String DATASTRUCTURE_TYPE = "datastructure";
  private static final int URN_TYPE_SEGMENT = 4;

  private final DataStructureVersionRepository dataStructureVersionRepository;

  private final DataStructureService dataStructureService;

  private final DataStructureVersionMapper dataStructureVersionMapper;

  private final ModelRegistryGateway modelRegistryGateway;

  private final ArtifactUsageLookup artifactUsageLookup;

  private final ModelReferenceAuthorizer modelReferenceAuthorizer;

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
    requirePinnedStructureUrns(input.getImportedStructureUrns());

    // Set dataStructure
    Optional.ofNullable(input.getDataStructureId())
        .map(dataStructureService::findByIdOrThrow)
        .ifPresentOrElse(
            entity::setDataStructure,
            () -> {
              throw new InvalidInputException(
                  "dataStructureId", entity.getId(), "dataStructureId cannot be null or blank");
            });

    // Store the model in Model Forge (the version authority), mirroring the assigned version + URN
    // onto the shell. Done here rather than in preSave because model, styles and the version bump
    // live on the input DTO, which preSave does not receive. The registry refuses a model that is
    // not a conforming JSON Schema, reported as a 400 by ModelRegistryExceptionHandler.
    storeModelInRegistry(entity, input);

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
   * Stores the input's model (styles merged in as {@code x-ui-styles} by the gateway) in Model
   * Forge (the version authority) and mirrors the assigned versioned URN and version string onto
   * the shell. Skipped when the input carries no model — a draft without a model stays unpinned, an
   * update without a model keeps the version's current content pin.
   *
   * @param entity the data structure version entity
   * @param input the input DTO carrying the model and its styles
   */
  private void storeModelInRegistry(
      DataStructureVersion entity, DataStructureVersionInputDTO input) {
    if (input.getModel() == null || input.getModel().isEmpty()) {
      return;
    }
    if (entity.getModelUrn() != null
        && modelRegistryGateway.isUnchanged(
            entity.getModelUrn(), input.getModel(), input.getStyles())) {
      // Unchanged content keeps the existing pin — an update that only touches metadata
      // (description, modelName) must not mint a new registry version.
      return;
    }
    DataStructure parent = entity.getDataStructure();
    // Before anything is written: a refused write must leave no trace in the registry.
    modelReferenceAuthorizer.authorizeWrite(
        parent.getId(), input.getModel(), input.getStyles(), entity.getModelUrn());
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storeModel(
            Optional.ofNullable(parent.getModelLogicalUrn()),
            parent.getId(),
            parent.getName(),
            input.getModel(),
            input.getStyles(),
            // A version's first model starts a new major and has no number to count from;
            // changing a model it already has advances the minor from the version's own number, so
            // the revision stays inside its major. Keyed on the stored pin rather than on how the
            // version was saved, so a model arriving on a later save still starts a major.
            entity.getModelUrn() == null ? VersionBump.MAJOR : VersionBump.MINOR,
            entity.getModelUrn() == null ? null : entity.getVersion());
    if (entity.getModelUrn() == null) {
      requireNewModelVersion(parent.getId(), pin);
    }
    if (parent.getModelLogicalUrn() == null) {
      parent.setModelLogicalUrn(pin.logicalUrn());
    }
    entity.setModelUrn(pin.versionedUrn());
    entity.setVersion(pin.version());
  }

  private static void requirePinnedStructureUrns(List<String> importedStructureUrns) {
    if (importedStructureUrns != null
        && !importedStructureUrns.stream()
            .allMatch(DataStructureVersionService::isPinnedDataStructureUrn)) {
      throw new InvalidInputException(
          "DataStructureVersion",
          "importedStructureUrns",
          "importedStructureUrns must name data structures by CORE URNs pinned to a version");
    }
  }

  private static boolean isPinnedDataStructureUrn(String urn) {
    return CoreUrn.isValid(urn) && DATASTRUCTURE_TYPE.equals(urn.split(":")[URN_TYPE_SEGMENT]);
  }

  /**
   * Model Forge stores no new version for content identical to its newest one and returns that
   * version's pin, which another version of this structure already holds.
   */
  private void requireNewModelVersion(UUID dataStructureId, ModelRegistryGateway.ModelPin pin) {
    if (dataStructureVersionRepository.existsByDataStructureIdAndModelUrn(
        dataStructureId, pin.versionedUrn())) {
      throw new UniqueConstraintViolationException(
          "The model is unchanged from version " + pin.version() + ", so it starts no new version");
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
    return super.update(id, input);
  }

  /**
   * Updates the metadata of a released data structure version.
   *
   * @param id the version ID
   * @param meta the metadata
   * @return the updated version
   * @throws InvalidInputException if the version is DRAFT
   */
  @Transactional
  public DataStructureVersion updateReleasedMeta(UUID id, DataStructureVersionMetaInputDTO meta) {
    DataStructureVersion existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus",
          id,
          "Cannot update released metadata for a DRAFT DataStructureVersion.");
    }
    existingEntity.setDescription(meta.getDescription());
    return save(existingEntity);
  }

  /**
   * Returns the current metadata of the version, which a metadata patch starts from.
   *
   * @param entity the version
   * @return the metadata
   */
  public DataStructureVersionMetaInputDTO toMetaInput(DataStructureVersion entity) {
    return dataStructureVersionMapper.toMetaInput(entity);
  }

  /**
   * Releases a data structure version by validating it has a model (JSON schema) with exactly one
   * root Element and setting status to AVAILABLE.
   *
   * @param id the version ID
   * @return the released version
   * @throws InvalidInputException if the version is already released or has no model
   * @throws ModelRootMissingException if the version's model has no root Element
   * @throws ModelMemberUnreachableException if members of the version's model are not reachable
   *     from its root Element
   * @throws ModelMissingException if the model registry no longer holds the version's model
   * @throws ModelReferenceNotAvailableException if the version's model references a model the
   *     caller may not use
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

    modelRegistryGateway.requireSingleRoot(version.getModelUrn());
    modelReferenceAuthorizer.authorizeStored(
        version.getDataStructure().getId(), version.getModelUrn());

    version.setDataStructureVersionStatus(DataStructureVersionStatus.AVAILABLE);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Duplicates a data structure version into a new DRAFT version of the same data structure. The
   * copy takes the description, model name and imported structure URNs of the source, and is pinned
   * to a registry copy of exactly the source's model version. A source without a model gives a copy
   * without a model.
   *
   * @param sourceVersionId the id of the version to duplicate
   * @return the new version
   * @throws ModelMissingException if the model registry no longer holds the source's model
   * @throws ModelReferenceNotAvailableException if the source's model references a model the caller
   *     may not use
   */
  @Transactional
  public DataStructureVersion duplicate(UUID sourceVersionId) {
    DataStructureVersion source = findByIdOrThrow(sourceVersionId);
    UUID dataStructureId = source.getDataStructure().getId();
    ModelRegistryGateway.ModelPin pin = null;
    if (source.getModelUrn() != null) {
      modelReferenceAuthorizer.authorizeStored(dataStructureId, source.getModelUrn());
      pin = modelRegistryGateway.copyModelVersion(source.getModelUrn());
    }

    DataStructureVersionInputDTO input = new DataStructureVersionInputDTO();
    input.setDataStructureId(dataStructureId);
    input.setDescription(source.getDescription());
    input.setModelName(source.getModelName());
    input.setImportedStructureUrns(
        source.getImportedStructureUrns() == null
            ? null
            : new ArrayList<>(source.getImportedStructureUrns()));
    DataStructureVersion copy = create(input);
    if (pin != null) {
      copy.setModelUrn(pin.versionedUrn());
      copy.setVersion(pin.version());
      copy = save(copy);
    }
    return copy;
  }

  /**
   * Unreleases a data structure version by setting status back to DRAFT.
   *
   * @param id the version ID
   * @return the unreleased version
   * @throws InvalidInputException if version is already DRAFT
   * @throws ResourceInUseException if a released entity references the version
   */
  @Transactional
  public DataStructureVersion unrelease(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);

    // Validate that version is currently released
    if (version.getDataStructureVersionStatus() == DataStructureVersionStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureVersionStatus", id, "DataStructureVersion is already in DRAFT status");
    }

    artifactUsageLookup.of(version).requireNoReleasedReferrer(getEntityName(), id);

    version.setDataStructureVersionStatus(DataStructureVersionStatus.DRAFT);
    return dataStructureVersionRepository.save(version);
  }

  /**
   * Refuses the delete while anything references any version of the version's line in the model
   * registry, a Data source is pinned to it, or another version of the data structure has its model
   * in the same line, since deleting the line deletes that version's model too.
   *
   * @param id the version ID to delete
   * @return the version entity to be deleted
   * @throws ResourceInUseException if the version is in use
   */
  @Override
  protected DataStructureVersion preProcessDelete(UUID id) {
    DataStructureVersion version = findByIdOrThrow(id);
    artifactUsageLookup.of(version).requireNotInUse(getEntityName(), id);
    requireOwnModelLine(version);
    return version;
  }

  private void requireOwnModelLine(DataStructureVersion version) {
    ModelLine line = modelLineOf(version);
    if (line == null) {
      return;
    }
    List<String> sharingModelUrns =
        dataStructureVersionRepository
            .findAllByDataStructure_IdIn(List.of(version.getDataStructure().getId()))
            .stream()
            .filter(other -> !other.getId().equals(version.getId()))
            .filter(other -> line.equals(modelLineOf(other)))
            .map(DataStructureVersion::getModelUrn)
            .toList();
    if (!sharingModelUrns.isEmpty()) {
      throw new ResourceInUseException(
          getEntityName(),
          version.getId(),
          "Cannot delete "
              + getEntityName()
              + " because another version of its data structure shares its model.",
          sharingModelUrns);
    }
  }

  private ModelLine modelLineOf(DataStructureVersion version) {
    String pin = version.getModelUrn();
    if (pin == null) {
      return null;
    }
    String logicalUrn = modelRegistryGateway.logicalUrn(pin);
    String major = GoverningVersionLookup.majorOf(pin, logicalUrn);
    return major == null ? null : new ModelLine(logicalUrn, major);
  }

  private record ModelLine(String logicalUrn, String major) {}

  /**
   * Deletes the version's model line from Model Forge in the delete's transaction. Once the
   * registry holds nothing of the data structure's model any more, the data structure forgets its
   * logical URN, so that its next model starts a new artifact; the registry still numbers that one
   * above every version it ever gave out.
   *
   * @param version the deleted version
   */
  @Override
  protected void postDelete(DataStructureVersion version) {
    if (version.getModelUrn() == null) {
      return;
    }
    modelRegistryGateway.deleteModelVersion(version.getModelUrn());
    // Loading the parent's versions here would put the deleted row back into the persistence
    // context, and Hibernate would drop the delete at flush.
    DataStructure parent = version.getDataStructure();
    if (parent.getModelLogicalUrn() != null
        && !modelRegistryGateway.holds(parent.getModelLogicalUrn())) {
      parent.setModelLogicalUrn(null);
    }
  }
}

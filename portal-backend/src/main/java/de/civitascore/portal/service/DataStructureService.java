package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.model.input.DataStructureMetaInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link DataStructure} entities through their lifecycle (DRAFT to AVAILABLE).
 * Handles version relationship resolution, release/unrelease status transitions, and validates that
 * no referenced versions are in use before allowing structural changes.
 */
@Service
@RequiredArgsConstructor
public class DataStructureService
    extends BaseDataEntityService<DataStructure, DataStructureInputDTO, DataStructureMetaInputDTO> {

  private final DataStructureRepository dataStructureRepository;
  private final DataStructureMapper dataStructureMapper;
  private final AssignmentFactory assignmentFactory;
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final ArtifactUsageLookup artifactUsageLookup;

  @Override
  protected DataStructureRepository getRepository() {
    return dataStructureRepository;
  }

  @Override
  protected DataStructureMapper getMapper() {
    return dataStructureMapper;
  }

  @Override
  protected String getEntityName() {
    return DataStructure.class.getSimpleName();
  }

  @Override
  protected AssignmentFactory getAssignmentFactory() {
    return assignmentFactory;
  }

  @Override
  protected ReleasableStatus getEntityStatus(DataStructure entity) {
    return entity.getDataStructureStatus();
  }

  @Override
  protected void setEntityStatus(DataStructure entity, ReleasableStatus status) {
    entity.setDataStructureStatus((DataStructureStatus) status);
  }

  @Override
  protected ReleasableStatus getDraftStatus() {
    return DataStructureStatus.DRAFT;
  }

  @Override
  protected ReleasableStatus getAvailableStatus() {
    return DataStructureStatus.AVAILABLE;
  }

  /**
   * Sets initial DRAFT status for newly created data structures.
   *
   * @param input the creation input
   * @return the input with DRAFT status set
   */
  @Override
  protected DataStructureInputDTO preProcessCreateInput(DataStructureInputDTO input) {
    // Set DRAFT status for newly created data structures
    input.setDataStructureStatus(DataStructureStatus.DRAFT);
    return super.preProcessCreateInput(input);
  }

  /**
   * Validates that the name is not blank before updating the data structure.
   *
   * @param input the update input
   * @param existingEntity the current data structure entity
   * @return the validated input
   * @throws InvalidInputException if the name is null or blank
   */
  @Override
  protected DataStructureInputDTO preProcessUpdateInput(
      DataStructureInputDTO input, DataStructure existingEntity) {
    if (StringUtils.isBlank(input.getName())) {
      throw new InvalidInputException(
          "name", existingEntity.getId(), "Name cannot be null or blank");
    }
    return input;
  }

  /**
   * Override update to ensure it can only be called for DRAFT data structures. For released data
   * structures, use updateReleasedMeta instead.
   *
   * @param id the data structure ID
   * @param input the update input
   * @return the updated data structure
   * @throws InvalidInputException if trying to update a non-DRAFT data structure
   */
  @Override
  public DataStructure update(UUID id, DataStructureInputDTO input) {
    DataStructure existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureStatus() != DataStructureStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureStatus",
          id,
          "Cannot update a released DataStructure. Use the released/meta endpoint instead.");
    }
    return super.update(id, input);
  }

  @Override
  @Transactional
  public DataStructure updateReleasedMeta(UUID id, DataStructureMetaInputDTO meta) {
    DataStructure existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureStatus() == DataStructureStatus.DRAFT) {
      throw new InvalidInputException(
          getEntityName(), id, "Cannot update released metadata on a DRAFT entity");
    }
    return super.update(id, dataStructureMapper.toUpdateInput(meta));
  }

  @Override
  public DataStructureMetaInputDTO toMetaInput(DataStructure entity) {
    return dataStructureMapper.toMetaInput(entity);
  }

  @Override
  protected void validateRelease(DataStructure entity) {
    boolean hasReleasedVersion =
        entity.getDataStructureVersions().stream()
            .anyMatch(
                version ->
                    version.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT);

    if (!hasReleasedVersion) {
      throw new InvalidInputException(
          "dataStructureVersions",
          entity.getId(),
          "DataStructure must contain at least one released DataStructureVersion before"
              + " releasing");
    }
  }

  @Override
  protected void validateUnrelease(DataStructure entity) {
    artifactUsageLookup.of(entity).requireNoReleasedReferrer(getEntityName(), entity.getId());
  }

  /**
   * Validates that nothing still references any of the data structure's versions before allowing
   * deletion.
   *
   * @param id the data structure ID to delete
   * @return the data structure entity to be deleted
   * @throws ResourceInUseException if any version is still referenced
   */
  @Override
  protected DataStructure preProcessDelete(UUID id) {
    DataStructure dataStructure = findByIdOrThrow(id);
    validateNoVersionInUse(dataStructure);
    return dataStructure;
  }

  /**
   * After the data structure and its versions are deleted, delete the backing model artifact from
   * Model Forge in the same transaction. No-op when no model was ever stored (logical URN null).
   *
   * @param entity the deleted data structure
   */
  @Override
  protected void postDelete(DataStructure entity) {
    if (entity != null && entity.getModelLogicalUrn() != null) {
      modelRegistryGateway.deleteModel(entity.getModelLogicalUrn());
    }
  }

  private void validateNoVersionInUse(DataStructure dataStructure) {
    Set<DataStructureVersion> versions = dataStructure.getDataStructureVersions();
    Set<UUID> versionIds =
        versions.stream().map(DataStructureVersion::getId).collect(Collectors.toSet());
    if (versionIds.isEmpty()) {
      return;
    }
    if (dataSourceRepository.existsByDataStructureVersionIdIn(versionIds)) {
      throw new ResourceInUseException(
          "DataStructure",
          dataStructure.getId(),
          "Cannot modify DataStructure because a DataSource is pinned to one of its versions.");
    }
    // The registry answers for every reference it holds onto a version's model — a sink's or
    // source's element, a mapping endpoint, another model, a second data set.
    List<String> blockers =
        versions.stream()
            .map(DataStructureVersion::getModelUrn)
            .map(modelRegistryGateway::referencesTo)
            .flatMap(List::stream)
            .distinct()
            .toList();
    if (!blockers.isEmpty()) {
      throw new ResourceInUseException(
          "DataStructure",
          dataStructure.getId(),
          "Cannot modify DataStructure because one or more of its versions is still referenced.",
          blockers);
    }
  }
}

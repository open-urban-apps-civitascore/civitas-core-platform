package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataStructure} entities through their lifecycle (DRAFT to AVAILABLE).
 * Handles version relationship resolution, release/unrelease status transitions, and validates that
 * no referenced versions are in use before allowing structural changes.
 */
@Service
@RequiredArgsConstructor
public class DataStructureService
    extends BaseDataEntityService<DataStructure, DataStructureInputDTO> {

  private final DataStructureRepository dataStructureRepository;
  private final DataStructureMapper dataStructureMapper;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final AssignmentFactory assignmentFactory;
  private final DataSourceRepository dataSourceRepository;
  private final ModelRegistryGateway modelRegistryGateway;

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
   * Resolves data structure version references after DTO-to-entity conversion. Validates that all
   * provided version IDs exist and sets the bidirectional relationship.
   *
   * @param entity the data structure entity
   * @param input the input DTO containing version IDs
   * @return the entity with resolved version relationships and assignments
   * @throws InvalidInputException if any version ID is not found
   */
  @Override
  protected DataStructure postConvertToEntity(DataStructure entity, DataStructureInputDTO input) {
    if (input.getDataStructureVersionIds() != null) {
      if (input.getDataStructureVersionIds().isEmpty()) {
        entity.setDataStructureVersions(new HashSet<>());
      } else {
        List<DataStructureVersion> versions =
            dataStructureVersionRepository.findAllById(input.getDataStructureVersionIds());
        if (versions.size() != input.getDataStructureVersionIds().size()) {
          throw new InvalidInputException(
              "DataStructure",
              "dataStructureVersionIds",
              "One or more DataStructureVersion IDs not found");
        }
        entity.setDataStructureVersions(new HashSet<>(versions));
      }
    }

    return super.postConvertToEntity(entity, input); // base handles assignments
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
    validateNoVersionInUse(entity);
  }

  /**
   * Validates that none of the data structure's versions are in use by a data source or a data sink
   * before allowing deletion.
   *
   * @param id the data structure ID to delete
   * @return the data structure entity to be deleted
   * @throws ResourceInUseException if any version is referenced by a data source or a data sink
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
    // Sink references live in the registry (a sink's config carries the version's model URN in its
    // element field, tracked by Model Forge); the source dimension is a host FK.
    boolean inUse =
        dataSourceRepository.existsByDataStructureVersionIdIn(versionIds)
            || versions.stream()
                .map(DataStructureVersion::getModelUrn)
                .anyMatch(modelRegistryGateway::isReferencedBySink);
    if (inUse) {
      throw new ResourceInUseException(
          "DataStructure",
          dataStructure.getId(),
          "Cannot modify DataStructure because one or more of its versions is referenced by a DataSource or DataSink.");
    }
  }
}

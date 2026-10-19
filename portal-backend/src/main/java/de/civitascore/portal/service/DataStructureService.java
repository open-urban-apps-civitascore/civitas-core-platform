package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.security.AssignmentChangeAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataStructure} entities. Validates that no version is in use before
 * the data structure is deleted.
 */
@Service
@RequiredArgsConstructor
public class DataStructureService
    extends BaseDataEntityService<DataStructure, DataStructureInputDTO> {

  private final DataStructureRepository dataStructureRepository;
  private final DataStructureMapper dataStructureMapper;
  private final AssignmentFactory assignmentFactory;
  private final AssignmentChangeAuthorizer assignmentChangeAuthorizer;
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
  protected AssignmentChangeAuthorizer getAssignmentChangeAuthorizer() {
    return assignmentChangeAuthorizer;
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
    // source's element, a mapping endpoint, another model.
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

package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DataStructureService
    extends BaseDataEntityService<DataStructure, DataStructureInputDTO> {

  private final DataStructureRepository dataStructureRepository;
  private final DataStructureMapper dataStructureMapper;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ScopedAssignmentBuilderService assignmentBuilderService;
  private final DataSourceRepository dataSourceRepository;

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
  protected ScopedAssignmentBuilderService getAssignmentBuilderService() {
    return assignmentBuilderService;
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataStructure along with dataStructureVersions in a single JOIN query, preventing N+1 query
   * problems that would occur with lazy loading.
   */
  @Override
  public Optional<DataStructure> findById(UUID id) {
    Optional<DataStructure> entity = dataStructureRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

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

  @Override
  protected DataStructure preSave(DataStructure entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  @Override
  protected DataStructureInputDTO preProcessCreateInput(DataStructureInputDTO input) {
    // Set DRAFT status for newly created data structures
    input.setDataStructureStatus(DataStructureStatus.DRAFT);
    return super.preProcessCreateInput(input);
  }

  private void validateUniqueName(DataStructure entity) {
    dataStructureRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    DataStructure.class.getSimpleName(), "name", entity.getName());
              }
            });
  }

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
   * Override update to ensure it can only be called for DRAFT data structures. For published data
   * structures, use updatePublishedMeta instead.
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
          "dataStructureStatus", id, "Cannot update non-DRAFT DataStructure.");
    }
    return super.update(id, input);
  }

  /**
   * Updates only the metadata (name, description) of a published data structure. Cannot modify
   * status or createdFromDataSource.
   *
   * @param id the data structure ID
   * @param input the update input
   * @return the updated data structure
   * @throws InvalidInputException if trying to update a DRAFT data structure
   */
  @Transactional
  public DataStructure updatePublishedMeta(UUID id, DataStructureInputDTO input) {
    DataStructure existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataStructureStatus() == DataStructureStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureStatus", id, "Cannot use updatePublishedMeta for DRAFT DataStructure.");
    }

    return super.update(id, input);
  }

  /**
   * Publishes a data structure by validating it has at least one published version and setting
   * status to AVAILABLE.
   *
   * @param id the data structure ID
   * @return the published data structure
   * @throws InvalidInputException if data structure has no published versions or is already
   *     published
   */
  @Transactional
  public DataStructure publish(UUID id) {
    DataStructure dataStructure = findByIdOrThrow(id);

    // Validate that data structure is currently in DRAFT status
    if (dataStructure.getDataStructureStatus() != DataStructureStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureStatus", id, "DataStructure is already published");
    }

    // Validate that data structure has at least one published version (any non-DRAFT version)
    boolean hasPublishedVersion =
        dataStructure.getDataStructureVersions().stream()
            .anyMatch(
                version ->
                    version.getDataStructureVersionStatus() != DataStructureVersionStatus.DRAFT);

    if (!hasPublishedVersion) {
      throw new InvalidInputException(
          "dataStructureVersions",
          id,
          "DataStructure must contain at least one published DataStructureVersion before"
              + " publishing");
    }

    dataStructure.setDataStructureStatus(DataStructureStatus.AVAILABLE);
    return dataStructureRepository.save(dataStructure);
  }

  /**
   * Unpublishes a data structure by setting status back to DRAFT. Always allowed.
   *
   * @param id the data structure ID
   * @return the unpublished data structure
   */
  @Transactional
  public DataStructure unpublish(UUID id) {
    DataStructure dataStructure = findByIdOrThrow(id);

    if (dataStructure.getDataStructureStatus() == DataStructureStatus.DRAFT) {
      throw new InvalidInputException(
          "dataStructureStatus", id, "DataStructure is already in DRAFT status");
    }

    validateNoVersionInUse(dataStructure);

    dataStructure.setDataStructureStatus(DataStructureStatus.DRAFT);
    return dataStructureRepository.save(dataStructure);
  }

  @Override
  protected DataStructure preProcessDelete(UUID id) {
    DataStructure dataStructure = findByIdOrThrow(id);
    validateNoVersionInUse(dataStructure);
    return dataStructure;
  }

  private void validateNoVersionInUse(DataStructure dataStructure) {
    Set<UUID> versionIds =
        dataStructure.getDataStructureVersions().stream()
            .map(DataStructureVersion::getId)
            .collect(Collectors.toSet());
    if (versionIds.isEmpty()) {
      return;
    }
    if (dataSourceRepository.existsByDataStructureVersionIdIn(versionIds)) {
      throw new ResourceInUseException(
          "DataStructure",
          dataStructure.getId(),
          "Cannot modify DataStructure because one or more of its versions is referenced by a DataSource.");
    }
  }
}

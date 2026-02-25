package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataStructureMapper;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.entity.DataStructure;
import de.civitascore.portal.model.input.DataStructureInputDTO;
import de.civitascore.portal.repository.AssignmentRepository;
import de.civitascore.portal.repository.DataStructureRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataStructureService extends BaseService<DataStructure, DataStructureInputDTO> {

  private final DataStructureRepository dataStructureRepository;
  private final DataStructureMapper dataStructureMapper;
  private final AssignmentRepository assignmentRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;

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
    // Set assignments
    Optional.ofNullable(input.getAssignmentIds())
        .map(assignmentRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setAssignments);

    // Set dataStructureVersions
    Optional.ofNullable(input.getDataStructureVersionIds())
        .map(dataStructureVersionRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setDataStructureVersions);

    return super.postConvertToEntity(entity, input);
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
}

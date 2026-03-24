package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.repository.DataSpaceRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSpaceService extends BaseService<DataSpace, DataSpaceInputDTO> {

  private final DataSpaceRepository dataSpaceRepository;
  private final DataSpaceMapper dataSpaceMapper;
  private final UserService userService;
  private final ObjectMapper objectMapper;

  @Override
  protected DataSpaceRepository getRepository() {
    return dataSpaceRepository;
  }

  @Override
  protected DataSpaceMapper getMapper() {
    return dataSpaceMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSpace.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataSpace along with owner and parentDataSpace in a single JOIN query, preventing N+1 query
   * problems that would occur with lazy loading.
   */
  @Override
  public Optional<DataSpace> findById(UUID id) {
    Optional<DataSpace> entity = dataSpaceRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected DataSpace postConvertToEntity(DataSpace entity, DataSpaceInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getOwnerUserId() != null) {
      entity.setOwner(userService.findByIdOrThrow(input.getOwnerUserId()));
    } else {
      entity.setOwner(null);
    }

    if (input.getParentDataSpaceId() != null) {
      entity.setParentDataSpace(findByIdOrThrow(input.getParentDataSpaceId()));
    } else {
      entity.setParentDataSpace(null);
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSpace preSave(DataSpace entity) {
    validateUniqueName(entity, dataSpaceRepository::findByName);
    return super.preSave(entity);
  }

  @Override
  protected DataSpaceInputDTO preProcessUpdateInput(
      DataSpaceInputDTO input, DataSpace existingEntity) {
    validateFieldsNotBlank(objectMapper, input, existingEntity.getId(), "name");
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

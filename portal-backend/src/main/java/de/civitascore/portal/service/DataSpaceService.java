package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.util.ResourceInUseException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSpace} entities. Resolves owner user and parent data space
 * relationships during entity conversion.
 */
@Service
@RequiredArgsConstructor
public class DataSpaceService extends BaseService<DataSpace, DataSpaceInputDTO> {

  private final DataSpaceRepository dataSpaceRepository;
  private final DataSpaceMapper dataSpaceMapper;
  private final UserService userService;

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
   * Resolves owner user and parent data space references after DTO-to-entity conversion.
   *
   * @param entity the data space entity
   * @param input the data space input DTO containing owner and parent IDs
   * @return the entity with resolved owner and parent relationships
   */
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
  protected DataSpace preProcessDelete(UUID id) {
    DataSpace dataSpace = findByIdOrThrow(id);
    if (!dataSpace.getChildDataSpaces().isEmpty()) {
      throw new ResourceInUseException(
          "DataSpace",
          dataSpace.getId(),
          "Cannot delete DataSpace because it has child data spaces. Remove or reassign child"
              + " data spaces first.");
    }
    return dataSpace;
  }
}

package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSpaceMapper;
import de.civitascore.portal.model.entity.DataSpace;
import de.civitascore.portal.model.input.DataSpaceInputDTO;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSpaceService extends BaseTenantAwareService<DataSpace, String, DataSpaceInputDTO> {

  private final DataSpaceRepository dataSpaceRepository;
  private final DataSpaceMapper dataSpaceMapper;
  private final UserService userService;
  private final ObjectMapper objectMapper;

  @Override
  protected TenantAwareRepository<DataSpace, String> getRepository() {
    return dataSpaceRepository;
  }

  @Override
  protected DataSpaceMapper getMapper() {
    return dataSpaceMapper;
  }

  @Override
  protected String getEntityName() {
    return "DataSpace";
  }

  @Override
  protected DataSpace postConvertToEntity(DataSpace entity, DataSpaceInputDTO input) {
    if (input.getOwnerUserId() != null) {
      entity.setOwner(userService.findById(input.getOwnerUserId()));
    } else {
      entity.setOwner(null);
    }

    if (input.getParentDataSpaceId() != null) {
      dataSpaceRepository
          .findById(input.getParentDataSpaceId())
          .ifPresent(entity::setParentDataSpace);
    } else {
      entity.setParentDataSpace(null);
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSpace preSave(DataSpace entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(DataSpace entity) {
    dataSpaceRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "DataSpace", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }

  @Override
  protected DataSpaceInputDTO preProcessUpdateInput(
      DataSpaceInputDTO input, DataSpace existingEntity) {
    try {
      String inputJson = objectMapper.writeValueAsString(input);
      JsonNode jsonNode = objectMapper.readTree(inputJson);

      if (jsonNode.has("name") && StringUtils.isBlank(jsonNode.get("name").asText())) {
        throw new InvalidInputException(
            "name", "Name cannot be null or blank", existingEntity.getId().toString());
      }
      if (jsonNode.has("title") && StringUtils.isBlank(jsonNode.get("title").asText())) {
        throw new InvalidInputException(
            "title", "Title cannot be null or blank", existingEntity.getId().toString());
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

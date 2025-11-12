package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.TenantAwareRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSetService extends BaseTenantAwareService<DataSet, String, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;
  private final UserService userService;
  private final DataSpaceService dataSpaceService;
  private final ObjectMapper objectMapper;

  @Override
  protected TenantAwareRepository<DataSet, String> getRepository() {
    return dataSetRepository;
  }

  @Override
  protected DataSetMapper getMapper() {
    return dataSetMapper;
  }

  @Override
  protected String getEntityName() {
    return "DataSet";
  }

  @Override
  protected DataSet postConvertToEntity(DataSet entity, DataSetInputDTO input) {
    if (input.getOwnerUserId() != null) {
      entity.setOwner(userService.findById(input.getOwnerUserId()));
    } else {
      entity.setOwner(null);
    }

    if (Objects.nonNull(input.getDataSpaceIds())) {
      entity.setDataSpaces(new HashSet<>());
      if (!input.getDataSpaceIds().isEmpty()) {
        input
            .getDataSpaceIds()
            .forEach(
                dataSpaceId ->
                    dataSpaceService
                        .getRepository()
                        .findById(dataSpaceId)
                        .ifPresent(entity.getDataSpaces()::add));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSet preSave(DataSet entity) {
    validateUniqueTitle(entity);
    return super.preSave(entity);
  }

  private void validateUniqueTitle(DataSet entity) {
    dataSetRepository
        .findByTitleAndTenantId(entity.getTitle(), entity.getTenantId())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    "DataSet", "title", entity.getTitle(), "tenant", entity.getTenantId());
              }
            });
  }

  @Override
  protected DataSetInputDTO preProcessUpdateInput(DataSetInputDTO input, DataSet existingEntity) {
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

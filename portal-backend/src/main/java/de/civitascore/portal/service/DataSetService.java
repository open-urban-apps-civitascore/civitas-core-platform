package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSetService extends BaseService<DataSet, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;
  private final UserService userService;
  private final DataSpaceService dataSpaceService;
  private final ObjectMapper objectMapper;

  @Override
  protected DataSetRepository getRepository() {
    return dataSetRepository;
  }

  @Override
  protected DataSetMapper getMapper() {
    return dataSetMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSet.class.getSimpleName();
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataSet along with owner and dataSpaces in a single JOIN query, preventing N+1 query problems
   * that would occur with lazy loading.
   */
  @Override
  public DataSet findById(UUID id) {
    preProcessLoad(id);
    DataSet entity =
        dataSetRepository
            .findByIdWithRelations(id)
            .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
    return postLoad(entity);
  }

  @Override
  protected DataSet postConvertToEntity(DataSet entity, DataSetInputDTO input) {
    // Use getReferenceById for ManyToOne relationships to avoid unnecessary SELECT queries
    if (input.getOwnerUserId() != null) {
      entity.setOwner(userService.getReferenceById(input.getOwnerUserId()));
    } else {
      entity.setOwner(null);
    }

    // Use findAllById for efficient batch loading of dataSpaces instead of N+1 queries
    if (Objects.nonNull(input.getDataSpaceIds())) {
      entity.setDataSpaces(new HashSet<>());
      if (!input.getDataSpaceIds().isEmpty()) {
        entity.setDataSpaces(
            new HashSet<>(dataSpaceService.getRepository().findAllById(input.getDataSpaceIds())));
      }
    }

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSet preSave(DataSet entity) {
    validateUniqueName(entity);
    return super.preSave(entity);
  }

  private void validateUniqueName(DataSet entity) {
    dataSetRepository
        .findByName(entity.getName())
        .ifPresent(
            existing -> {
              if (!existing.getId().equals(entity.getId())) {
                throw new UniqueConstraintViolationException(
                    DataSet.class.getSimpleName(), "name", entity.getName());
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
            "name", existingEntity.getId(), "Name cannot be null or blank");
      }
    } catch (InvalidInputException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to process update input", e);
    }
    return super.preProcessUpdateInput(input, existingEntity);
  }
}

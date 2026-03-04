package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSetService extends BaseDataEntityService<DataSet, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;

  private final ScopedAssignmentBuilderService assignmentBuilderService;
  private final DistributionService distributionService;

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

  @Override
  protected ScopedAssignmentBuilderService getAssignmentBuilderService() {
    return assignmentBuilderService;
  }

  /**
   * Override findById to use EntityGraph for efficient loading of relationships. This fetches the
   * DataSet along with owner and dataSpaces in a single JOIN query, preventing N+1 query problems
   * that would occur with lazy loading.
   */
  @Override
  public Optional<DataSet> findById(UUID id) {
    Optional<DataSet> entity = dataSetRepository.findByIdWithRelations(id);
    return postLoad(entity);
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

  /**
   * Override update to ensure it can only be called for DRAFT datasets. For published datasets, use
   * updatePublishedMeta instead.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws UniqueConstraintViolationException if trying to update a non-DRAFT dataset
   */
  @Override
  public DataSet update(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new UniqueConstraintViolationException(
          "DataSet", "id", id.toString(), "status", existingEntity.getDataSetStatus().toString());
    }
    return super.update(id, input);
  }

  /**
   * Updates only the metadata (name, description) of a published dataset. Cannot modify
   * persistenceId or pipelines.
   *
   * @param id the dataset ID
   * @param input the update input
   * @return the updated dataset
   * @throws UniqueConstraintViolationException if trying to update a DRAFT dataset
   */
  @Transactional
  public DataSet updatePublishedMeta(UUID id, DataSetInputDTO input) {
    DataSet existingEntity = findByIdOrThrow(id);
    if (existingEntity.getDataSetStatus() == DataSetStatus.DRAFT) {
      throw new UniqueConstraintViolationException(
          "DataSet", "id", id.toString(), "status", existingEntity.getDataSetStatus().toString());
    }

    return super.update(id, input);
  }

  /**
   * Publishes a dataset by validating it has at least one pipeline, generating distributions from
   * pipeline APIs, and setting status to READY.
   *
   * @param id the dataset ID
   * @return the published dataset
   * @throws InvalidInputException if dataset has no pipelines or is already published
   */
  @Transactional
  public DataSet publish(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    // Validate that dataset is not already published
    if (dataSet.getDataSetStatus() == DataSetStatus.READY) {
      throw new InvalidInputException("dataSetStatus", id, "DataSet is already published");
    }

    // Validate that dataset has at least one pipeline
    if (dataSet.getPipelines() == null || dataSet.getPipelines().isEmpty()) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must contain at least one Pipeline before publishing");
    }

    // Generate distributions from pipeline APIs
    dataSet.getPipelines().stream()
        .filter(pipeline -> pipeline.getApis() != null)
        .flatMap(pipeline -> pipeline.getApis().stream())
        .distinct()
        .forEach(
            apiPath -> {
              Distribution distribution =
                  distributionService.createFromApiUrlAndDataSet(apiPath, dataSet);
              dataSet.getDistributions().add(distribution);
            });

    dataSet.setDataSetStatus(DataSetStatus.READY);
    return dataSetRepository.save(dataSet);
  }

  @Override
  protected DataSet preProcessDelete(UUID id) {
    DataSet dataSet = super.preProcessDelete(id);
    if (dataSet != null && dataSet.getDataSetStatus() != DataSetStatus.DRAFT) {
      throw new InvalidInputException(
          "dataSetStatus",
          id,
          "DataSet can only be deleted when in DRAFT status. Current status: "
              + dataSet.getDataSetStatus());
    }
    return dataSet;
  }
}

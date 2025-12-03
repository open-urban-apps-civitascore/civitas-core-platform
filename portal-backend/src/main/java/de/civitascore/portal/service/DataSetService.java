package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.AgentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSetSeriesRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class DataSetService extends BaseService<DataSet, DataSetInputDTO> {

  private final DataSetRepository dataSetRepository;
  private final DataSetMapper dataSetMapper;
  private final UserService userService;
  private final DataSpaceRepository dataSpaceRepository;
  private final DataSetSeriesRepository dataSetSeriesRepository;
  private final AgentRepository agentRepository;
  private final DistributionRepository distributionRepository;
  private final CatalogRepository catalogRepository;
  private final ObjectMapper objectMapper;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSetMapper dataSetMapper,
      UserService userService,
      DataSpaceRepository dataSpaceRepository,
      DataSetSeriesRepository dataSetSeriesRepository,
      AgentRepository agentRepository,
      DistributionRepository distributionRepository,
      CatalogRepository catalogRepository,
      ObjectMapper objectMapper) {
    this.dataSetRepository = dataSetRepository;
    this.dataSetMapper = dataSetMapper;
    this.userService = userService;
    this.dataSpaceRepository = dataSpaceRepository;
    this.dataSetSeriesRepository = dataSetSeriesRepository;
    this.agentRepository = agentRepository;
    this.distributionRepository = distributionRepository;
    this.catalogRepository = catalogRepository;
    this.objectMapper = objectMapper;
  }

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
    Optional.ofNullable(input.getOwnerUserId())
        .map(userService::findById)
        .ifPresentOrElse(entity::setOwner, () -> entity.setOwner(null));

    // Set dataSetSeries
    Optional.ofNullable(input.getDataSetSeriesId())
        .flatMap(dataSetSeriesRepository::findById)
        .ifPresentOrElse(entity::setDataSetSeries, () -> entity.setDataSetSeries(null));

    // Set dataSpaces
    Optional.ofNullable(input.getDataSpaceIds())
        .map(dataSpaceRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setDataSpaces);

    // Set agents
    Optional.ofNullable(input.getAgentIds())
        .map(agentRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setAgents);

    // Set distributions
    Optional.ofNullable(input.getDistributionIds())
        .map(distributionRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setDistributions);

    // Set catalogs
    Optional.ofNullable(input.getCatalogIds())
        .map(catalogRepository::findAllById)
        .map(HashSet::new)
        .ifPresent(entity::setCatalogs);

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

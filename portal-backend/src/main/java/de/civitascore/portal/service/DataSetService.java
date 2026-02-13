package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.civitascore.portal.config.GatewayConfig;
import de.civitascore.portal.mapper.DataSetMapper;
import de.civitascore.portal.model.embedded.DataSetStatus;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.Distribution;
import de.civitascore.portal.model.input.DataSetInputDTO;
import de.civitascore.portal.repository.AgentRepository;
import de.civitascore.portal.repository.CatalogRepository;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSetSeriesRepository;
import de.civitascore.portal.repository.DataSpaceRepository;
import de.civitascore.portal.repository.DistributionRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final PipelineRepository pipelineRepository;
  private final ObjectMapper objectMapper;
  private final GatewayConfig.GatewayConfigProperties gatewayConfigProperties;

  public DataSetService(
      DataSetRepository dataSetRepository,
      DataSetMapper dataSetMapper,
      UserService userService,
      DataSpaceRepository dataSpaceRepository,
      DataSetSeriesRepository dataSetSeriesRepository,
      AgentRepository agentRepository,
      DistributionRepository distributionRepository,
      CatalogRepository catalogRepository,
      PipelineRepository pipelineRepository,
      ObjectMapper objectMapper,
      GatewayConfig.GatewayConfigProperties gatewayConfigProperties) {
    this.dataSetRepository = dataSetRepository;
    this.dataSetMapper = dataSetMapper;
    this.userService = userService;
    this.dataSpaceRepository = dataSpaceRepository;
    this.dataSetSeriesRepository = dataSetSeriesRepository;
    this.agentRepository = agentRepository;
    this.distributionRepository = distributionRepository;
    this.catalogRepository = catalogRepository;
    this.pipelineRepository = pipelineRepository;
    this.objectMapper = objectMapper;
    this.gatewayConfigProperties = gatewayConfigProperties;
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
  public Optional<DataSet> findById(UUID id) {
    Optional<DataSet> entity = dataSetRepository.findByIdWithRelations(id);
    return postLoad(entity);
  }

  @Override
  protected DataSet postConvertToEntity(DataSet entity, DataSetInputDTO input) {
    Optional.ofNullable(input.getOwnerUserId())
        .map(userService::findByIdOrThrow)
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

    // Set pipelines
    Optional.ofNullable(input.getPipelineIds())
        .map(pipelineRepository::findAllById)
        .map(ArrayList::new)
        .ifPresent(entity::setPipelines);

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

  /**
   * Publishes a dataset by validating it has at least one pipeline, generating distributions from
   * pipeline APIs, and setting status to FINISHED.
   *
   * @param id the dataset ID
   * @return the published dataset
   * @throws InvalidInputException if dataset has no pipelines
   */
  @Transactional
  public DataSet publish(UUID id) {
    DataSet dataSet = findByIdOrThrow(id);

    // Validate that dataset has at least one pipeline
    if (dataSet.getPipelines() == null || dataSet.getPipelines().isEmpty()) {
      throw new InvalidInputException(
          "pipelines", id, "DataSet must contain at least one Pipeline before publishing");
    }

    // Generate distributions from pipeline APIs
    String gatewayBaseUrl = gatewayConfigProperties.getBaseUrl();

    dataSet.getPipelines().stream()
        .filter(pipeline -> pipeline.getApis() != null)
        .flatMap(pipeline -> pipeline.getApis().stream())
        .distinct()
        .forEach(
            apiPath -> {
              Distribution distribution = new Distribution();
              distribution.setAccessUrl(gatewayBaseUrl + apiPath);
              distribution.setApiType("SensorThings");
              distribution.setFormat("application/json");
              distribution.setAutoGenerated(true);
              distribution.setDataSet(dataSet);
              dataSet.getDistributions().add(distribution);
            });

    // Update status to FINISHED
    dataSet.setDataSetStatus(DataSetStatus.FINISHED);

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

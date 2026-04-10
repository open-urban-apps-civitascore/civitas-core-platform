package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.connector.OnPublish;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ReleasableStatus;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.repository.PipelineRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import jakarta.validation.groups.Default;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Service for managing {@link DataSource} entities through their full lifecycle (DRAFT to
 * AVAILABLE). Handles connector configuration normalization, encryption of sensitive fields, data
 * structure version linking, and release/unrelease status transitions.
 */
@Service
@RequiredArgsConstructor
public class DataSourceService extends BaseDataEntityService<DataSource, DataSourceInputDTO> {

  private final DataSourceRepository dataSourceRepository;
  private final DataSourceMapper dataSourceMapper;
  private final ConnectorHandlerRegistry connectorHandlerRegistry;
  private final AssignmentFactory assignmentFactory;
  private final DataStructureVersionService dataStructureVersionService;
  private final DataSetRepository dataSetRepository;
  private final PipelineRepository pipelineRepository;

  @Override
  protected DataSourceRepository getRepository() {
    return dataSourceRepository;
  }

  @Override
  protected DataSourceMapper getMapper() {
    return dataSourceMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSource.class.getSimpleName();
  }

  @Override
  protected AssignmentFactory getAssignmentFactory() {
    return assignmentFactory;
  }

  @Override
  protected ReleasableStatus getEntityStatus(DataSource entity) {
    return entity.getDataSourceStatus();
  }

  @Override
  protected void setEntityStatus(DataSource entity, ReleasableStatus status) {
    entity.setDataSourceStatus((DataSourceStatus) status);
  }

  @Override
  protected ReleasableStatus getDraftStatus() {
    return DataSourceStatus.DRAFT;
  }

  @Override
  protected ReleasableStatus getAvailableStatus() {
    return DataSourceStatus.AVAILABLE;
  }

  /**
   * Links the data structure version to the data source after DTO-to-entity conversion. Validates
   * that the referenced version is in AVAILABLE status and its parent data structure is also
   * AVAILABLE.
   *
   * @param entity the data source entity
   * @param input the data source input DTO
   * @return the entity with the data structure version relationship set
   * @throws InvalidInputException if the data structure version is not linkable
   */
  @Override
  protected DataSource postConvertToEntity(DataSource entity, DataSourceInputDTO input) {
    if (input.getDataStructureVersionId() != null) {
      DataStructureVersion dsv =
          dataStructureVersionService.findByIdOrThrow(input.getDataStructureVersionId());
      validateDataStructureVersionLinkable(dsv);
      entity.setDataStructureVersion(dsv);
    } else {
      entity.setDataStructureVersion(null);
    }
    return super.postConvertToEntity(entity, input);
  }

  /**
   * Normalizes and encrypts the connector configuration before creating the data source. Validates
   * that a connector type is provided when configuration is present.
   *
   * @param input the data source creation input
   * @return the input with normalized and encrypted configuration
   * @throws InvalidInputException if configuration is set without a connector type
   */
  @Override
  protected DataSourceInputDTO preProcessCreateInput(DataSourceInputDTO input) {
    if (input.getConfiguration() != null) {
      if (input.getConnectorType() == null) {
        throw new InvalidInputException(
            getEntityName(), (UUID) null, "Cannot set configuration without a connector type");
      }
      ConnectorHandler handler =
          connectorHandlerRegistry.getHandlerOrThrow(input.getConnectorType());
      Map<String, Object> normalized = handler.normalizeAndValidate(input.getConfiguration());
      input.setConfiguration(handler.encryptSensitiveFields(normalized));
    }
    return input;
  }

  /**
   * Handles PUT and PATCH updates: validates connector type changes, normalizes configuration,
   * encrypts sensitive fields, then restores masked placeholders with existing encrypted values.
   */
  @Override
  protected DataSourceInputDTO preProcessUpdateInput(
      DataSourceInputDTO input, DataSource existingEntity) {
    validateConnectorTypeChange(input, existingEntity);
    validateDataStructureVersionChange(input, existingEntity);

    if (input.getConfiguration() != null) {
      ConnectorType type = resolveConnectorType(input, existingEntity);
      if (type == null) {
        throw new InvalidInputException(
            getEntityName(),
            existingEntity.getId(),
            "Cannot set configuration without a connector type");
      }
      ConnectorHandler handler = connectorHandlerRegistry.getHandlerOrThrow(type);

      // Defensive copy: MapStruct's updateEntity does clear() + putAll() on the entity's map.
      // If the DTO shares the same map reference, clear() empties both.
      Map<String, Object> existingConfig = copyConfiguration(existingEntity.getConfiguration());

      Map<String, Object> normalized = handler.normalizeAndValidate(input.getConfiguration());
      // Encrypt first, then restore: masked "********" values get encrypted to a garbage value,
      // which is then overwritten with the original encrypted value from the existing entity.
      Map<String, Object> encrypted = handler.encryptSensitiveFields(normalized);
      if (existingConfig != null) {
        restoreMaskedValues(encrypted, existingConfig, handler, normalized);
      }
      input.setConfiguration(encrypted);
    }
    return input;
  }

  /**
   * Merges configuration for a PATCH operation. Because Jackson's readerForUpdating performs a
   * shallow replacement of the configuration map rather than a deep merge, this method starts from
   * the existing (decrypted) config and overlays only the fields present in the patch.
   *
   * @param patchedDto the DTO produced by Jackson's partial deserialization
   * @param existing the current persisted data source entity
   * @param rawPatch the raw JSON patch node for inspecting which fields were supplied
   */
  public void mergeConfigurationForPatch(
      DataSourceInputDTO patchedDto, DataSource existing, JsonNode rawPatch) {
    JsonNode configPatch = rawPatch.path("configuration");
    Map<String, Object> existingConfig = copyConfiguration(existing.getConfiguration());

    if (configPatch.isMissingNode()) {
      patchedDto.setConfiguration(existingConfig);
      return;
    }

    patchedDto.setConfiguration(mergeConfiguration(existingConfig, patchedDto.getConfiguration()));
  }

  private Map<String, Object> mergeConfiguration(
      Map<String, Object> existingConfig, Map<String, Object> patchConfig) {
    Map<String, Object> merged =
        existingConfig != null ? new HashMap<>(existingConfig) : new HashMap<>();
    if (patchConfig != null) {
      merged.putAll(patchConfig);
    }
    return merged;
  }

  private ConnectorType resolveConnectorType(DataSourceInputDTO input, DataSource existing) {
    return input.getConnectorType() != null
        ? input.getConnectorType()
        : existing.getConnectorType();
  }

  // Defensive copy: MapStruct's updateEntity does clear() + putAll(), which corrupts the original
  // when Hibernate's first-level cache makes entity and input share the same map reference.
  private Map<String, Object> copyConfiguration(Map<String, Object> config) {
    return config != null ? new HashMap<>(config) : null;
  }

  private void validateConnectorTypeChange(DataSourceInputDTO input, DataSource existingEntity) {
    if (existingEntity.getDataSourceStatus() == DataSourceStatus.AVAILABLE
        && input.getConnectorType() != null
        && existingEntity.getConnectorType() != input.getConnectorType()) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot change connector type of an AVAILABLE data source");
    }
  }

  /**
   * Restores masked placeholder values in the encrypted config with the original encrypted values
   * from the existing entity. Checks the pre-encryption (normalized) map to detect "********".
   */
  private void restoreMaskedValues(
      Map<String, Object> encryptedConfig,
      Map<String, Object> existingConfig,
      ConnectorHandler handler,
      Map<String, Object> normalizedConfig) {
    for (String field : handler.getSensitiveFields()) {
      Object preEncrypt = normalizedConfig.get(field);
      Object existing = existingConfig.get(field);
      if (preEncrypt instanceof String s
          && ConnectorHandler.MASKED_VALUE.equals(s)
          && existing != null) {
        encryptedConfig.put(field, existing);
      }
    }
  }

  /**
   * Validates the connector configuration before saving if the data source is in AVAILABLE status.
   *
   * @param entity the data source entity to validate
   * @return the validated entity
   * @throws InvalidInputException if the configuration is invalid for an AVAILABLE data source
   */
  @Override
  protected DataSource preSave(DataSource entity) {
    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      validateConfiguration(entity);
    }
    return entity;
  }

  @Override
  protected void validateRelease(DataSource entity) {
    if (entity.getConnectorType() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Connector type must be set before releasing");
    }

    if (entity.getDataStructureVersion() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Data structure version must be set before releasing");
    }

    validateConfiguration(entity);
  }

  @Override
  protected void validateUnrelease(DataSource entity) {
    validateNotInUse(entity.getId());
  }

  private void validateNotInUse(UUID id) {
    if (pipelineRepository.existsByDataSourcesId(id)) {
      throw new ResourceInUseException(
          getEntityName(),
          id,
          "Cannot unrelease DataSource because it is referenced by a Pipeline.");
    }
  }

  /**
   * Updates metadata of an AVAILABLE data source. Allows name, description, and assignment changes.
   * Technical fields (connector type, configuration, data structure version) can only be changed
   * when the data source is not referenced by any READY or AVAILABLE dataset.
   *
   * @param id the data source ID
   * @param input the partial update input
   * @return the updated data source
   * @throws InvalidInputException if the data source is not AVAILABLE or violates in-use
   *     constraints
   */
  @Override
  @Transactional
  public DataSource updateReleasedMeta(UUID id, DataSourceInputDTO input) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Only data sources in AVAILABLE status can have metadata updated");
    }

    boolean inUse = pipelineRepository.existsByDataSourcesId(id);

    if (inUse) {
      validateInUseConstraints(input, entity);
    }

    if (input.getName() != null) {
      entity.setName(input.getName());
    }
    if (input.getDescription() != null) {
      entity.setDescription(input.getDescription());
    }
    if (input.getAssignments() != null) {
      Set<Assignment> assignments =
          input.getAssignments().stream()
              .distinct()
              .map(dto -> getAssignmentFactory().build(dto))
              .collect(Collectors.toSet());
      entity.setAssignments(assignments);
    }

    if (!inUse) {
      applyTechnicalFields(input, entity);
    }

    return save(entity);
  }

  private void validateInUseConstraints(DataSourceInputDTO input, DataSource entity) {
    if (input.getConnectorType() != null && input.getConnectorType() != entity.getConnectorType()) {
      throw new InvalidInputException(
          getEntityName(),
          entity.getId(),
          "Cannot change connector type of a data source that is in use");
    }
    if (input.getConfiguration() != null) {
      throw new InvalidInputException(
          getEntityName(),
          entity.getId(),
          "Cannot change configuration of a data source that is in use");
    }
    UUID existingDsvId =
        entity.getDataStructureVersion() != null ? entity.getDataStructureVersion().getId() : null;
    if (input.getDataStructureVersionId() != null
        && !input.getDataStructureVersionId().equals(existingDsvId)) {
      throw new InvalidInputException(
          getEntityName(),
          entity.getId(),
          "Cannot change data structure version of a data source that is in use");
    }
  }

  private void applyTechnicalFields(DataSourceInputDTO input, DataSource entity) {
    if (input.getConnectorType() != null) {
      entity.setConnectorType(input.getConnectorType());
    }
    if (input.getDataStructureVersionId() != null) {
      DataStructureVersion dsv =
          dataStructureVersionService.findByIdOrThrow(input.getDataStructureVersionId());
      validateDataStructureVersionLinkable(dsv);
      entity.setDataStructureVersion(dsv);
    }
    if (input.getConfiguration() != null) {
      ConnectorType type = entity.getConnectorType();
      if (type == null) {
        throw new InvalidInputException(
            getEntityName(), entity.getId(), "Cannot set configuration without a connector type");
      }
      ConnectorHandler handler = connectorHandlerRegistry.getHandlerOrThrow(type);

      Map<String, Object> existingConfig = copyConfiguration(entity.getConfiguration());

      Map<String, Object> normalized = handler.normalizeAndValidate(input.getConfiguration());
      Map<String, Object> encrypted = handler.encryptSensitiveFields(normalized);
      if (existingConfig != null) {
        restoreMaskedValues(encrypted, existingConfig, handler, normalized);
      }
      entity.setConfiguration(encrypted);
    }
  }

  /**
   * Prevents deletion of data sources in AVAILABLE status. The data source must be unreleased
   * first.
   *
   * @param id the data source ID
   * @return the data source entity to be deleted
   * @throws InvalidInputException if the data source is in AVAILABLE status
   */
  @Override
  protected DataSource preProcessDelete(UUID id) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Cannot delete a released data source. Unrelease it first.");
    }

    return entity;
  }

  private void validateConfiguration(DataSource entity) {
    if (entity.getConnectorType() == null) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Connector type is required for validation");
    }

    Map<String, Object> config = entity.getConfiguration();
    if (config == null || config.isEmpty()) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Configuration is required for publishing");
    }

    ConnectorHandler handler =
        connectorHandlerRegistry.getHandlerOrThrow(entity.getConnectorType());
    List<String> errors = handler.validate(config, Default.class, OnPublish.class);

    if (!errors.isEmpty()) {
      throw new InvalidInputException(
          getEntityName(), entity.getId(), "Invalid configuration: " + String.join("; ", errors));
    }
  }

  /**
   * Returns the data structure version linked to the given data source, or {@code null} if none is
   * linked.
   *
   * @param dataSourceId the data source ID
   * @return the linked {@link DataStructureVersion}, or {@code null}
   * @throws de.civitascore.portal.util.ResourceNotFoundException if the data source does not exist
   */
  @Transactional(readOnly = true)
  public DataStructureVersion findLinkedDataStructureVersion(UUID dataSourceId) {
    DataSource dataSource = findByIdOrThrow(dataSourceId);
    if (dataSource.getDataStructureVersion() == null) {
      return null;
    }
    return dataStructureVersionService.findByIdOrThrow(
        dataSource.getDataStructureVersion().getId());
  }

  private void validateDataStructureVersionLinkable(DataStructureVersion dsv) {
    if (dsv.getDataStructureVersionStatus() != DataStructureVersionStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(),
          dsv.getId(),
          "DataStructureVersion must be in AVAILABLE status to be linked to a DataSource");
    }
    if (dsv.getDataStructure().getDataStructureStatus() != DataStructureStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(),
          dsv.getId(),
          "The parent DataStructure must be in AVAILABLE status to be linked to a DataSource");
    }
  }

  private void validateDataStructureVersionChange(
      DataSourceInputDTO input, DataSource existingEntity) {
    if (existingEntity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      return;
    }
    UUID existingDsvId =
        existingEntity.getDataStructureVersion() != null
            ? existingEntity.getDataStructureVersion().getId()
            : null;
    if (input.getDataStructureVersionId() != null
        && !input.getDataStructureVersionId().equals(existingDsvId)) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot change data structure version of an AVAILABLE data source");
    }
    if (input.getDataStructureVersionId() == null && existingDsvId != null) {
      throw new InvalidInputException(
          getEntityName(),
          existingEntity.getId(),
          "Cannot remove data structure version from an AVAILABLE data source");
    }
  }
}

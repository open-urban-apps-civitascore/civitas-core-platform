package de.civitascore.portal.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.civitascore.portal.mapper.DataSourceMapper;
import de.civitascore.portal.model.connector.OnPublish;
import de.civitascore.portal.model.embedded.ConnectorType;
import de.civitascore.portal.model.embedded.DataSourceStatus;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.entity.Assignment;
import de.civitascore.portal.model.entity.DataSource;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSourceInputDTO;
import de.civitascore.portal.model.input.DataSourceMetaInputDTO;
import de.civitascore.portal.repository.DataSourceRepository;
import de.civitascore.portal.service.connector.ConnectorHandler;
import de.civitascore.portal.service.connector.ConnectorHandlerRegistry;
import de.civitascore.portal.util.InvalidInputException;
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

@Service
@RequiredArgsConstructor
public class DataSourceService extends BaseDataEntityService<DataSource, DataSourceInputDTO> {

  private final DataSourceRepository dataSourceRepository;
  private final DataSourceMapper dataSourceMapper;
  private final ConnectorHandlerRegistry connectorHandlerRegistry;
  private final ScopedAssignmentBuilderService assignmentBuilderService;
  private final DataStructureVersionService dataStructureVersionService;

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
  protected ScopedAssignmentBuilderService getAssignmentBuilderService() {
    return assignmentBuilderService;
  }

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
    return entity;
  }

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
   * Jackson's readerForUpdating does a shallow merge — the configuration map is replaced, not
   * merged. We need to start from the existing (decrypted) config and overlay the patch fields.
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

  @Override
  protected DataSource preSave(DataSource entity) {
    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      validateConfiguration(entity);
    }
    return entity;
  }

  @Transactional
  public DataSource publish(UUID id) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() != DataSourceStatus.DRAFT) {
      throw new InvalidInputException(
          getEntityName(), id, "Only data sources in DRAFT status can be published");
    }

    if (entity.getConnectorType() == null) {
      throw new InvalidInputException(
          getEntityName(), id, "Connector type must be set before publishing");
    }

    if (entity.getDataStructureVersion() == null) {
      throw new InvalidInputException(
          getEntityName(), id, "Data structure version must be set before publishing");
    }

    validateConfiguration(entity);

    entity.setDataSourceStatus(DataSourceStatus.AVAILABLE);
    return save(entity);
  }

  @Transactional
  public DataSource unpublish(UUID id) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Only data sources in AVAILABLE status can be unpublished");
    }

    entity.setDataSourceStatus(DataSourceStatus.DRAFT);
    return save(entity);
  }

  @Transactional
  public DataSource updatePublishedMeta(UUID id, DataSourceMetaInputDTO input) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() != DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Only data sources in AVAILABLE status can have metadata updated");
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
              .map(dto -> getAssignmentBuilderService().build(dto))
              .collect(Collectors.toSet());
      entity.setAssignments(assignments);
    }

    return save(entity);
  }

  @Override
  protected DataSource preProcessDelete(UUID id) {
    DataSource entity = findByIdOrThrow(id);

    if (entity.getDataSourceStatus() == DataSourceStatus.AVAILABLE) {
      throw new InvalidInputException(
          getEntityName(), id, "Cannot delete a data source in AVAILABLE status");
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

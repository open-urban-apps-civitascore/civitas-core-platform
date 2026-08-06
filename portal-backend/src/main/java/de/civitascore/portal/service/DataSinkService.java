package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.DataStructureStatus;
import de.civitascore.portal.model.embedded.DataStructureVersionStatus;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.entity.DataStructureVersion;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import de.civitascore.portal.util.UniqueConstraintViolationException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSink} entities. A DataSink belongs directly to a {@link DataSet};
 * its {@link de.civitascore.portal.model.entity.Pipeline Pipeline} link is optional and managed by
 * {@link PipelineService} when a Pipeline declares the DataSink in its {@code dataSinkIds}.
 */
@Slf4j
@Service
public class DataSinkService extends BaseService<DataSink, DataSinkInputDTO> {

  /**
   * ASCII-only keeps Java's {@code equalsIgnoreCase} in the uniqueness check from disagreeing with
   * Postgres' own case folding about which two names denote one table.
   */
  private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  /**
   * PostgreSQL truncates a longer identifier instead of rejecting it, which would silently detach
   * the sink from the table it names.
   */
  private static final int MAX_TABLE_NAME_LENGTH = 63;

  private final DataSinkRepository dataSinkRepository;
  private final DataSinkMapper dataSinkMapper;
  private final DataSetRepository dataSetRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final LayerRepository layerRepository;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;

  public DataSinkService(
      DataSinkRepository dataSinkRepository,
      DataSinkMapper dataSinkMapper,
      DataSetRepository dataSetRepository,
      DataStructureVersionRepository dataStructureVersionRepository,
      LayerRepository layerRepository,
      ScopeAccessAuthorizer scopeAccessAuthorizer) {
    this.dataSinkRepository = dataSinkRepository;
    this.dataSinkMapper = dataSinkMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataStructureVersionRepository = dataStructureVersionRepository;
    this.layerRepository = layerRepository;
    this.scopeAccessAuthorizer = scopeAccessAuthorizer;
  }

  @Override
  protected DataSinkRepository getRepository() {
    return dataSinkRepository;
  }

  @Override
  protected DataSinkMapper getMapper() {
    return dataSinkMapper;
  }

  @Override
  protected String getEntityName() {
    return DataSink.class.getSimpleName();
  }

  @Override
  public Optional<DataSink> findById(UUID id) {
    return dataSinkRepository.findByIdWithRelations(id);
  }

  /**
   * Finds a DataSink by ID and verifies that it belongs to the specified dataset.
   *
   * @param id the DataSink ID
   * @param dataSetId the expected parent dataset ID
   * @return the DataSink entity
   * @throws ResourceNotFoundException if the DataSink does not exist or belongs to a different
   *     dataset
   */
  public DataSink findByIdAndDataSetOrThrow(UUID id, UUID dataSetId) {
    DataSink sink = findByIdOrThrow(id);
    if (!dataSetId.equals(sink.getDataSet().getId())) {
      throw new ResourceNotFoundException(getEntityName(), id);
    }
    return sink;
  }

  /**
   * Rejects attempts to change the immutable {@code dataSinkType} of an existing DataSink. Runs for
   * both PUT and PATCH; a PATCH that omits {@code dataSinkType} carries the entity's current type
   * forward via the assembler and passes this check.
   *
   * @throws InvalidInputException if the incoming type differs from the persisted type
   */
  @Override
  protected DataSinkInputDTO preProcessUpdateInput(
      DataSinkInputDTO input, DataSink existingEntity) {
    if (input.getDataSinkType() != null
        && input.getDataSinkType() != existingEntity.getDataSinkType()) {
      throw new InvalidInputException(
          getEntityName(), existingEntity.getId(), "dataSinkType cannot be changed after creation");
    }
    requireDataLossConfirmation(input, existingEntity);
    return input;
  }

  /**
   * A change to {@code tableName} or {@code dataStructureVersionId} rebuilds the sink's backing
   * storage on the next release, discarding all stored data — for POSTGIS a table drop+recreate (no
   * ALTER TABLE), for FROST a re-provisioning of the target entities. If the parent dataset has
   * been provisioned (storage actually exists), such a change requires explicit {@code
   * confirmDataLoss=true}; otherwise the update is rejected with 409. Comparing ids alone is
   * sufficient because the AVAILABLE-only invariant makes a version's model immutable for the
   * sink's lifetime (see {@link #requireExistingDataStructureVersion}).
   */
  private void requireDataLossConfirmation(DataSinkInputDTO input, DataSink existingEntity) {
    if (input.isConfirmDataLoss() || !existingEntity.getDataSet().isProvisioned()) {
      return;
    }

    Map<String, Object> incoming = input.getConfiguration();
    if (incoming == null) {
      return;
    }
    Map<String, Object> current = existingEntity.getConfiguration();

    boolean destructiveChange =
        fieldChanged(incoming, current, "tableName")
            || fieldChanged(incoming, current, "dataStructureVersionId");
    if (destructiveChange) {
      throw new ResourceInUseException(
          getEntityName(),
          existingEntity.getId(),
          "This change rebuilds the sink's table and discards all stored data;"
              + " set confirmDataLoss=true to proceed");
    }
  }

  private static boolean fieldChanged(
      Map<String, Object> incoming, Map<String, Object> current, String key) {
    Object newValue = incoming.get(key);
    Object oldValue = current == null ? null : current.get(key);
    return !Objects.equals(String.valueOf(newValue), String.valueOf(oldValue));
  }

  /**
   * Resolves the parent dataset and validates the type-specific configuration.
   *
   * @throws ResourceNotFoundException if the dataset is not found
   * @throws InvalidInputException if the configuration is invalid for the given type
   */
  @Override
  protected DataSink postConvertToEntity(DataSink entity, DataSinkInputDTO input) {
    DataSet dataSet =
        Optional.ofNullable(input.getDataSetId())
            .flatMap(dataSetRepository::findById)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        DataSet.class.getSimpleName(), input.getDataSetId()));
    entity.setDataSet(dataSet);

    validateConfiguration(input.getDataSinkType(), input.getConfiguration());

    return super.postConvertToEntity(entity, input);
  }

  @Override
  protected DataSink preSave(DataSink entity) {
    validateUniquePostgisTableName(entity);
    return super.preSave(entity);
  }

  /**
   * The dataset's POSTGIS sinks share one schema, so one {@code tableName} is one physical table.
   */
  private void validateUniquePostgisTableName(DataSink entity) {
    if (entity.getDataSinkType() != DataSinkType.POSTGIS) {
      return;
    }
    String tableName = (String) entity.getConfiguration().get("tableName");

    dataSinkRepository.findByDataSetId(entity.getDataSet().getId()).stream()
        .filter(sibling -> !Objects.equals(sibling.getId(), entity.getId()))
        .filter(sibling -> sibling.getDataSinkType() == DataSinkType.POSTGIS)
        .filter(sibling -> tableName.equalsIgnoreCase(siblingTableName(sibling)))
        .findFirst()
        .ifPresent(
            _ -> {
              throw new UniqueConstraintViolationException(
                  duplicateTableNameMessage(entity, tableName));
            });
  }

  /**
   * An update carries the stored {@code tableName} forward when the request does not mention it, so
   * a sink that already collides is rejected by a request that changed nothing about its name. That
   * reads as a bug in the request unless the message points at the stored value.
   */
  private static String duplicateTableNameMessage(DataSink entity, String tableName) {
    if (entity.getId() == null) {
      return "Another POSTGIS DataSink of this dataset already uses tableName '%s';"
              .formatted(tableName)
          + " they would share one physical table";
    }
    return "This DataSink's tableName '%s' is already used by another POSTGIS DataSink of this"
            .formatted(tableName)
        + " dataset; rename it to change this sink";
  }

  private static String siblingTableName(DataSink sink) {
    Map<String, Object> config = sink.getConfiguration();
    return config != null && config.get("tableName") instanceof String tableName ? tableName : null;
  }

  /**
   * Detaches every DataSink belonging to the given pipeline by clearing its {@code pipeline}
   * reference. DataSinks themselves survive — they remain owned by their parent dataset and can be
   * reattached to a different pipeline later.
   *
   * @param pipelineId the pipeline whose DataSinks should be detached
   */
  public void unlinkByPipelineId(UUID pipelineId) {
    dataSinkRepository
        .findByPipelineId(pipelineId)
        .forEach(
            sink -> {
              sink.setPipeline(null);
              dataSinkRepository.save(sink);
            });
  }

  /**
   * Guards DELETE against Layer references.
   *
   * @throws ResourceNotFoundException if the DataSink does not exist
   * @throws ResourceInUseException (409) if a Layer references this DataSink
   */
  @Override
  protected DataSink preProcessDelete(UUID id) {
    DataSink sink =
        findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));

    if (layerRepository.existsByDataSinkId(id)) {
      throw new ResourceInUseException(
          getEntityName(), id, "DataSink is referenced by one or more Layers");
    }

    return sink;
  }

  private void validateConfiguration(DataSinkType type, Map<String, Object> config) {
    switch (type) {
      case FROST -> validateFrostConfiguration(config);
      case POSTGIS -> validatePostgisConfiguration(config);
    }
  }

  /**
   * A FROST configuration is empty (passthrough) or references exactly the mapping's Thing-shaped
   * target structure — any other key would silently be dropped by the deploy engine.
   */
  private void validateFrostConfiguration(Map<String, Object> config) {
    if (config == null || config.isEmpty()) {
      return;
    }
    if (!config.keySet().equals(Set.of("dataStructureVersionId"))) {
      throw new InvalidInputException(
          "DataSink",
          "configuration",
          "FROST sinks accept only an optional dataStructureVersionId");
    }
    requireExistingDataStructureVersion(config.get("dataStructureVersionId"));
  }

  private void validatePostgisConfiguration(Map<String, Object> config) {
    if (config == null) {
      throw new InvalidInputException(
          "DataSink", "configuration", "POSTGIS sinks require a non-null configuration");
    }

    Object tableNameRaw = config.get("tableName");
    if (!(tableNameRaw instanceof String tableName) || tableName.isBlank()) {
      throw new InvalidInputException(
          "DataSink", "configuration.tableName", "tableName is required for POSTGIS sinks");
    }
    if (!TABLE_NAME_PATTERN.matcher(tableName).matches()) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.tableName",
          "tableName must start with a letter or underscore and contain only letters, digits and"
              + " underscores");
    }
    if (tableName.length() > MAX_TABLE_NAME_LENGTH) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.tableName",
          "tableName must be at most " + MAX_TABLE_NAME_LENGTH + " characters");
    }

    Object dsvIdRaw = config.get("dataStructureVersionId");
    if (dsvIdRaw == null) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "dataStructureVersionId is required for POSTGIS sinks");
    }
    requireExistingDataStructureVersion(dsvIdRaw);
  }

  private void requireExistingDataStructureVersion(Object dsvIdRaw) {
    UUID dsvId;
    try {
      dsvId = UUID.fromString(String.valueOf(dsvIdRaw));
    } catch (IllegalArgumentException e) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "dataStructureVersionId must be a valid UUID");
    }

    // Authorization scopes on the parent DataStructure, which is only known after loading the
    // version, so existence is necessarily checked first. A missing version and an unauthorized
    // one must yield the SAME outward failure, otherwise the 400-vs-403 difference is an existence
    // oracle over version ids. Both therefore raise the identical "not available" 400; the real
    // authorization denial is still logged server-side by ScopeAccessAuthorizer for audit.
    DataStructureVersion dsv =
        dataStructureVersionRepository.findById(dsvId).orElseThrow(this::versionNotAvailable);

    // The version references a DATASTRUCTURE-scoped entity, which the DATASET-typed route header
    // cannot cover — authorize the caller against the parent structure. This is the whole guard:
    // DataSinkController is a plain BaseController with no scope filtering of its own.
    try {
      scopeAccessAuthorizer.authorizeReferences(
          ScopeType.DATASTRUCTURE, Set.of(dsv.getDataStructure().getId()));
    } catch (AccessDeniedException e) {
      throw versionNotAvailable();
    }

    // A sink may only reference an AVAILABLE version. This makes the referenced model immutable for
    // the sink's lifetime (an AVAILABLE version cannot go back to DRAFT while a sink uses it), so a
    // same-id reference always means the same model — the data-loss diff can compare ids alone.
    if (dsv.getDataStructureVersionStatus() != DataStructureVersionStatus.AVAILABLE
        || dsv.getDataStructure().getDataStructureStatus() != DataStructureStatus.AVAILABLE) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.dataStructureVersionId",
          "Referenced DataStructureVersion must be in AVAILABLE status");
    }
  }

  private InvalidInputException versionNotAvailable() {
    return new InvalidInputException(
        "DataSink",
        "configuration.dataStructureVersionId",
        "Referenced DataStructureVersion is not available");
  }
}

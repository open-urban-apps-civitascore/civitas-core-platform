package de.civitascore.portal.service;

import de.civitascore.portal.mapper.DataSinkMapper;
import de.civitascore.portal.model.embedded.DataSinkType;
import de.civitascore.portal.model.embedded.ScopeType;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.entity.DataSink;
import de.civitascore.portal.model.input.DataSinkInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.repository.DataSetRepository;
import de.civitascore.portal.repository.DataSinkRepository;
import de.civitascore.portal.repository.DataStructureVersionRepository;
import de.civitascore.portal.repository.LayerRepository;
import de.civitascore.portal.security.ScopeAccessAuthorizer;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceInUseException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link DataSink} entities. A DataSink belongs directly to a {@link DataSet};
 * its {@link de.civitascore.portal.model.entity.Pipeline Pipeline} link is optional and managed by
 * {@link PipelineService} when a Pipeline declares the DataSink in its {@code dataSinkIds}. The
 * type-specific configuration document lives in the Model Forge registry, pinned by the sink's
 * {@code configurationUrn}.
 */
@Slf4j
@Service
public class DataSinkService extends BaseService<DataSink, DataSinkInputDTO> {

  private final DataSinkRepository dataSinkRepository;
  private final DataSinkMapper dataSinkMapper;
  private final DataSetRepository dataSetRepository;
  private final LayerRepository layerRepository;
  private final DataStructureVersionRepository dataStructureVersionRepository;
  private final ModelRegistryGateway modelRegistryGateway;
  private final ScopeAccessAuthorizer scopeAccessAuthorizer;

  public DataSinkService(
      DataSinkRepository dataSinkRepository,
      DataSinkMapper dataSinkMapper,
      DataSetRepository dataSetRepository,
      DataStructureVersionRepository dataStructureVersionRepository,
      LayerRepository layerRepository,
      ModelRegistryGateway modelRegistryGateway,
      ScopeAccessAuthorizer scopeAccessAuthorizer) {
    this.dataSinkRepository = dataSinkRepository;
    this.dataSinkMapper = dataSinkMapper;
    this.dataSetRepository = dataSetRepository;
    this.dataStructureVersionRepository = dataStructureVersionRepository;
    this.layerRepository = layerRepository;
    this.modelRegistryGateway = modelRegistryGateway;
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
   * A change to {@code tableName} or the referenced {@code element} rebuilds the sink's backing
   * storage on the next release, discarding all stored data — for POSTGIS a table drop+recreate (no
   * ALTER TABLE), for FROST a re-provisioning of the target entities. If the parent dataset has
   * been provisioned (storage actually exists), such a change requires explicit {@code
   * confirmDataLoss=true}; otherwise the update is rejected with 409. The current configuration is
   * read back from the Model Forge registry (the sink no longer stores it on the entity). Comparing
   * the {@code element} URN alone is sufficient because it is a versioned CORE URN whose content is
   * immutable — a different model always yields a different URN.
   */
  private void requireDataLossConfirmation(DataSinkInputDTO input, DataSink existingEntity) {
    if (input.isConfirmDataLoss() || !existingEntity.getDataSet().isProvisioned()) {
      return;
    }

    Map<String, Object> incoming = input.getConfiguration();
    if (incoming == null) {
      return;
    }
    Map<String, Object> current =
        existingEntity.getConfigurationUrn() == null
            ? null
            : modelRegistryGateway
                .fetchPayload(existingEntity.getConfigurationUrn())
                .map(ModelRegistryGateway.RegistryDocument::content)
                .orElse(null);

    boolean destructiveChange =
        fieldChanged(incoming, current, "tableName") || fieldChanged(incoming, current, "element");
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
   * Resolves the parent dataset, validates the type-specific configuration and stores it in the
   * Model Forge registry, mirroring the assigned pin onto the shell.
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
    storeConfigurationInRegistry(entity, input);

    return super.postConvertToEntity(entity, input);
  }

  /**
   * Stores the validated configuration in the Model Forge registry (kind {@code DATA_SINK}) and
   * mirrors the assigned pin onto the shell. Every DataSink — including a passthrough FROST sink
   * whose only content is its {@code connectionType} — is a first-class CORE DataSink artifact that
   * pipelines reference by {@code sinkRef}, so it always owns a {@code configurationUrn}. A {@code
   * null} configuration (a metadata-only update that does not touch the config) keeps the existing
   * pin untouched; an empty configuration still stores the stamped {@code connectionType} (all a
   * FROST sink needs). The {@code element} URN soft reference is preserved verbatim inside the
   * stored payload, where Model Forge records it as a {@code datasink-element} dependency edge onto
   * the referenced model.
   *
   * @param entity the data sink entity
   * @param input the input DTO carrying the configuration
   */
  private void storeConfigurationInRegistry(DataSink entity, DataSinkInputDTO input) {
    Map<String, Object> configuration = input.getConfiguration();
    DataSinkType type = input.getDataSinkType();
    // No configuration and no type to identify the sink → nothing to store (clear the pin). A null
    // configuration with a known type is a metadata-only update: keep the existing pin.
    if (type == null) {
      entity.setConfigurationUrn(null);
      return;
    }
    if (configuration == null) {
      return;
    }
    // The host supplies only content: the connectionType (from the sink type) plus any
    // type-specific
    // fields. Model Forge owns the artifact's self-description — it stamps $schema and id on every
    // write (see EmbeddedModelForgeOperations.saveArtifact) so the stored payload satisfies
    // datasink.schema.json. isUnchanged's comparable() ignores those stamps, so re-versions only
    // mint when a real field changed.
    Map<String, Object> payload = new LinkedHashMap<>(configuration);
    payload.put("connectionType", type.name().toLowerCase(Locale.ROOT));
    if (entity.getConfigurationUrn() != null
        && modelRegistryGateway.isUnchanged(entity.getConfigurationUrn(), payload, null)) {
      // Unchanged configuration keeps the existing pin — no new registry version.
      return;
    }
    ModelRegistryGateway.ModelPin pin =
        modelRegistryGateway.storePayload(
            PayloadKind.DATA_SINK,
            Optional.ofNullable(entity.getConfigurationLogicalUrn()),
            deriveArtifactName(input, configuration),
            payload,
            null);
    if (entity.getConfigurationLogicalUrn() == null) {
      entity.setConfigurationLogicalUrn(pin.logicalUrn());
    }
    entity.setConfigurationUrn(pin.versionedUrn());
  }

  /**
   * Derives a readable registry artifact name for the sink configuration: the POSTGIS table name
   * when present, otherwise the sink type. Model Forge appends a UUID, so equal names never
   * collide.
   */
  private static String deriveArtifactName(DataSinkInputDTO input, Map<String, Object> config) {
    if (config.get("tableName") instanceof String tableName && !tableName.isBlank()) {
      return tableName;
    }
    return input.getDataSinkType() != null
        ? input.getDataSinkType().name().toLowerCase() + "-sink"
        : "datasink";
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

  /**
   * After the sink row is deleted, delete the backing configuration artifact from Model Forge in
   * the same transaction. No-op when no configuration was ever stored (FROST sinks).
   *
   * @param entity the deleted data sink
   */
  @Override
  protected void postDelete(DataSink entity) {
    if (entity != null && entity.getConfigurationLogicalUrn() != null) {
      modelRegistryGateway.deletePayload(entity.getConfigurationLogicalUrn());
    }
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
    if (!config.keySet().equals(Set.of("element"))) {
      throw new InvalidInputException(
          "DataSink", "configuration", "FROST sinks accept only an optional element URN");
    }
    requireExistingElement(config.get("element"));
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

    Object elementRaw = config.get("element");
    if (elementRaw == null) {
      throw new InvalidInputException(
          "DataSink", "configuration.element", "element is required for POSTGIS sinks");
    }
    requireExistingElement(elementRaw);
  }

  /**
   * Validates the {@code element} soft reference: it must be a non-blank CORE URN that resolves to
   * an existing model (Element) in the registry. Model Forge tracks this URN as a {@code
   * datasink-element} dependency edge when the configuration is stored, which is what the in-use
   * guard on {@code DataStructureVersion} queries.
   */
  private void requireExistingElement(Object elementRaw) {
    if (!(elementRaw instanceof String urn) || urn.isBlank()) {
      throw new InvalidInputException(
          "DataSink", "configuration.element", "element must be a CORE URN");
    }
    if (modelRegistryGateway.fetchModel(urn).isEmpty()) {
      throw new InvalidInputException(
          "DataSink",
          "configuration.element",
          "Referenced Element not found in the model registry: " + urn);
    }
    authorizeReferencedStructure(urn);
  }

  /**
   * Authorizes the caller against the DataStructure that owns the referenced {@code element} URN.
   *
   * <p>The sink's {@code element} is a versioned CORE {@code :datastructure:} URN (the mapping's
   * target structure). It is resolved to the owning portal DataStructure via that structure's
   * {@code modelUrn}, matched version-agnostically since every version of a structure shares the
   * same parent. The DataStructure is DATASTRUCTURE-scoped, which the DATASET-typed route header
   * cannot cover — so DataSinkService authorizes the reference here (DataSinkController is a plain
   * BaseController with no scope filtering of its own). A URN that resolves to no known
   * DataStructure and an unauthorized one must yield the SAME outward failure, otherwise the
   * difference is an existence oracle over structure URNs; both raise the identical "not available"
   * 400 and the real denial is logged by {@link ScopeAccessAuthorizer} for audit.
   */
  private void authorizeReferencedStructure(String elementUrn) {
    UUID dataStructureId =
        dataStructureVersionRepository
            .findFirstByModelUrnStartingWith(modelRegistryGateway.logicalUrn(elementUrn) + ":")
            .map(dsv -> dsv.getDataStructure().getId())
            .orElseThrow(this::referencedStructureNotAvailable);
    try {
      scopeAccessAuthorizer.authorizeReferences(ScopeType.DATASTRUCTURE, Set.of(dataStructureId));
    } catch (AccessDeniedException e) {
      throw referencedStructureNotAvailable();
    }
  }

  private InvalidInputException referencedStructureNotAvailable() {
    return new InvalidInputException(
        "DataSink", "configuration.element", "Referenced DataStructure is not available");
  }
}

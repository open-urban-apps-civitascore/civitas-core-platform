package de.civitascore.portal.service;

import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.model.input.MappingInputDTO;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.RegistryDocument;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * First-class Mapping artifacts, nested under the DataSet they belong to. A Mapping is a CORE
 * document (a field-to-field transform between two DataStructures) whose CONTENT lives only in
 * Model Forge; the host is a thin pass-through that reads only the envelope (a display title) and
 * NEVER inspects the mapping rules. Mappings are referenced by URN from pipelines. Model Forge
 * validates the document against {@code mapping.schema.json} and stamps its {@code $schema} +
 * {@code id} on store.
 *
 * <p>A Mapping is addressed by the UUID of its CORE URN, and every operation resolves that id among
 * the members of the DataSet's manifest only. The DataSet-typed route grant covers exactly those
 * members, so a caller cannot reach a mapping of another DataSet, and a foreign mapping answers
 * like an unknown one.
 */
@Service
@RequiredArgsConstructor
public class MappingService {

  private static final String ENTITY_NAME = "Mapping";

  /** CORE artifact-type segment of a Mapping, as it appears in the dependency graph. */
  private static final String MAPPING_ARTIFACT_TYPE = "mapping";

  private static final String POSITIONS = "positions";

  private static final TypeReference<Map<String, Object>> CONTENT = new TypeReference<>() {};

  private final ModelRegistryGateway registry;
  private final DataSetService dataSetService;
  private final DataSetMutationGuard dataSetMutationGuard;
  private final ObjectMapper objectMapper;

  /** A Mapping as the list names it. */
  public record MappingSummary(UUID mappingId, String logicalUrn, String title) {}

  /**
   * A stored version of a Mapping.
   *
   * @param mapping the authored content with the editor's node {@code positions}
   */
  public record StoredMapping(ModelPin pin, MappingInputDTO mapping) {}

  private record Document(Map<String, Object> content, Map<String, Object> styles) {}

  /**
   * The Mappings of this DataSet, in manifest order.
   *
   * @throws InvalidInputException when the DataSet has no manifest
   */
  public List<MappingSummary> list(UUID dataSetId) {
    return members(dataSetWithManifestOrThrow(dataSetId).getManifestLogicalUrn()).stream()
        .map(member -> new MappingSummary(member.id(), member.logicalUrn(), titleOf(member)))
        .toList();
  }

  /**
   * The current version of a Mapping of this DataSet, with the editor's node layout, which the
   * editor needs to restore a saved diagram.
   *
   * @throws ResourceNotFoundException when the Mapping is no member of this DataSet
   */
  public StoredMapping get(UUID dataSetId, UUID mappingId) {
    ModelPin member =
        currentMemberOrThrow(
            dataSetWithManifestOrThrow(dataSetId).getManifestLogicalUrn(), mappingId);
    MappingInputDTO mapping =
        registry
            .fetchPayload(member.versionedUrn())
            .map(this::toMapping)
            .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, mappingId));
    return new StoredMapping(member, mapping);
  }

  /**
   * Stores a new Mapping and links it into the DataSet's manifest, which is what makes it reachable
   * through this DataSet afterwards.
   */
  public StoredMapping create(UUID dataSetId, MappingInputDTO mapping) {
    DataSet dataSet = dataSetWithManifestOrThrow(dataSetId);
    dataSetMutationGuard.requireMutable(dataSet, "create " + ENTITY_NAME);
    return store(dataSet.getManifestLogicalUrn(), Optional.empty(), mapping, toDocument(mapping));
  }

  /**
   * Stores a new version of a Mapping of this DataSet. A Mapping whose content and node layout
   * equal its current version keeps that version.
   *
   * @throws ResourceNotFoundException when the Mapping is no member of this DataSet
   */
  public StoredMapping update(UUID dataSetId, UUID mappingId, MappingInputDTO mapping) {
    DataSet dataSet = dataSetWithManifestOrThrow(dataSetId);
    String manifestUrn = dataSet.getManifestLogicalUrn();
    ModelPin member = currentMemberOrThrow(manifestUrn, mappingId);
    dataSetMutationGuard.requireMutable(dataSet, "update " + ENTITY_NAME);
    Document document = toDocument(mapping);
    if (registry.isUnchanged(member.versionedUrn(), document.content(), document.styles())) {
      return new StoredMapping(member, mapping);
    }
    return store(manifestUrn, Optional.of(member.logicalUrn()), mapping, document);
  }

  /**
   * Deletes a Mapping of this DataSet with all its versions. Model Forge rejects the delete while
   * another artifact still references the mapping — a pipeline's {@code mappingRef}, for instance —
   * and the caller removes that reference at its own artifact first. A pipeline deleted on its own
   * takes with it the mappings no other pipeline uses.
   *
   * @throws ResourceNotFoundException when the Mapping is no member of this DataSet
   */
  public void delete(UUID dataSetId, UUID mappingId) {
    DataSet dataSet = dataSetWithManifestOrThrow(dataSetId);
    ModelPin member = currentMemberOrThrow(dataSet.getManifestLogicalUrn(), mappingId);
    dataSetMutationGuard.requireMutable(dataSet, "delete " + ENTITY_NAME);
    registry.deletePayload(member.logicalUrn());
  }

  /**
   * The editor's UI-only node layout ({@code positions}) goes into {@code x-ui-styles}; the rules
   * in {@code fields} reach Model Forge unchanged.
   */
  private Document toDocument(MappingInputDTO mapping) {
    requireResolvableEndpoint(mapping.getSource(), "source");
    requireResolvableEndpoint(mapping.getTarget(), "target");
    Map<String, Object> content = objectMapper.convertValue(mapping, CONTENT);
    Object positions = content.remove(POSITIONS);
    return new Document(content, positions == null ? null : Map.of(POSITIONS, positions));
  }

  private StoredMapping store(
      String manifestUrn, Optional<String> logicalUrn, MappingInputDTO mapping, Document document) {
    ModelPin pin =
        registry.storePayload(
            PayloadKind.MAPPING,
            logicalUrn,
            // A Mapping has no portal row, so Model Forge mints its UUID.
            null,
            deriveName(mapping),
            document.content(),
            document.styles(),
            manifestUrn);
    return new StoredMapping(pin, mapping);
  }

  private MappingInputDTO toMapping(RegistryDocument stored) {
    Map<String, Object> document = new LinkedHashMap<>(stored.content());
    if (stored.styles() != null && stored.styles().get(POSITIONS) != null) {
      document.put(POSITIONS, stored.styles().get(POSITIONS));
    }
    return objectMapper.convertValue(document, MappingInputDTO.class);
  }

  /**
   * A mapping's {@code source} and {@code target} name the DataStructures it maps between. Both are
   * optional — a draft need not have picked them yet — but one that is present has to be pinned to
   * a SemVer version, the form the deployment accepts ({@link CoreUrn}), and has to resolve:
   * otherwise the mapping pins a structure, or a version of one, that does not exist, and the
   * reference is only discovered when the dataset is released and the deployment engine tries to
   * read the schema. A URN that does not resolve and one the caller may not see are reported
   * identically, so the difference is not an existence oracle over structure URNs.
   */
  private void requireResolvableEndpoint(String urn, String field) {
    if (urn == null) {
      return;
    }
    if (!CoreUrn.isValid(urn)) {
      throw new InvalidInputException(
          ENTITY_NAME, field, field + " must be a CORE URN pinned to a version");
    }
    if (registry.fetchModel(urn).isEmpty()) {
      throw new InvalidInputException(ENTITY_NAME, field, "Referenced model is not available");
    }
  }

  private static String deriveName(MappingInputDTO mapping) {
    String title = mapping.getTitle();
    return title != null && !title.isBlank() ? title : "mapping";
  }

  private String titleOf(ModelPin member) {
    return registry
        .fetchPayload(member.versionedUrn())
        .map(stored -> toMapping(stored).getTitle())
        .orElse(null);
  }

  private DataSet dataSetWithManifestOrThrow(UUID dataSetId) {
    DataSet dataSet = dataSetService.findByIdOrThrow(dataSetId);
    if (dataSet.getManifestLogicalUrn() == null) {
      throw new InvalidInputException(
          "DataSet", dataSetId, "DataSet has no manifest to hold mappings");
    }
    return dataSet;
  }

  private List<ModelPin> members(String manifestUrn) {
    return registry.dependencyPinsOfType(manifestUrn, MAPPING_ARTIFACT_TYPE);
  }

  /** The manifest's pin of the Mapping, which is its current version. */
  private ModelPin currentMemberOrThrow(String manifestUrn, UUID mappingId) {
    return members(manifestUrn).stream()
        .filter(member -> mappingId.equals(member.id()))
        .findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, mappingId));
  }
}

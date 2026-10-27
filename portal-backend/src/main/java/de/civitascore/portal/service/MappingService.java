package de.civitascore.portal.service;

import de.civitascore.configadapter.model.dataset.CoreUrn;
import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.ModelRegistryGateway.ModelPin;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

  private final ModelRegistryGateway registry;
  private final DataSetService dataSetService;

  /** A Mapping as the list names it. */
  public record MappingSummary(UUID mappingId, String logicalUrn, String title) {}

  /**
   * A stored version of a Mapping.
   *
   * @param document the authored content with the editor's node {@code positions}
   */
  public record StoredMapping(UUID mappingId, ModelPin pin, Map<String, Object> document) {}

  /**
   * The Mappings of this DataSet, in manifest order.
   *
   * @throws InvalidInputException when the DataSet has no manifest
   */
  public List<MappingSummary> list(UUID dataSetId) {
    Set<String> seen = new HashSet<>();
    return members(manifestUrnOrThrow(dataSetId)).stream()
        .filter(member -> seen.add(member.logicalUrn()))
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
    ModelPin member = currentMemberOrThrow(manifestUrnOrThrow(dataSetId), mappingId);
    Map<String, Object> document =
        registry
            .fetchPayload(member.versionedUrn())
            .map(
                stored ->
                    withPositions(
                        stored.content(),
                        stored.styles() == null ? null : stored.styles().get(POSITIONS)))
            .orElseThrow(() -> new ResourceNotFoundException(ENTITY_NAME, mappingId));
    return new StoredMapping(mappingId, member, document);
  }

  /**
   * Stores a new Mapping and links it into the DataSet's manifest, which is what makes it reachable
   * through this DataSet afterwards.
   */
  public StoredMapping create(UUID dataSetId, Map<String, Object> doc) {
    return store(manifestUrnOrThrow(dataSetId), Optional.empty(), doc);
  }

  /**
   * Stores a new version of a Mapping of this DataSet, also when the content did not change.
   *
   * @throws ResourceNotFoundException when the Mapping is no member of this DataSet
   */
  public StoredMapping update(UUID dataSetId, UUID mappingId, Map<String, Object> doc) {
    String manifestUrn = manifestUrnOrThrow(dataSetId);
    String logicalUrn = currentMemberOrThrow(manifestUrn, mappingId).logicalUrn();
    return store(manifestUrn, Optional.of(logicalUrn), doc);
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
    ModelPin member = currentMemberOrThrow(manifestUrnOrThrow(dataSetId), mappingId);
    registry.deletePayload(member.logicalUrn());
  }

  /**
   * The editor's UI-only node layout ({@code positions}) is split off the content into {@code
   * x-ui-styles}; everything else is passed through opaquely to Model Forge.
   */
  private StoredMapping store(
      String manifestUrn, Optional<String> logicalUrn, Map<String, Object> doc) {
    Map<String, Object> content = new LinkedHashMap<>(doc);
    Object positions = content.remove(POSITIONS);
    Map<String, Object> styles = positions == null ? null : Map.of(POSITIONS, positions);
    requireResolvableEndpoint(content, "source");
    requireResolvableEndpoint(content, "target");
    ModelPin pin =
        registry.storePayload(
            PayloadKind.MAPPING,
            logicalUrn,
            // A Mapping has no portal row, so Model Forge mints its UUID.
            null,
            deriveName(content),
            content,
            styles,
            manifestUrn);
    return new StoredMapping(pin.id(), pin, withPositions(content, positions));
  }

  private static Map<String, Object> withPositions(Map<String, Object> content, Object positions) {
    Map<String, Object> document = new LinkedHashMap<>(content);
    if (positions != null) {
      document.put(POSITIONS, positions);
    }
    return document;
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
  private void requireResolvableEndpoint(Map<String, Object> content, String field) {
    Object raw = content.get(field);
    if (raw == null) {
      return;
    }
    if (!(raw instanceof String urn) || !CoreUrn.isValid(urn)) {
      throw new InvalidInputException(
          ENTITY_NAME, field, field + " must be a CORE URN pinned to a version");
    }
    if (registry.fetchModel(urn).isEmpty()) {
      throw new InvalidInputException(ENTITY_NAME, field, "Referenced model is not available");
    }
  }

  private static String deriveName(Map<String, Object> doc) {
    Object title = doc.get("title");
    return title instanceof String s && !s.isBlank() ? s : "mapping";
  }

  private String titleOf(ModelPin member) {
    return registry
        .fetchPayload(member.versionedUrn())
        .map(stored -> stored.content().get("title"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse(null);
  }

  /** The DataSet's manifest URN, which every mapping of that DataSet is a member of. */
  private String manifestUrnOrThrow(UUID dataSetId) {
    DataSet dataSet = dataSetService.findByIdOrThrow(dataSetId);
    String manifestUrn = dataSet.getManifestLogicalUrn();
    if (manifestUrn == null) {
      throw new InvalidInputException(
          "DataSet", dataSetId, "DataSet has no manifest to hold mappings");
    }
    return manifestUrn;
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

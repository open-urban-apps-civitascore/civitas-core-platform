package de.civitascore.portal.service;

import de.civitascore.portal.model.entity.DataSet;
import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import de.civitascore.portal.util.InvalidInputException;
import de.civitascore.portal.util.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * First-class Mapping artifacts, nested under the DataSet they belong to. A Mapping is a CORE
 * document (a field-to-field transform between two DataStructures) whose CONTENT lives only in
 * Model Forge; the host is a thin pass-through that reads only the envelope (a display title) and
 * NEVER inspects the mapping rules. Mappings are referenced by URN from pipelines. Model Forge
 * validates the document against {@code mapping.schema.json} and stamps its {@code $schema} +
 * {@code id} on store.
 *
 * <p>Every operation is scoped to a DataSet: the mapping is a member of that DataSet's manifest, so
 * the DataSet-typed route grant covers it and a caller cannot reach a mapping of another DataSet.
 * Membership is checked version-agnostically, because a mapping is addressed by either its logical
 * or a versioned URN and every version shares the same membership.
 */
@Service
public class MappingService {

  private static final String ENTITY_NAME = "Mapping";

  /** CORE artifact-type segment of a Mapping, as it appears in the dependency graph. */
  private static final String MAPPING_ARTIFACT_TYPE = "mapping";

  private final ModelRegistryGateway registry;
  private final DataSetService dataSetService;

  /**
   * The namespace the registry mints into, read from the registry's own configuration so the two
   * cannot drift apart.
   */
  private final String urnScope;

  private final String urnOwner;
  private final String urnDomain;

  public MappingService(
      ModelRegistryGateway registry,
      DataSetService dataSetService,
      @Value("${model-forge.urn.scope:}") String urnScope,
      @Value("${model-forge.urn.owner:}") String urnOwner,
      @Value("${model-forge.urn.domain:}") String urnDomain) {
    this.registry = registry;
    this.dataSetService = dataSetService;
    this.urnScope = urnScope;
    this.urnOwner = urnOwner;
    this.urnDomain = urnDomain;
  }

  /**
   * Stores a mapping document under a DataSet — creates a new artifact when {@code logicalUrn} is
   * null, otherwise versions the existing one — and returns the assigned URN pins. A new mapping is
   * linked into the DataSet's manifest as it is stored, which is what makes it reachable through
   * this DataSet afterwards. The editor's UI-only node layout ({@code positions}) is split off the
   * content into {@code x-ui-styles}; everything else is passed through opaquely to Model Forge.
   *
   * @throws ResourceNotFoundException when versioning a mapping that is not a member of this
   *     DataSet
   */
  public ModelRegistryGateway.ModelPin store(
      UUID dataSetId, String logicalUrn, Map<String, Object> doc) {
    requireOwnNamespace(logicalUrn);
    String manifestUrn = manifestUrnOrThrow(dataSetId);
    if (logicalUrn != null) {
      requireMemberOfDataSet(manifestUrn, logicalUrn);
    }
    Map<String, Object> content = new LinkedHashMap<>(doc == null ? Map.of() : doc);
    content.remove("logicalUrn");
    Object positions = content.remove("positions");
    Map<String, Object> styles =
        positions instanceof Map<?, ?> ? Map.of("positions", positions) : null;
    requireResolvableEndpoint(content, "source");
    requireResolvableEndpoint(content, "target");
    return registry.storePayload(
        PayloadKind.MAPPING,
        Optional.ofNullable(logicalUrn),
        deriveName(content),
        content,
        styles,
        manifestUrn);
  }

  /**
   * The mapping read back from Model Forge — its rules plus the editor's node layout, which is
   * stored alongside the content and has to be served with it for the editor to restore a saved
   * diagram. Empty when the mapping does not exist or is not a member of this DataSet; the two are
   * deliberately indistinguishable, otherwise the difference is an existence oracle over mapping
   * URNs.
   */
  public Optional<Map<String, Object>> get(UUID dataSetId, String urn) {
    if (!isMemberOfDataSet(manifestUrnOrThrow(dataSetId), urn)) {
      return Optional.empty();
    }
    return registry
        .fetchPayload(urn)
        .map(
            document -> {
              Map<String, Object> result = new LinkedHashMap<>(document.content());
              if (document.styles() != null && document.styles().get("positions") != null) {
                result.put("positions", document.styles().get("positions"));
              }
              return result;
            });
  }

  /**
   * Deletes a Mapping artifact of this DataSet by its (logical or versioned) CORE URN. Model Forge
   * rejects the delete while another artifact still references the mapping — a pipeline's {@code
   * mappingRef}, for instance — and the caller removes that reference at its own artifact first. A
   * pipeline deleted on its own takes with it the mappings no other pipeline uses.
   *
   * @throws ResourceNotFoundException when the mapping is not a member of this DataSet
   */
  public void delete(UUID dataSetId, String urn) {
    requireMemberOfDataSet(manifestUrnOrThrow(dataSetId), urn);
    registry.deletePayload(urn);
  }

  /**
   * A mapping's {@code source} and {@code target} name the DataStructures it maps between. Both are
   * optional — a draft need not have picked them yet — but one that is present has to resolve:
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
    if (!(raw instanceof String urn) || urn.isBlank()) {
      throw new InvalidInputException(ENTITY_NAME, field, field + " must be a CORE URN");
    }
    if (registry.fetchModel(urn).isEmpty()) {
      throw new InvalidInputException(
          ENTITY_NAME, field, "Referenced model is not available: " + urn);
    }
  }

  private static String deriveName(Map<String, Object> doc) {
    Object title = doc.get("title");
    return title instanceof String s && !s.isBlank() ? s : "mapping";
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

  private void requireMemberOfDataSet(String manifestUrn, String urn) {
    if (!isMemberOfDataSet(manifestUrn, urn)) {
      throw new ResourceNotFoundException(ENTITY_NAME, urn);
    }
  }

  /**
   * The logical URNs of this DataSet's mappings.
   *
   * <p>A mapping has no host row and is addressed by its URN alone, so without this a mapping whose
   * pipeline no longer names it cannot be found, and the delete that would clear it has no address
   * to aim at.
   *
   * @throws ResourceNotFoundException when the DataSet has no manifest
   */
  public List<String> list(UUID dataSetId) {
    return registry
        .dependencyUrnsOfType(manifestUrnOrThrow(dataSetId), MAPPING_ARTIFACT_TYPE)
        .stream()
        .map(registry::logicalUrn)
        .distinct()
        .toList();
  }

  /**
   * Whether {@code urn} names a mapping of this DataSet. Compared on logical URNs so a versioned
   * and a logical URN of the same mapping both resolve to the same membership.
   */
  private boolean isMemberOfDataSet(String manifestUrn, String urn) {
    if (urn == null || urn.isBlank()) {
      return false;
    }
    String logical = registry.logicalUrn(urn);
    return registry.dependencyUrnsOfType(manifestUrn, MAPPING_ARTIFACT_TYPE).stream()
        .anyMatch(member -> registry.logicalUrn(member).equals(logical));
  }

  /**
   * A mapping has no host row to pin it to, so the URN of the artifact to version arrives in the
   * request body. It is therefore caller input and has to name this registry's own namespace —
   * otherwise a request could create an artifact anywhere in the registry, or silently create a
   * second mapping from a mistyped URN. Rejected when the namespace is unknown, since an unchecked
   * body-supplied URN is what this guards against.
   */
  private void requireOwnNamespace(String logicalUrn) {
    if (logicalUrn == null) {
      return;
    }
    if (urnScope.isBlank() || urnOwner.isBlank() || urnDomain.isBlank()) {
      throw new InvalidInputException(
          ENTITY_NAME,
          "logicalUrn",
          "Cannot accept a mapping URN: the registry namespace is unset");
    }
    String ownPrefix = "urn:core:%s:%s:mapping:%s:".formatted(urnScope, urnOwner, urnDomain);
    if (!logicalUrn.startsWith(ownPrefix)) {
      throw new InvalidInputException(
          ENTITY_NAME,
          "logicalUrn",
          "Mapping URN must name this registry's own namespace (%s...): %s"
              .formatted(ownPrefix, logicalUrn));
    }
  }
}

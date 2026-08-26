package de.civitascore.portal.service;

import de.civitascore.portal.modelregistry.ModelRegistryGateway;
import de.civitascore.portal.modelregistry.PayloadKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * First-class Mapping artifacts. A Mapping is a CORE document (a field-to-field transform between
 * two DataStructures) whose CONTENT lives only in Model Forge; the host is a thin pass-through that
 * reads only the envelope (a display title) and NEVER inspects the mapping rules. Mappings are
 * referenced by URN from pipelines and — in future — maintained standalone. Model Forge validates
 * the document against {@code mapping.schema.json} and stamps its {@code $schema} + {@code id} on
 * store.
 */
@Service
public class MappingService {

  private final ModelRegistryGateway registry;

  public MappingService(ModelRegistryGateway registry) {
    this.registry = registry;
  }

  /**
   * Stores a mapping document — creates a new artifact when {@code logicalUrn} is null, otherwise
   * versions the existing one — and returns the assigned URN pins. The editor's UI-only node layout
   * ({@code positions}) is split off the content into {@code x-ui-styles}; everything else is
   * passed through opaquely to Model Forge.
   */
  public ModelRegistryGateway.ModelPin store(String logicalUrn, Map<String, Object> doc) {
    AuthoredDocument document = authored(doc);
    return registry.storePayload(
        PayloadKind.MAPPING,
        Optional.ofNullable(logicalUrn),
        document.name(),
        document.content(),
        document.styles());
  }

  /**
   * Imports the authored mapping document at its catalogue-declared logical URN through Model
   * Forge's envelope door: created on first install (identity kept), reused when an identical
   * mapping is already installed, refused (409) when the identity holds different content. The
   * document is split exactly like {@link #store}, so both paths agree on what "identical" means.
   */
  public ModelRegistryGateway.EnvelopeImportResult importAt(
      String logicalUrn, Map<String, Object> doc) {
    AuthoredDocument document = authored(doc);
    return registry.importPayloadAt(
        PayloadKind.MAPPING, logicalUrn, document.content(), document.styles());
  }

  /**
   * The versioned URN of the mapping's current version — what a pipeline node should pin. Empty
   * when no mapping exists at the URN.
   */
  public Optional<String> currentVersionedUrn(String urn) {
    return registry.currentModelUrn(urn);
  }

  /**
   * Whether a mapping artifact exists at this (logical or versioned) CORE URN. Used by the bundle
   * import to tell "create at the authored URN" from "this identity is already installed".
   */
  public boolean exists(String urn) {
    return registry.fetchPayload(urn).isPresent();
  }

  /**
   * Whether the artifact at {@code urn} already holds exactly this authored document. Deliberately
   * routed through the same split {@link #store} uses: a reuse decision made on a differently split
   * document would disagree with what a write actually produces, and every re-install of an
   * unchanged bundle would be misread as a conflict.
   */
  public boolean isUnchanged(String urn, Map<String, Object> doc) {
    AuthoredDocument document = authored(doc);
    return registry.isUnchanged(urn, document.content(), document.styles());
  }

  /** The mapping's content (rules), read back from Model Forge, or empty when it does not exist. */
  public Optional<Map<String, Object>> get(String urn) {
    return registry.fetchPayload(urn).map(ModelRegistryGateway.RegistryDocument::content);
  }

  /**
   * Deletes a Mapping artifact by its (logical or versioned) CORE URN. Without {@code force} the
   * delete is rejected by Model Forge while another artifact still references the mapping (e.g. a
   * pipeline's {@code mappingRef}); with {@code force} it is unlinked from any DataSets and deleted
   * regardless. Deleting a mapping that a pipeline still references leaves a dangling reference, so
   * callers should delete referencing pipelines first (or pass {@code force}).
   */
  public void delete(String urn, boolean force) {
    if (force) {
      registry.deleteArtifact(urn, false, true);
    } else {
      registry.deletePayload(urn);
    }
  }

  /** An authored mapping document, split the way Model Forge stores it. */
  private record AuthoredDocument(
      String name, Map<String, Object> content, Map<String, Object> styles) {}

  /**
   * Splits an authored document into what Model Forge stores: the CORE content, the UI-only node
   * layout as {@code x-ui-styles}, and the display name derived from the title. Single source of
   * truth for both writing and comparing.
   */
  private static AuthoredDocument authored(Map<String, Object> doc) {
    Map<String, Object> content = new LinkedHashMap<>(doc == null ? Map.of() : doc);
    content.remove("logicalUrn");
    Object positions = content.remove("positions");
    Map<String, Object> styles =
        positions instanceof Map<?, ?> ? Map.of("positions", positions) : null;
    return new AuthoredDocument(deriveName(content), content, styles);
  }

  private static String deriveName(Map<String, Object> doc) {
    Object title = doc.get("title");
    return title instanceof String s && !s.isBlank() ? s : "mapping";
  }
}

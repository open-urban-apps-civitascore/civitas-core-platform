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
    Map<String, Object> content = new LinkedHashMap<>(doc == null ? Map.of() : doc);
    content.remove("logicalUrn");
    Object positions = content.remove("positions");
    Map<String, Object> styles =
        positions instanceof Map<?, ?> ? Map.of("positions", positions) : null;
    return registry.storePayload(
        PayloadKind.MAPPING, Optional.ofNullable(logicalUrn), deriveName(content), content, styles);
  }

  /**
   * The mapping read back from Model Forge — its rules plus the editor's node layout, which is
   * stored alongside the content and has to be served with it for the editor to restore a saved
   * diagram. Empty when the mapping does not exist.
   */
  public Optional<Map<String, Object>> get(String urn) {
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

  private static String deriveName(Map<String, Object> doc) {
    Object title = doc.get("title");
    return title instanceof String s && !s.isBlank() ? s : "mapping";
  }
}

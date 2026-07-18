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

  /** The mapping's content (rules), read back from Model Forge, or empty when it does not exist. */
  public Optional<Map<String, Object>> get(String urn) {
    return registry.fetchPayload(urn).map(ModelRegistryGateway.RegistryDocument::content);
  }

  private static String deriveName(Map<String, Object> doc) {
    Object title = doc.get("title");
    return title instanceof String s && !s.isBlank() ? s : "mapping";
  }
}

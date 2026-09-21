package de.civitascore.portal.frost;

import de.civitascore.portal.frost.PortStructureModel.PortStructure;
import de.civitascore.portal.model.datasink.FrostSinkPort;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The published structure of each port.
 *
 * <p>The documents are generated from {@link PortStructureCatalog} and kept in the repository, so
 * this class reads them rather than rendering them: what an installation publishes is then the
 * document a reviewer saw, not the output of the code that happens to be deployed with it.
 *
 * <p>A structure is the same for every Tenant. It is the contract of the sink, not a model anybody
 * owns, so it is loaded once and answered as an unmodifiable document.
 *
 * <p>Nothing in the platform writes against it yet. A Mapping that feeds a FROST sink still takes
 * the target structure its modeller selects, and the deploy engine still reads that one.
 */
@Component
public class PortStructures {

  private final Map<String, Map<String, Object>> byPort;

  public PortStructures(ObjectMapper objectMapper) {
    Map<String, Map<String, Object>> loaded = new LinkedHashMap<>();
    for (PortStructure structure : PortStructureCatalog.all()) {
      loaded.put(structure.port(), read(objectMapper, structure));
    }
    byPort = Map.copyOf(loaded);
  }

  /** The model document of a port, or empty when the label names no port. */
  public Optional<Map<String, Object>> model(String port) {
    return Optional.ofNullable(port).map(byPort::get);
  }

  /** The model document of a port. */
  public Optional<Map<String, Object>> model(FrostSinkPort port) {
    return port == null ? Optional.empty() : model(port.label());
  }

  /** The labels of the ports that publish a structure, in the order they are offered in. */
  public List<String> ports() {
    return List.copyOf(byPort.keySet());
  }

  private static Map<String, Object> read(ObjectMapper objectMapper, PortStructure structure) {
    String path = PortStructureGenerator.resourcePath(structure);
    try (InputStream source = PortStructures.class.getClassLoader().getResourceAsStream(path)) {
      if (source == null) {
        // The file is generated and committed. Its absence means the build lost a resource, and a
        // Dataset would then publish with no target structure at all.
        throw new IllegalStateException(
            "the published structure " + path + " is not on the class path");
      }
      return objectMapper.readValue(source, new TypeReference<Map<String, Object>>() {});
    } catch (IOException e) {
      throw new IllegalStateException("cannot read the published structure " + path, e);
    }
  }
}

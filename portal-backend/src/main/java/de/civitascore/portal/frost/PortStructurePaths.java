package de.civitascore.portal.frost;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * The record paths a published structure offers, in the notation the Mapping uses: {@code $.name},
 * {@code $.Datastreams[].properties.reference}.
 *
 * <p>The walk mirrors the tree the mapping editor builds from the same document — a collection adds
 * {@code []} and a nested class adds a level — so a path this class answers is a path the editor
 * offers, and a path it does not answer cannot be mapped. That is what lets a test compare the
 * published structures against the paths a Pipeline maps today.
 *
 * <p>Both the container and its fields are answered. A Mapping may assign a whole object, which is
 * how the free bag of a Sensor is written.
 */
public final class PortStructurePaths {

  private static final String ROOT = "$";

  private PortStructurePaths() {}

  /** Every path of the model document, the containers included. */
  public static Set<String> of(JsonNode model) {
    Set<String> paths = new LinkedHashSet<>();
    JsonNode defs = model.path("$defs");
    String root = rootClass(model);
    if (root != null) {
      walk(defs, defs.path(root), ROOT, paths, new ArrayList<>(List.of(root)));
    }
    return paths;
  }

  /** The class the wrapper root points at. */
  private static String rootClass(JsonNode model) {
    JsonNode properties = model.path("properties");
    if (properties.size() != 1) {
      return null;
    }
    String reference = properties.values().iterator().next().path("$ref").asText("");
    return reference.startsWith("#/$defs/") ? reference.substring("#/$defs/".length()) : null;
  }

  private static void walk(
      JsonNode defs, JsonNode declared, String base, Set<String> paths, List<String> open) {
    JsonNode properties = declared.path("properties");
    properties
        .properties()
        .forEach(
            member -> {
              String path = base + "." + member.getKey();
              paths.add(path);
              JsonNode value = member.getValue();
              if ("array".equals(value.path("type").asText(null))) {
                descend(defs, value.path("items"), path + "[]", paths, open);
                return;
              }
              descend(defs, value, path, paths, open);
            });
  }

  /**
   * Follows a reference into its class. A class already on the path is not followed again: a cycle
   * would answer an endless set of paths, and the editor stops there too.
   */
  private static void descend(
      JsonNode defs, JsonNode value, String path, Set<String> paths, List<String> open) {
    String reference = value.path("$ref").asText("");
    if (!reference.startsWith("#/$defs/")) {
      return;
    }
    String className = reference.substring("#/$defs/".length());
    if (open.contains(className)) {
      return;
    }
    List<String> nested = new ArrayList<>(open);
    nested.add(className);
    walk(defs, defs.path(className), path, paths, nested);
  }
}

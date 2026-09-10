package de.civitascore.modelforge.adminui.wicket;

import java.util.List;
import java.util.Map;

/**
 * The canonical set of artifact-type tokens (as stored/returned by {@code ModelForge.search}) and
 * their display labels, in the order every type-grouped view (the sidebar tree, the registry stats
 * strip) lists them.
 */
public final class ArtifactTypeCatalog {

    public static final List<String> TYPE_ORDER =
        List.of("element", "datastructure", "dataset", "mapping", "pipeline", "datasource", "datasink");

    public static final Map<String, String> TYPE_LABELS = Map.of(
        "element", "Elements",
        "datastructure", "Data Structures",
        "dataset", "Data Sets",
        "mapping", "Mappings",
        "pipeline", "Pipelines",
        "datasource", "Data Sources",
        "datasink", "Data Sinks"
    );

    public static String labelFor(String type) {
        // TYPE_LABELS is an immutable Map.of(...) — getOrDefault(null, ...) throws NPE on a null
        // key regardless of the fallback value (see ArtifactViewPage for the same class of bug),
        // so a null type must short-circuit before ever calling it.
        return type == null ? null : TYPE_LABELS.getOrDefault(type, capitalize(type));
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private ArtifactTypeCatalog() {
    }
}

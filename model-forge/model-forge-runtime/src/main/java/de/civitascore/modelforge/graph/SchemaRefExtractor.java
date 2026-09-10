package de.civitascore.modelforge.graph;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.urn.UrnParser;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extracts all outgoing CORE-URN $ref values from a JSON Schema document.
 *
 * Only refs that are valid CORE URNs (urn:core:...) are returned.
 * HTTP/HTTPS $ref values and JSON Pointer fragments are ignored.
 */
public class SchemaRefExtractor {

    /** Recursively collect all urn:core:... $ref values from the schema tree. */
    public Set<String> extractRefs(JsonNode schema) {
        Set<String> refs = new LinkedHashSet<>();
        collect(schema, refs);
        return refs;
    }

    /**
     * Recursively collect the {@code type} value of every {@code x-core-ref} annotation — the
     * declared foreign-key targets, regardless of form (any present, non-blank {@code type}).
     * The caller classifies each value (concrete artifact URN vs. the {@code urn:core:type:<Kind>}
     * category marker vs. a malformed value) and decides what to check; nothing is silently
     * dropped here, so an under-specified target can be surfaced rather than ignored.
     */
    public Set<String> extractCoreRefTypes(JsonNode schema) {
        Set<String> types = new LinkedHashSet<>();
        collectCoreRefTypes(schema, types);
        return types;
    }

    /**
     * Recursively collect the <em>concrete</em> {@code x-core-ref} targets — association (foreign
     * key) edges, the by-reference counterpart to {@link #extractRefs}'s by-value {@code $ref}
     * edges. Category markers ({@code urn:core:type:<Kind>}, which name no specific artifact) and
     * malformed (non-URN) values are excluded — {@code ReferenceExistenceValidator} surfaces those
     * separately as validation diagnostics; this method only ever returns real dependency-graph
     * targets.
     */
    public Set<String> extractCoreRefTargets(JsonNode schema) {
        Set<String> targets = new LinkedHashSet<>();
        for (String type : extractCoreRefTypes(schema)) {
            if (UrnParser.isUrn(type) && !isCategoryMarker(type)) targets.add(type);
        }
        return targets;
    }

    /** A category marker is exactly {@code urn:core:type:<Kind>} (no further segments). */
    private static boolean isCategoryMarker(String type) {
        return type.matches("urn:core:type:[A-Za-z0-9._-]+");
    }

    private void collectCoreRefTypes(JsonNode node, Set<String> types) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isObject()) {
            JsonNode xcr = node.get("x-core-ref");
            if (xcr != null && xcr.isObject()) {
                String type = xcr.path("type").asText(null);
                if (type != null && !type.isBlank()) types.add(type);
            }
            node.properties().forEach(e -> collectCoreRefTypes(e.getValue(), types));
        } else if (node.isArray()) {
            node.forEach(el -> collectCoreRefTypes(el, types));
        }
    }

    private void collect(JsonNode node, Set<String> refs) {
        if (node == null || node.isMissingNode() || node.isNull()) return;
        if (node.isObject()) {
            String ref = node.path("$ref").asText(null);
            if (ref != null && UrnParser.isUrn(ref)) refs.add(ref);
            node.properties().forEach(e -> collect(e.getValue(), refs));
        } else if (node.isArray()) {
            node.forEach(el -> collect(el, refs));
        }
    }
}

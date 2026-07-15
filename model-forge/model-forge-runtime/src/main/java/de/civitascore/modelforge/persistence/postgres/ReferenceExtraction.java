package de.civitascore.modelforge.persistence.postgres;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.urn.UrnParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Per-artifact-type extraction of the durable reference edges that get persisted into
 * {@code model_forge.artifact_reference}. Each artifact type keeps its cross-references in different
 * places, so extraction is type-specific:
 *
 * <ul>
 *   <li><b>Element</b> — CORE-URN {@code $ref}s (JSON Schema) / {@code xs:import}s (XSD) /
 *       concrete {@code x-core-ref} association targets, supplied already-collected as a
 *       {@code Set} ({@link #schemaRefs}/{@link #xsdImportRefs}/{@link #associationRefs}).</li>
 *   <li><b>Mapping</b> — {@code source} / {@code target} ({@link #mappingRefs}).</li>
 *   <li><b>Pipeline</b> — each node's reference field: {@code sourceRef} / {@code sinkRef} /
 *       {@code mappingRef} / an enrich node's {@code lookupSourceRef} ({@link #pipelineRefs}).</li>
 *   <li><b>DataStructure</b> — {@code elementRefs} ({@link #dataStructureRefs}).</li>
 *   <li><b>DataSet</b> — all {@code *Refs} arrays ({@link #dataSetRefs}).</li>
 *   <li><b>DataSource / DataSink</b> — the {@code element} field describing the payload/row format
 *       ({@link #dataSourceRefs}/{@link #dataSinkRefs}).</li>
 * </ul>
 *
 * <p>All references are stored verbatim (a pinned {@code …:version} or the {@code …:latest} token);
 * resolution to a concrete version happens on read. Only well-formed CORE URNs are kept — a blank
 * or non-URN value is skipped rather than persisted as a dangling edge.
 *
 * <p>Package-private and dependency-free (pure {@code JsonNode} → {@link ReferenceRow}) so the
 * per-type derivations can be unit-tested without a database.
 */
final class ReferenceExtraction {

    // ── Reference-graph edge types (values land in model_forge.artifact_reference.reference_type) ──
    static final String MAPPING_SOURCE     = "mapping-source";
    static final String MAPPING_TARGET     = "mapping-target";
    static final String DATASOURCE_ELEMENT = "datasource-element";
    static final String DATASINK_ELEMENT   = "datasink-element";
    static final String ASSOCIATION_REF    = "association-ref";

    private ReferenceExtraction() {}

    /** Element JSON Schema {@code $ref}s (already collected) → schema-ref edges. */
    static List<ReferenceRow> schemaRefs(Set<String> refs) {
        return typedRefs(refs, "schema-ref");
    }

    /** Element {@code x-core-ref} association (foreign-key) targets → association-ref edges —
     *  the by-reference counterpart to {@link #schemaRefs}'s by-value {@code $ref} edges. */
    static List<ReferenceRow> associationRefs(Set<String> targets) {
        return typedRefs(targets, ASSOCIATION_REF);
    }

    /** Element XSD {@code xs:import}s (already collected) → xsd-import edges. */
    static List<ReferenceRow> xsdImportRefs(Set<String> refs) {
        return typedRefs(refs, "xsd-import");
    }

    private static List<ReferenceRow> typedRefs(Set<String> refs, String type) {
        if (refs == null) return List.of();
        List<ReferenceRow> rows = new ArrayList<>();
        int i = 0;
        for (String u : refs) {
            if (UrnParser.isUrn(u)) rows.add(new ReferenceRow(u, type, null, i++));
        }
        return rows;
    }

    /** {@code source} / {@code target} of a Mapping document → mapping-source / mapping-target. */
    static List<ReferenceRow> mappingRefs(JsonNode m) {
        List<ReferenceRow> rows = new ArrayList<>();
        addRef(rows, m.path("source").asText(null), MAPPING_SOURCE, null, 0);
        addRef(rows, m.path("target").asText(null), MAPPING_TARGET, null, 1);
        return rows;
    }

    /** A DataSource's {@code element} field → the Element describing its payload format. */
    static List<ReferenceRow> dataSourceRefs(JsonNode s) {
        List<ReferenceRow> rows = new ArrayList<>();
        addRef(rows, s.path("element").asText(null), DATASOURCE_ELEMENT, null, 0);
        return rows;
    }

    /** A DataSink's {@code element} field → the Element describing its output row format. */
    static List<ReferenceRow> dataSinkRefs(JsonNode s) {
        List<ReferenceRow> rows = new ArrayList<>();
        addRef(rows, s.path("element").asText(null), DATASINK_ELEMENT, null, 0);
        return rows;
    }

    /** {@code nodes[]} of a Pipeline document → one pipeline-node edge per node reference field. */
    static List<ReferenceRow> pipelineRefs(JsonNode p) {
        JsonNode nodes = p.path("nodes");
        if (!nodes.isArray()) return List.of();
        List<ReferenceRow> rows = new ArrayList<>();
        int i = 0;
        for (JsonNode node : nodes) {
            String kind = node.path("kind").asText(null);
            // Node kinds and their x-core-ref field, per pipeline.schema.json. An enrich node
            // references a DataSource via lookupSourceRef — omitting it would drop that edge.
            String refField = switch (kind == null ? "" : kind) {
                case "source"  -> "sourceRef";
                case "sink"    -> "sinkRef";
                case "mapping" -> "mappingRef";
                case "enrich"  -> "lookupSourceRef";
                default         -> null;
            };
            if (refField != null) addRef(rows, node.path(refField).asText(null), "pipeline-node", kind, i++);
        }
        return rows;
    }

    /** The DataSet manifest's {@code *Refs} arrays → dataset / datasource / datasink edges. */
    static List<ReferenceRow> dataSetRefs(JsonNode manifest) {
        List<ReferenceRow> rows = new ArrayList<>();
        int[] i = {0};
        addArrayRefs(rows, manifest.path("elementRefs"),    "dataset-ref",    "element",  i);
        addArrayRefs(rows, manifest.path("mappingRefs"),    "dataset-ref",    "mapping",  i);
        addArrayRefs(rows, manifest.path("pipelineRefs"),   "dataset-ref",    "pipeline", i);
        addArrayRefs(rows, manifest.path("dataSourceRefs"), "datasource-ref", null,       i);
        addArrayRefs(rows, manifest.path("dataSinkRefs"),   "datasink-ref",   null,       i);
        return rows;
    }

    /** The DataStructure manifest's {@code elementRefs} array → datastructure-ref edges. */
    static List<ReferenceRow> dataStructureRefs(JsonNode manifest) {
        List<ReferenceRow> rows = new ArrayList<>();
        int[] i = {0};
        addArrayRefs(rows, manifest.path("elementRefs"), "datastructure-ref", "element", i);
        return rows;
    }

    private static void addArrayRefs(List<ReferenceRow> rows, JsonNode array, String type, String name, int[] i) {
        if (!array.isArray()) return;
        for (JsonNode n : array) addRef(rows, n.asText(null), type, name, i[0]++);
    }

    private static void addRef(List<ReferenceRow> rows, String urn, String type, String name, int sort) {
        // Store the reference verbatim (pinned version or :latest); resolution happens on read.
        if (UrnParser.isUrn(urn)) rows.add(new ReferenceRow(urn, type, name, sort));
    }
}

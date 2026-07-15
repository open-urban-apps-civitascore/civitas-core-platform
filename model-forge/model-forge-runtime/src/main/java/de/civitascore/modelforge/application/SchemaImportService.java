package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.urn.UrnService;
import de.civitascore.modelforge.validation.ModelValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Handles all Element import operations: JSON Schema, XSD, bulk import, and data validation.
 *
 * <p>Extracted from the former {@code ModelForgeService} to give each concern its own class.
 */
public class SchemaImportService {

    private static final Logger log = LoggerFactory.getLogger(SchemaImportService.class);

    private final ModelValidator           validator;
    private final ObjectMapper             mapper;
    private final ArtifactRegistry           registry;
    private final UrnService               urns;
    private final SchemaRefExtractor       refExtractor;
    private final DependencyGraphService   graph;
    private final ReferenceExistenceValidator refExistence;
    private final RemoteSchemaRepository   remoteFetcher;

    public SchemaImportService(ModelValidator validator,
                               ObjectMapper mapper,
                               ArtifactRegistry registry,
                               UrnService urns,
                               SchemaRefExtractor refExtractor,
                               DependencyGraphService graph,
                               ReferenceExistenceValidator refExistence,
                               RemoteSchemaRepository remoteFetcher) {
        this.validator      = validator;
        this.mapper         = mapper;
        this.registry       = registry;
        this.urns           = urns;
        this.refExtractor   = refExtractor;
        this.graph          = graph;
        this.refExistence   = refExistence;
        this.remoteFetcher  = remoteFetcher;
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Validates a JSON Schema and stores every Element (splitting {@code $defs}) in the registry,
     * plus the automatic DataStructure grouping. Composition into a DataSet is the caller's
     * concern (via {@code saveArtifact}); the import performs no DataSet linking.
     */
    public SchemaImportResult importSchema(SchemaImportRequest req) {
        return importSchema(req, Set.of());
    }

    /**
     * Import variant that additionally treats {@code alsoAvailableLogicalUrns} as present
     * foreign-key targets. Used by the startup bootstrap so a concrete {@code x-core-ref}
     * reference between files in the same corpus resolves regardless of import order — the
     * existence check then sees the whole corpus, not only the elements of the current file.
     */
    public SchemaImportResult importSchema(SchemaImportRequest req, Set<String> alsoAvailableLogicalUrns) {
        String explicitVersion = req.preserveVersion() ? req.version() : null;
        if (explicitVersion != null && !isValidVersion(explicitVersion)) {
            return importError("invalid-version",
                "Version '" + explicitVersion + "' is not a valid version (expected e.g. 1.0, 6.0, or 1.0.0).");
        }

        JsonNode schema = req.schema();
        if (schema == null) {
            return importError("schema-missing", "Import request must contain a JSON Schema in 'schema'.");
        }

        List<Diagnostic> schemaDiags = validator.validateSchema(schema);
        if (schemaDiags.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            return new SchemaImportResult(null, schemaDiags);
        }

        String schemaTitle = schema.path("title").asText(null);
        String safeTitle   = sanitizeId(schemaTitle, UUID.randomUUID().toString());

        ObjectNode elements = buildElements(schema, safeTitle, schemaTitle);

        // Registry-aware existence check: every concrete x-core-ref foreign-key target must
        // resolve to an existing artifact, or be one of the elements imported in this request
        // (importedLogicalUrns) or its wider corpus (alsoAvailableLogicalUrns, e.g. a bootstrap batch).
        Set<String> availableTargets = new HashSet<>(importedLogicalUrns(elements));
        availableTargets.addAll(alsoAvailableLogicalUrns);
        List<Diagnostic> refDiags = refExistence.checkForeignKeys(schema, availableTargets);
        if (!refDiags.isEmpty()) {
            return new SchemaImportResult(null, refDiags);
        }

        // The registry assigns the versions inside the write and returns the pins; the ROOT
        // element's pin (first entry, by construction) is carried through so the facade can
        // return it — and every sibling Element's pin, for importedResourceIds — verbatim,
        // without any read-back.
        List<String> pins = storeElementsInRegistry(elements, explicitVersion);
        createDataStructureForImport(elements, schemaTitle, safeTitle);

        String rootPin = pins.isEmpty() ? primaryResourceId(elements, safeTitle) : pins.getFirst();
        return new SchemaImportResult(rootPin, pins, List.of());
    }

    /**
     * Whether {@code version} is safe to interpolate into a URN — 1–3 numeric segments plus an
     * optional SemVer pre-release/build suffix. Two-segment versions are accepted because real
     * XÖV/XRepository standards use them (e.g. 6.0, 5.5). Rejects anything else (in particular a
     * {@code ':'}, which would inject extra colon-delimited URN segments — URN-injection /
     * identity-confusion hardening).
     */
    private static boolean isValidVersion(String version) {
        return version != null && version.matches("\\d+(\\.\\d+){0,2}([-+][0-9A-Za-z.-]+)?");
    }

    public SchemaImportResult importXsd(XsdImportRequest command) {
        String name = command.name();
        String version = command.version();
        String xsdContent = command.xsdContent();
        String artifactId = command.artifactId();
        if (name == null || name.isBlank()) {
            return importError("name-missing", "XSD import requires a non-empty name.");
        }
        if (xsdContent == null || xsdContent.isBlank()) {
            return importError("content-missing", "XSD import requires non-empty XML content.");
        }
        if (version != null && !version.isBlank() && !isValidVersion(version)) {
            return importError("invalid-version",
                "Version '" + version + "' is not a valid version (expected e.g. 1.0, 6.0, or 1.0.0).");
        }
        String safeName = sanitizeId(name, UUID.randomUUID().toString());
        // XSD shares the Element identity — the format is a stored representation, not in the URN.
        // A name-derived identity is minted (UUID invariant); an explicit artifactId (e.g. the
        // XRepository kennung-based URN) is caller-owned and respected as-is.
        String urn = artifactId != null && !artifactId.isBlank()
            ? artifactId
            : version != null && !version.isBlank()
            ? urns.mintElement(safeName, version)
            : urns.mintElement(safeName);

        Set<String> importRefs =
            Optional.ofNullable(registry.extractImportRefs(xsdContent)).orElse(Set.of());
        // Store first, then register the graph node — so a failed/​rejected store (XXE guard,
        // integrity → 400) never leaves a phantom node/edge behind. Register under the
        // registry-assigned pin, not the pre-write urn — a caller-supplied artifactId/version may
        // not match what MF actually assigns (always 1.0.0 for a brand-new artifact unless
        // preserveVersion opts into adopting it verbatim), which would otherwise orphan the graph
        // node once a rebuild re-syncs from the durable registry.
        String explicitVersion = command.preserveVersion() && version != null && !version.isBlank() ? version : null;
        String pin = registry.storeXsdElement(urn, xsdContent, importRefs, explicitVersion);
        graph.register(pin != null ? pin : urn, importRefs);

        // The write itself assigned the version; return its pin (fall back to the import URN
        // only when no pin is available, e.g. a test double).
        return new SchemaImportResult(pin != null ? pin : urn, List.of());
    }

    /**
     * Fetches a JSON Schema from a public {@code url} server-side (so the browser avoids CORS,
     * e.g. importing from smartdatamodels.org or a raw GitHub URL) and imports it. The URL is
     * SSRF-checked and fetched without following redirects; a malformed body or unsafe URL
     * surfaces as HTTP 400, an unreachable host as HTTP 502.
     */
    public SchemaImportResult importFromUrl(String url) {
        JsonNode schema = remoteFetcher.fetchJson(url);
        return importSchema(new SchemaImportRequest(schema));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** JSON Schema keywords that give a document a shape of its own (as opposed to being a pure
     *  {@code $defs} container — see {@link #hasOwnShape}). */
    private static final List<String> OWN_SHAPE_KEYWORDS = List.of(
        "type", "properties", "required", "$ref", "allOf", "oneOf", "anyOf", "enum", "const",
        "items", "additionalProperties", "patternProperties", "not");

    private ObjectNode buildElements(JsonNode schema, String safeTitle, String schemaTitle) {
        ObjectNode elements = mapper.createObjectNode();
        JsonNode defs = schema.path("$defs");

        if (!defs.isObject() || defs.isEmpty()) {
            // Flat schema (no $defs): the whole schema is one Element.
            ObjectNode flat = buildFlatElement(schema, safeTitle, schemaTitle);
            elements.set(schemaKey(flat, safeTitle), flat);
            return elements;
        }

        // The root's URN is resolved up front (used to seed de-dup / re-import-matching for the
        // defs below) so a $defs entry named like the schema title can never be assigned it.
        boolean rootIsExplicit = UrnParser.isUrn(schema.path("$id").asText(null));
        String rootUrn = rootIsExplicit
            ? schema.path("$id").asText()
            : urns.mintElement(safeTitle);
        Map<String, String> defUrns = mapDefUrns(defs, rootUrn, rootIsExplicit);

        // A document with no shape of its own (no type/properties/$ref/composition keyword — just
        // metadata and $defs) is a pure container: the DataStructure grouping its defs, not an
        // Element in its own right, so — unlike a root with real content — it is not itself stored.
        if (hasOwnShape(schema)) {
            ObjectNode root = buildRootElement(schema, defUrns, rootUrn, schemaTitle);
            elements.set(schemaKey(root, safeTitle), root);
        }

        defs.properties().forEach(e -> {
            ObjectNode def = buildDefElement(e.getKey(), e.getValue(), defUrns);
            // Map keys are display names — disambiguate instead of dropping, so every
            // def survives even when its name segment collides with the root's.
            elements.set(uniqueElementKey(elements,
                UrnParser.nameFromUrn(defUrns.get(e.getKey()))), def);
        });
        return elements;
    }

    private static boolean hasOwnShape(JsonNode schema) {
        for (String keyword : OWN_SHAPE_KEYWORDS) {
            if (schema.has(keyword)) return true;
        }
        return false;
    }

    /** Flat schema → a single Element, with $id/title backfilled when absent. */
    private ObjectNode buildFlatElement(JsonNode schema, String safeTitle, String schemaTitle) {
        ObjectNode s = (ObjectNode) schema.deepCopy();
        if (!UrnParser.isUrn(s.path("$id").asText(null)))
            s.put("$id", urns.mintElement(safeTitle));
        if (!s.has("title") && schemaTitle != null)
            s.put("title", schemaTitle);
        return s;
    }

    /**
     * The root Element of a $defs schema: the schema body with its
     * {@code "#/$defs/<Name>"} references rewritten to CORE URNs (so the extracted
     * entities reference each other globally — resolvable in views, producing
     * dependency-graph edges) and the now-redundant {@code $defs} block dropped.
     * {@code $id} and {@code title} are backfilled when the schema omits them.
     */
    private ObjectNode buildRootElement(JsonNode schema, Map<String, String> defUrns,
                                          String rootUrn, String schemaTitle) {
        ObjectNode root = dropRedundantDefs((ObjectNode) rewriteLocalDefRefs(schema, defUrns));
        if (!UrnParser.isUrn(root.path("$id").asText(null)))
            root.put("$id", rootUrn);
        if (!root.has("title") && schemaTitle != null)
            root.put("title", schemaTitle);
        return root;
    }

    /** A single {@code $defs} entry as its own Element, with local refs rewritten. */
    private ObjectNode buildDefElement(String defKey, JsonNode def, Map<String, String> defUrns) {
        ObjectNode s = (ObjectNode) rewriteLocalDefRefs(def, defUrns);
        s.put("$id", defUrns.get(defKey));
        if (!s.has("title")) s.put("title", defKey);
        return s;
    }

    /**
     * Map each top-level {@code $defs} key to the URN its extracted Element will carry.
     * A definition that already declares its own CORE-URN {@code $id} keeps it. Otherwise the
     * def is matched against the sub-elements minted for this root in an earlier import (so a
     * re-imported def keeps its identity and is versioned, not duplicated); a def with no such
     * predecessor gets a freshly minted, UUID-bearing URN. Generated URNs are de-duplicated
     * against each other <em>and</em> against the root schema's URN — comparison is on the
     * logical (version-free) form because the logical URN is the registry artifact ID, so two
     * URNs differing only in version would still collapse onto the same artifact.
     */
    private Map<String, String> mapDefUrns(JsonNode defs, String reservedRootUrn, boolean rootIsExplicit) {
        Map<String, String> defUrns = new LinkedHashMap<>();
        Set<String> usedLogical = new HashSet<>();
        usedLogical.add(UrnParser.logicalUrn(reservedRootUrn));
        // Pass 1: reserve URNs of defs that bring their own CORE-URN $id — but only when the
        // logical URN is still free. A def whose explicit $id collides with the reserved root URN
        // (or an earlier def's $id) is left for pass 2, which derives a distinct URN from the def
        // key; this mirrors pass 2's de-dup so a def can never silently mint another version of an
        // already-claimed artifact (URN-collision / identity-confusion hardening).
        defs.properties().forEach(e -> {
            String ownId = e.getValue().path("$id").asText(null);
            if (UrnParser.isUrn(ownId) && !usedLogical.contains(UrnParser.logicalUrn(ownId))) {
                defUrns.put(e.getKey(), ownId);
                usedLogical.add(UrnParser.logicalUrn(ownId));
            }
        });
        // Pass 2: reuse the sub-element minted for the same def name in an earlier import of this
        // root, or mint a fresh UUID-bearing URN. Minted UUIDs cannot collide; the usedLogical
        // guard stays as identity-confusion hardening. Only an explicitly supplied root URN can
        // be a re-import — a root minted in this very call cannot have predecessors.
        Map<String, String> mintedPredecessors = rootIsExplicit
            ? mintedSubElementsOf(reservedRootUrn)
            : Map.of();
        defs.properties().forEach(e -> {
            if (!defUrns.containsKey(e.getKey())) {
                String predecessor = mintedPredecessors.get(UrnParser.sanitize(e.getKey()));
                String urn = predecessor != null && !usedLogical.contains(UrnParser.logicalUrn(predecessor))
                    ? predecessor
                    : mintedElementUrn(usedLogical, e.getKey());
                defUrns.put(e.getKey(), urn);
                usedLogical.add(UrnParser.logicalUrn(urn));
            }
        });
        return defUrns;
    }

    /**
     * The minted sub-elements a previous import of {@code rootUrn} produced, keyed by the base
     * of their name segment (the sanitised def key without the UUID suffix). Re-importing a root
     * therefore versions its existing sub-elements instead of minting a parallel set — the
     * def-name-within-parent matching that keeps sub-element identity stable across imports.
     */
    private Map<String, String> mintedSubElementsOf(String rootUrn) {
        Map<String, String> byBaseName = new LinkedHashMap<>();
        for (String dependency : graph.getDependencies(rootUrn)) {
            String base = UrnParser.nameFromUrn(dependency);
            if (base != null) byBaseName.putIfAbsent(base, dependency);
        }
        return byBaseName;
    }

    /** Mint a UUID-bearing Element URN for {@code name}; the guard loop is belt-and-braces. */
    private String mintedElementUrn(Set<String> usedLogical, String name) {
        String urn = urns.mintElement(name);
        while (usedLogical.contains(UrnParser.logicalUrn(urn)))
            urn = urns.mintElement(name);
        return urn;
    }

    /** Pick an elements-map key that is not taken yet, appending a numeric suffix on collision. */
    private static String uniqueElementKey(ObjectNode elements, String preferred) {
        if (!elements.has(preferred)) return preferred;
        int i = 2;
        while (elements.has(preferred + "-" + i)) i++;
        return preferred + "-" + i;
    }

    /**
     * Deep-copy {@code node}, replacing every exact {@code "#/$defs/<Name>"} reference with the
     * URN from {@code defUrns}. Sub-path pointers ({@code "#/$defs/<Name>/..."}) and refs to
     * unknown names are left unchanged.
     */
    private JsonNode rewriteLocalDefRefs(JsonNode node, Map<String, String> defUrns) {
        if (node == null || node.isNull() || node.isMissingNode()) return node;
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.properties().forEach(e -> {
                if ("$ref".equals(e.getKey()) && e.getValue().isTextual()) {
                    String urn = defUrns.get(wholeDefName(e.getValue().asText()));
                    if (urn != null) copy.put("$ref", urn);
                    else copy.set("$ref", e.getValue());
                } else {
                    copy.set(e.getKey(), rewriteLocalDefRefs(e.getValue(), defUrns));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode arr = mapper.createArrayNode();
            node.forEach(child -> arr.add(rewriteLocalDefRefs(child, defUrns)));
            return arr;
        }
        return node;
    }

    /** Def name iff {@code ref} is exactly {@code "#/$defs/<name>"} (no sub-path), else {@code null}. */
    private static String wholeDefName(String ref) {
        if (ref == null || !ref.startsWith("#/$defs/")) return null;
        String rest = ref.substring("#/$defs/".length());
        if (rest.isEmpty() || rest.indexOf('/') >= 0) return null; // empty or sub-path pointer
        return rest.replace("~1", "/").replace("~0", "~");          // JSON Pointer unescape
    }

    /** Drop the top-level {@code $defs} block when no {@code "#/$defs/"} pointer survives outside it. */
    private ObjectNode dropRedundantDefs(ObjectNode root) {
        if (!root.has("$defs")) return root;
        ObjectNode without = (ObjectNode) root.deepCopy();
        without.remove("$defs");
        return containsLocalDefsRef(without) ? root : without;
    }

    /** Whether the subtree still contains any {@code "#/$defs/"} JSON-Pointer reference. */
    private static boolean containsLocalDefsRef(JsonNode node) {
        if (node == null) return false;
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.isTextual() && ref.asText().startsWith("#/$defs/")) return true;
            for (JsonNode child : node) if (containsLocalDefsRef(child)) return true;
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) if (containsLocalDefsRef(child)) return true;
        }
        return false;
    }

    /** Returns the short name used as the map key (name segment of $id, or fallback). */
    private static String schemaKey(ObjectNode schema, String fallback) {
        String id = schema.path("$id").asText(null);
        return UrnParser.isUrn(id) ? UrnParser.nameFromUrn(id) : fallback;
    }

    /**
     * Stores every imported Element and returns each one's registry-assigned pin (the versioned
     * URN returned by the write itself), in {@code elements}' order — the first entry is the root,
     * by construction. Never empty when {@code elements} is non-empty: an element whose write
     * returned no pin (e.g. a registry double) falls back to its pre-write identity.
     *
     * @param explicitVersion when non-null, adopted verbatim as every stored Element's version
     *     instead of Model Forge's usual version authority — see
     *     {@link ArtifactRegistry#storeElement}'s equivalent parameter. Applied uniformly to every
     *     Element this import produces, matching the "one document, one version" mental model.
     */
    private List<String> storeElementsInRegistry(ObjectNode elements, String explicitVersion) {
        List<String> pins = new ArrayList<>();
        elements.properties().forEach(e -> {
            JsonNode schema  = e.getValue();
            Set<String> refs         = refExtractor.extractRefs(schema);
            Set<String> associations = refExtractor.extractCoreRefTargets(schema);
            String schemaId = schema.path("$id").asText(null);
            String urn = UrnParser.isUrn(schemaId) ? schemaId : urns.mintElement(e.getKey());
            // Persist first, then register the graph node, so a failed store never leaves a phantom
            // node/edge. Persist the full (possibly cyclic) edge set — compositions ($ref) and
            // associations (x-core-ref) alike, so both are navigable as dependencies/dependents.
            // Register under the registry-assigned pin (the version MF actually stored), not the
            // pre-write $id — a caller-supplied $id may carry a version MF never assigns (MF always
            // mints 1.0.0 for a brand-new artifact regardless of the incoming URN's version segment,
            // unless explicitVersion opts into adopting it verbatim), which would otherwise orphan
            // the graph node once a rebuild re-syncs from the registry.
            String pin = registry.storeElement(e.getKey(), schema, refs, associations, explicitVersion);
            String resolvedPin = pin != null ? pin : urn;
            pins.add(resolvedPin);
            Set<String> allEdges = new LinkedHashSet<>(refs);
            allEdges.addAll(associations);
            graph.register(resolvedPin, allEdges);
        });
        return pins;
    }

    /**
     * Creates (or versions) a DataStructure grouping every Element produced by this import — one
     * DataStructure per imported JSON Schema document, named after the document title. A
     * DataStructure is a stored artifact (not a View): it records which Elements belong together,
     * referencing its members by URN. Skipped when the import yielded no
     * elements.
     */
    private void createDataStructureForImport(ObjectNode elements, String schemaTitle, String safeTitle) {
        List<String> members = elementUrns(elements);
        if (members.isEmpty()) return;
        // The grouping reuses the root Element's identity (first member) — its name AND
        // disambiguator segments — so a re-imported document versions its existing DataStructure
        // instead of minting a new one.
        String firstMember = members.getFirst();
        String dataStructureUrn = urns.dataStructure(
            UrnParser.nameFromUrn(firstMember), UrnParser.disambiguatorFromUrn(firstMember));
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("$schema", "https://civitasconnect.digital/core-datastructure/v1");
        manifest.put("id", dataStructureUrn);
        manifest.put("title", schemaTitle != null ? schemaTitle : safeTitle);
        ArrayNode refs = manifest.putArray("elementRefs");
        members.forEach(refs::add);
        registry.storeDataStructure(UrnParser.logicalUrn(dataStructureUrn), manifest);
    }

    /** The member Element URNs of an import, in order: each element's $id (or generated URN). */
    private List<String> elementUrns(ObjectNode elements) {
        List<String> urnList = new ArrayList<>();
        elements.properties().forEach(e -> {
            String id = e.getValue().path("$id").asText(null);
            urnList.add(UrnParser.isUrn(id) ? id : urns.mintElement(e.getKey()));
        });
        return urnList;
    }

    /** A URN-safe slug of {@code candidate}, or {@code fallback} when nothing usable remains. */
    private static String sanitizeId(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) return fallback;
        String safe = UrnParser.sanitize(candidate);
        return safe.equals("unknown") ? fallback : safe;
    }

    /** Logical URNs of the elements created by this import — co-imported FK targets / self-refs. */
    private Set<String> importedLogicalUrns(ObjectNode elements) {
        Set<String> logical = new HashSet<>();
        elements.properties().forEach(e -> {
            String id = e.getValue().path("$id").asText(null);
            if (UrnParser.isUrn(id)) logical.add(UrnParser.logicalUrn(id));
        });
        return logical;
    }

    private String primaryResourceId(ObjectNode elements, String fallbackName) {
        for (Map.Entry<String, JsonNode> first : elements.properties()) {
            return first.getValue().path("$id").asText(urns.mintElement(first.getKey()));
        }
        return urns.mintElement(fallbackName);
    }

    private SchemaImportResult importError(String code, String message) {
        return new SchemaImportResult(null,
            List.of(new Diagnostic(DiagnosticSeverity.ERROR, message, code, null)));
    }

}

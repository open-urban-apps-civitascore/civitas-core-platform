package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.ModelValidator;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Write operations for Elements: store/update JSON Schemas and XSDs, keeping the
 * dependency graph and the persisted reference edges in sync.
 *
 * <p>The read side lives in {@link ElementQueryService}. Every write registers
 * the full (possibly cyclic) edge set in the in-memory graph and persists the same full set
 * as reference rows. The new version number is computed by the registry from the supplied
 * {@link VersionBump}.
 */
public class ElementCommandService {

    private final ArtifactRegistry registry;
    private final DependencyGraphService graph;
    private final SchemaRefExtractor     refExtractor;
    private final ModelValidator         validator;

    public ElementCommandService(ArtifactRegistry registry,
                                       DependencyGraphService graph,
                                       SchemaRefExtractor refExtractor,
                                       ModelValidator validator) {
        this.registry     = registry;
        this.graph        = graph;
        this.refExtractor = refExtractor;
        this.validator    = validator;
    }

    /**
     * Stores a new version of a JSON Schema Element. The schema's {@code $id} is
     * backfilled with {@code urn} when it does not already carry a CORE URN.
     *
     * @param bump how the registry should bump the version (ignored for a brand-new artifact)
     * @param bumpFromVersion the existing version {@code bump} counts from; {@code null} counts from
     *     the artifact's newest version
     * @return the concrete versioned URN (pin) the write resolved to — assigned by the write
     *     itself, so no read-back is needed
     */
    public String storeJsonSchema(String urn, JsonNode schema, VersionBump bump, String bumpFromVersion) {
        requireElementUrn(urn);
        // A JSON Schema document is an object. Nothing upstream enforces that — saveArtifact routes
        // ELEMENT on isTextual() alone and the CORE validator has no ELEMENT schema — so an array or
        // scalar would otherwise reach the cast below and surface as a 500 instead of a rejection.
        if (schema == null || !schema.isObject()) {
            throw rejected("element-not-an-object",
                "A JSON Schema Element must be a JSON object: " + urn);
        }
        List<Diagnostic> nonConforming = validator.validateSchema(schema);
        if (nonConforming.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            throw new ValidationFailedException(
                "Element is not a conforming JSON Schema", nonConforming);
        }
        ObjectNode updated = (ObjectNode) schema.deepCopy();
        String existingId = updated.path("$id").asText(null);
        if (!UrnParser.isUrn(existingId)) updated.put("$id", urn);
        Set<String> refs         = refExtractor.extractRefs(updated);
        Set<String> associations = refExtractor.extractCoreRefTargets(updated);
        String pin = registry.storeElement(
            updated, refs, associations, null, bump, bumpFromVersion);
        // Register the graph node under the pin the write itself assigned (the current version),
        // so the node key matches what read-time lookups resolve a logical/:latest URN to.
        Set<String> allEdges = new LinkedHashSet<>(refs);
        allEdges.addAll(associations);
        graph.register(pin != null && !pin.isBlank() ? pin : updated.path("$id").asText(urn), allEdges);
        return pin;
    }

    /**
     * Stores raw XSD content and registers its {@code xs:import} references in the graph.
     *
     * <p>Only the identity is checked here: an XSD carries no JSON Schema, so there is nothing for
     * {@link ModelValidator} to meta-validate. Conformance of an XSD-derived Element is decided on
     * the import path, where the converted JSON Schema exists.
     *
     * @return the concrete versioned URN (pin) the write resolved to
     */
    public String storeXsd(String urn, String xsdContent, VersionBump bump) {
        requireElementUrn(urn);
        Set<String> importRefs = registry.extractImportRefs(xsdContent);
        String pin = registry.storeXsdElement(urn, xsdContent, importRefs, bump);
        graph.register(pin != null && !pin.isBlank() ? pin : urn, new LinkedHashSet<>(importRefs));
        return pin;
    }

    /**
     * A write rejected for the caller's reason given, carrying the diagnostic shape the registry's
     * other rejections carry — so a bad identity reaches the caller as a 400 with a reason, not as
     * an unmapped server error.
     */
    private static ValidationFailedException rejected(String code, String message) {
        return new ValidationFailedException(message,
            List.of(new Diagnostic(DiagnosticSeverity.ERROR, message, code, "")));
    }

    /**
     * {@link UrnParser#requireUrn}'s rule, raised as a caller-facing rejection. The identity check
     * and its wording stay in {@code UrnParser}; only the exception type differs, because a write
     * reached over HTTP answers a bad identity with a 400 rather than an unmapped server error.
     */
    private static void requireElementUrn(String urn) {
        try {
            UrnParser.requireUrn(urn, "Element id");
        } catch (IllegalArgumentException e) {
            throw rejected("invalid-artifact-id", e.getMessage());
        }
    }

    /** Removes the artifact from the registry and the dependency graph. */
    public void delete(String urn) {
        delete(urn, false, false);
    }

    public void delete(String urn, boolean cascade) {
        delete(urn, cascade, false);
    }

    /**
     * Removes the artifact from the registry and the dependency graph under the DataSet-aware
     * deletion policy, optionally cascading into the artifacts it owns.
     *
     * <p>Non-DataSet references block unconditionally; DataSet membership is counted: none or one
     * lets the artifact go (that one manifest is unlinked with it), two or more block — except for a
     * Data Structure, which is unlinked from all of them. {@code force} overrides both and unlinks
     * from every DataSet, which may dangle references.
     *
     * <p>Each delete re-checks the live registry, so a cascade never dangles a reference, and joins
     * the caller's transaction where there is one, so several removals commit or roll back together.
     */
    public void delete(String urn, boolean cascade, boolean force) {
        delete(urn, cascade, force, Set.of());
    }

    /**
     * Like {@link #delete(String, boolean, boolean)}, where a reference held by an artifact in
     * {@code removedTogether} does not count: the same delete removes it.
     */
    private void delete(String urn, boolean cascade, boolean force, Set<String> removedTogether) {
        String logical = UrnParser.logicalUrn(urn);
        // DataSet memberships are read before the delete removes the target's incoming edges.
        List<String> memberships = registry.dataSetMemberships(urn);
        if (!force) {
            List<String> blockers = outsideReferrers(urn, removedTogether);
            if (!blockers.isEmpty()) {
                throw new ArtifactInUseException(logical, blockers);
            }
            // A member shared across several DataSets must be removed from all but one first.
            if (memberships.size() >= 2 && !isDataStructure(urn)) {
                throw new ArtifactInUseException(logical, memberships);
            }
        }
        // Snapshot the members before the artifact — and with it its outgoing edges — are gone. The
        // delete removes every version, so a Data Structure takes the Elements of each one.
        List<String> members = !cascade ? List.of()
            : isDataStructure(urn) ? registry.dataStructureMembersOfAnyVersion(urn)
            : registry.ownedMemberUrns(urn);
        Set<String> freed = freedMembers(logical, members, removedTogether);
        registry.deleteArtifact(urn);
        graph.remove(urn);
        // Keep every DataSet manifest that listed this member consistent (the |D|=1 rule, and — with
        // force — any number), so no manifest dangles the removed member.
        for (String dataSet : memberships) {
            unlinkFromDataSet(dataSet, logical);
        }
        Set<String> removed = new LinkedHashSet<>(removedTogether);
        removed.add(logical);
        removed.addAll(freed);
        for (String member : freed) {
            // Recursing collects a member's own orphans; its one Data Set is unlinked with it.
            if (registry.fetch(member).isPresent()) {
                delete(member, true, force, removed);
            }
        }
    }

    /**
     * The members the cascade removes, by logical URN: those only the container and the other
     * removed members reference. A member something else references stays, and so does every member
     * only it references, so the cascade dangles nothing and the order of the members decides nothing.
     */
    private Set<String> freedMembers(String containerLogical, List<String> members, Set<String> removedTogether) {
        // Counted before this container's own edge goes, or a member of two would read one.
        Set<String> freed = members.stream()
            .filter(member -> registry.dataSetMemberships(member).size() < 2)
            .map(UrnParser::logicalUrn)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> removed = new LinkedHashSet<>(removedTogether);
        removed.add(containerLogical);
        boolean shrunk = true;
        while (shrunk) {
            shrunk = freed.removeIf(member -> registry.nonDataSetBlockingDependents(member).stream()
                .map(UrnParser::logicalUrn)
                .anyMatch(referrer -> !removed.contains(referrer) && !freed.contains(referrer)));
        }
        return freed;
    }

    /** The referrers of {@code urn} that the delete does not remove with it. */
    private List<String> outsideReferrers(String urn, Set<String> removedTogether) {
        return registry.nonDataSetBlockingDependents(urn).stream()
            .filter(referrer -> !removedTogether.contains(UrnParser.logicalUrn(referrer)))
            .toList();
    }

    /**
     * Whether the artifact is a Data Structure, whose Data Set memberships are not counted: a Data
     * Set only groups it, and the Mapping or Data Sink that uses it holds the version it uses.
     */
    static boolean isDataStructure(String urn) {
        return "datastructure".equals(UrnParser.artifactTypeFromUrn(urn));
    }

    /**
     * Removes {@code memberLogicalUrn} from every {@code *Refs} array of DataSet manifest
     * {@code dataSetLogicalUrn} and re-stores it, so the manifest no longer lists a member that was
     * just deleted or explicitly unlinked. A no-op when the manifest is absent or already excludes it.
     */
    public void unlinkFromDataSet(String dataSetLogicalUrn, String memberLogicalUrn) {
        Optional<JsonNode> manifest = registry.fetch(dataSetLogicalUrn);
        if (manifest.isEmpty() || !manifest.get().isObject()) {
            return;
        }
        ObjectNode doc = (ObjectNode) manifest.get().deepCopy();
        boolean changed = false;
        for (String field : List.of(
                "datastructureRefs", "mappingRefs", "pipelineRefs", "dataSourceRefs", "dataSinkRefs")) {
            if (doc.get(field) instanceof tools.jackson.databind.node.ArrayNode refs) {
                for (int i = refs.size() - 1; i >= 0; i--) {
                    if (memberLogicalUrn.equals(UrnParser.logicalUrn(refs.get(i).asText()))) {
                        refs.remove(i);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            String pin = registry.storeDataSet(dataSetLogicalUrn, doc, VersionBump.MINOR);
            // The graph is keyed by the version a node was registered under, and this mints a new
            // one. Without the refresh every member of the Data Set reads as removed.
            graph.registerFromRegistry(pin != null && !pin.isBlank() ? pin : dataSetLogicalUrn);
        }
    }
}

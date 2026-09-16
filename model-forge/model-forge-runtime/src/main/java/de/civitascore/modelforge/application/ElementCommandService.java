package de.civitascore.modelforge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;

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

    public ElementCommandService(ArtifactRegistry registry,
                                       DependencyGraphService graph,
                                       SchemaRefExtractor refExtractor) {
        this.registry     = registry;
        this.graph        = graph;
        this.refExtractor = refExtractor;
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
        // A JSON Schema document is an object. Nothing upstream enforces that — saveArtifact routes
        // ELEMENT on isTextual() alone and the CORE validator has no ELEMENT schema — so an array or
        // scalar would otherwise reach the cast below and surface as a 500 instead of a rejection.
        if (schema == null || !schema.isObject()) {
            throw new IllegalArgumentException(
                "A JSON Schema Element must be a JSON object: " + urn);
        }
        ObjectNode updated = (ObjectNode) schema.deepCopy();
        String existingId = updated.path("$id").asText(null);
        if (!UrnParser.isUrn(existingId)) updated.put("$id", urn);
        Set<String> refs         = refExtractor.extractRefs(updated);
        Set<String> associations = refExtractor.extractCoreRefTargets(updated);
        String pin = registry.storeElement(
            UrnParser.nameFromUrn(urn), updated, refs, associations, null, bump, bumpFromVersion);
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
     * @return the concrete versioned URN (pin) the write resolved to
     */
    public String storeXsd(String urn, String xsdContent, VersionBump bump) {
        Set<String> importRefs = registry.extractImportRefs(xsdContent);
        String pin = registry.storeXsdElement(urn, xsdContent, importRefs, bump);
        graph.register(pin != null && !pin.isBlank() ? pin : urn, new LinkedHashSet<>(importRefs));
        return pin;
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
     * lets the artifact go (that one manifest is unlinked with it), two or more block. {@code force}
     * overrides both and unlinks from every DataSet, which may dangle references.
     *
     * <p>Each delete re-checks the live registry, so a cascade never dangles a reference, and joins
     * the caller's transaction where there is one, so several removals commit or roll back together.
     */
    public void delete(String urn, boolean cascade, boolean force) {
        String logical = UrnParser.logicalUrn(urn);
        // DataSet memberships are read before the delete removes the target's incoming edges.
        List<String> memberships = registry.dataSetMemberships(urn);
        if (!force) {
            List<String> blockers = registry.nonDataSetBlockingDependents(urn);
            if (!blockers.isEmpty()) {
                throw new ArtifactInUseException(logical, blockers);
            }
            // A member shared across several DataSets must be removed from all but one first.
            if (memberships.size() >= 2) {
                throw new ArtifactInUseException(logical, memberships);
            }
        }
        // Snapshot the members before the artifact — and with it its outgoing edges — are gone.
        List<String> members = cascade ? registry.ownedMemberUrns(urn) : List.of();
        // Counted before this container's own edge goes, or a member of two would read one.
        Set<String> heldByAnotherDataSet = members.stream()
            .filter(member -> registry.dataSetMemberships(member).size() >= 2)
            .collect(Collectors.toSet());
        registry.deleteArtifact(urn);
        graph.remove(urn);
        // Keep every DataSet manifest that listed this member consistent (the |D|=1 rule, and — with
        // force — any number), so no manifest dangles the removed member.
        for (String dataSet : memberships) {
            unlinkFromDataSet(dataSet, logical);
        }
        for (String member : members) {
            // Recursing collects a member's own orphans; its one Data Set is unlinked with it.
            if (registry.fetch(member).isPresent()
                && registry.nonDataSetBlockingDependents(member).isEmpty()
                && !heldByAnotherDataSet.contains(member)) {
                delete(member, true, force);
            }
        }
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
            registry.storeDataSet(dataSetLogicalUrn, doc, VersionBump.MINOR);
            // The graph is keyed by the version a node was registered under, and this mints a new
            // one. Without the refresh every member of the Data Set reads as removed.
            graph.registerFromRegistry(dataSetLogicalUrn);
        }
    }
}

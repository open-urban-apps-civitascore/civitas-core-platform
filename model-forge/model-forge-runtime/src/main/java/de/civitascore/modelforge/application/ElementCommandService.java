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
     * @return the concrete versioned URN (pin) the write resolved to — assigned by the write
     *     itself, so no read-back is needed
     */
    public String storeJsonSchema(String urn, JsonNode schema, VersionBump bump) {
        ObjectNode updated = (ObjectNode) schema.deepCopy();
        String existingId = updated.path("$id").asText(null);
        if (!UrnParser.isUrn(existingId)) updated.put("$id", urn);
        Set<String> refs         = refExtractor.extractRefs(updated);
        Set<String> associations = refExtractor.extractCoreRefTargets(updated);
        String pin = registry.storeElement(UrnParser.nameFromUrn(urn), updated, refs, associations, bump);
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
        delete(urn, false);
    }

    /**
     * Removes the artifact from the registry and the dependency graph, optionally cascading into
     * its members.
     *
     * <p>Model integrity: an artifact that other artifacts still reference must not disappear —
     * deleting it would leave dangling references. Every reference type blocks, a grouping (a
     * DataStructure that lists this Element) included: a "container" may be deleted freely, but its
     * "contents" are protected while any container still references them. Only the artifact's own
     * self-references are exempt (see ArtifactReferenceRepository#blockingDependents).
     *
     * <p>When {@code cascade} is set, the artifact's members (the artifacts it references) are
     * deleted after it — but each only if, now that this container is gone, nothing else references
     * it. The recursion reuses this same rule, so an orphaned member's own orphaned members are
     * removed too, while shared or mutually-referencing members are kept. Each artifact is deleted
     * in its own transaction; a member is re-checked against the live registry, so the cascade can
     * never dangle a reference even though it is not one atomic operation.
     */
    public void delete(String urn, boolean cascade) {
        List<String> blockers = registry.blockingDependents(urn);
        if (!blockers.isEmpty()) {
            throw new ArtifactInUseException(UrnParser.logicalUrn(urn), blockers);
        }
        // Snapshot the members before the artifact — and with it its outgoing edges — are gone.
        List<String> members = cascade
            ? registry.fetchArtifactRefUrns(urn).stream().map(UrnParser::logicalUrn).distinct().toList()
            : List.of();
        registry.deleteArtifact(urn);
        graph.remove(urn);
        for (String member : members) {
            // Delete a member only now that this container is gone AND nothing else references it.
            // fetch guards against an already-removed member (a self-reference, or one reached via
            // two paths in the same cascade); blockingDependents keeps shared / mutually-referencing
            // members. Recurse so an orphaned member's own orphans are collected.
            if (registry.fetch(member).isPresent() && registry.blockingDependents(member).isEmpty()) {
                delete(member, true);
            }
        }
    }
}

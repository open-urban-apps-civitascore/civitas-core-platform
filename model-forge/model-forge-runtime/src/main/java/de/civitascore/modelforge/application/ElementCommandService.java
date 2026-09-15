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
     * Removes the artifact from the registry and the dependency graph, applying the DataSet-aware
     * deletion policy (see the deletion-policy concept), optionally cascading into its members.
     *
     * <p>Model integrity — two kinds of referrer, treated differently:
     * <ul>
     *   <li><b>Non-DataSet references</b> (a Pipeline using a Mapping, a DataStructure grouping an
     *       Element, …) block unconditionally: the target must not disappear or the reference would
     *       dangle.</li>
     *   <li><b>DataSet membership</b> ({@code dataset-ref} edges) is count-based: <b>0</b> → delete;
     *       <b>1</b> → delete and auto-unlink the target from that one DataSet's manifest; <b>≥2</b>
     *       → blocked (the shared member must be removed from the other DataSets first).</li>
     * </ul>
     *
     * <p>{@code force} overrides both blocks — the target is deleted regardless of referrers, and it
     * is auto-unlinked from <em>every</em> DataSet so no manifest dangles it. Dangerous (it can leave
     * non-DataSet references dangling); use for administrative repair only.
     *
     * <p>When {@code cascade} is set, the target's members are deleted after it, but each only if it
     * becomes a fully-orphaned artifact once this container is gone (no non-DataSet referrer and no
     * remaining DataSet membership). Shared / mutually-referencing members are kept. Each delete runs
     * in its own transaction and re-checks the live registry, so cascade never dangles a reference.
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
        List<String> members = cascade
            ? registry.fetchArtifactRefUrns(urn).stream().map(UrnParser::logicalUrn).distinct().toList()
            : List.of();
        registry.deleteArtifact(urn);
        graph.remove(urn);
        // Keep every DataSet manifest that listed this member consistent (the |D|=1 rule, and — with
        // force — any number), so no manifest dangles the removed member.
        for (String dataSet : memberships) {
            unlinkFromDataSet(dataSet, logical);
        }
        for (String member : members) {
            // Cascade-delete a member only now that this container is gone AND it is fully orphaned:
            // no non-DataSet referrer and no remaining DataSet membership. Recurse so an orphaned
            // member's own orphans are collected; shared / mutually-referencing members are kept.
            if (registry.fetch(member).isPresent()
                && registry.nonDataSetBlockingDependents(member).isEmpty()
                && registry.dataSetMemberships(member).isEmpty()) {
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
        }
    }
}

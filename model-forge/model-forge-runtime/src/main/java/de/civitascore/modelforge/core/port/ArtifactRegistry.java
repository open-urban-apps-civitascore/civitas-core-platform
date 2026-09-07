package de.civitascore.modelforge.core.port;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.VersionBump;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core-facing storage boundary for Model Forge artifacts.
 *
 * <p>Model Forge is the sole version authority: every write method returns the concrete
 * <em>versioned URN</em> (the pin) the write resolved to — the newly assigned version, or the
 * existing version when the write was content-identical (idempotent). Callers use that returned
 * pin directly instead of reading the assigned version back, so the pin is correct even when the
 * write runs inside a surrounding transaction whose effects are not yet visible to other reads.
 */
public interface ArtifactRegistry {

    /**
     * Outgoing reference edges of the given artifact version, grouped by stored reference type
     * ({@code schema-ref}, {@code mapping-source}, {@code pipeline-node}, {@code dataset-ref}, …)
     * in document order. Accepts versioned, logical and {@code :latest} URNs; empty when the
     * artifact is unknown or no registry is configured.
     */
    default Map<String, List<String>> referencesByType(String urn) {
        return Map.of();
    }

    default String storeElement(String name, JsonNode schema, Set<String> refs) {
        return storeElement(name, schema, refs, Set.of(), null, VersionBump.PATCH);
    }

    default String storeElement(String name, JsonNode schema, Set<String> refs, VersionBump bump) {
        return storeElement(name, schema, refs, Set.of(), null, bump);
    }

    default String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets) {
        return storeElement(name, schema, refs, associationTargets, null, VersionBump.PATCH);
    }

    default String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 VersionBump bump) {
        return storeElement(name, schema, refs, associationTargets, null, bump);
    }

    default String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 String explicitVersion) {
        return storeElement(name, schema, refs, associationTargets, explicitVersion, VersionBump.PATCH);
    }

    /**
     * @param refs               {@code $ref} composition edges (schema-ref)
     * @param associationTargets concrete {@code x-core-ref} association (foreign-key) edges
     * @param explicitVersion    when non-blank, stored verbatim instead of Model Forge's usual
     *     version authority (1.0.0 for a new artifact, otherwise the next SemVer from
     *     {@code bump}) — the escape hatch for imports (e.g. XÖV) that must preserve an upstream
     *     version identity. Accepted as given, with no ordering check against the current version.
     * @return the concrete versioned URN (pin) the write resolved to
     */
    String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets,
                         String explicitVersion, VersionBump bump);

    /**
     * Stores an opaque CORE artifact (mapping, pipeline, datasource, datasink, dataset) at the
     * caller-declared URN, optionally adopting an explicit initial version — the same escape hatch
     * {@code storeElement}'s {@code explicitVersion} provides, for envelope imports that preserve
     * an externally managed version identity. The default delegates to the kind's store method and
     * ignores the explicit version (test doubles keep working); the Postgres registry honours it.
     *
     * @return the concrete versioned URN (pin) the write resolved to
     */
    default String storeAt(ArtifactKind kind, String urn, JsonNode content, VersionBump bump,
                            String explicitVersion) {
        return switch (kind) {
            case MAPPING -> storeMapping(urn, content, bump);
            case PIPELINE -> storePipeline(urn, content, bump);
            case DATA_SOURCE -> storeDataSource(urn, content, bump);
            case DATA_SINK -> storeDataSink(urn, content, bump);
            case DATA_SET -> storeDataSet(urn, content, bump);
            case DATA_STRUCTURE, ELEMENT -> throw new IllegalArgumentException(
                "storeAt covers the opaque CORE kinds; Elements and DataStructures are stored "
                + "through the schema paths.");
        };
    }

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storeMapping(String id, JsonNode mapping, VersionBump bump);

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storePipeline(String id, JsonNode pipeline, VersionBump bump);

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storeDataSource(String id, JsonNode source, VersionBump bump);

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storeDataSink(String id, JsonNode sink, VersionBump bump);

    default String storeDataSet(String urn, JsonNode manifest) {
        return storeDataSet(urn, manifest, VersionBump.PATCH);
    }

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storeDataSet(String urn, JsonNode manifest, VersionBump bump);

    default String storeDataStructure(String urn, JsonNode manifest) {
        return storeDataStructure(urn, manifest, VersionBump.PATCH);
    }

    /** @return the concrete versioned URN (pin) the write resolved to */
    String storeDataStructure(String urn, JsonNode manifest, VersionBump bump);

    void deleteArtifact(String urn);

    /**
     * Logical URNs of artifacts whose stored references point at any version of the given
     * artifact and therefore block its deletion. Every reference type blocks — a grouping that
     * merely lists the artifact ({@code datastructure-ref}) protects it just as a hard dependency
     * does, so a "content" cannot be deleted while any "container" still references it. Only the
     * artifact's own self-references do not block. Empty when nothing blocks the delete.
     */
    List<String> blockingDependents(String urn);

    /**
     * Non-DataSet dependents that block deletion (referential integrity). DataSet membership is
     * counted separately by the deletion policy via {@link #dataSetMemberships}.
     */
    List<String> nonDataSetBlockingDependents(String urn);

    /** Logical URNs of the DataSets this artifact is a member of ({@code dataset-ref} in-edges). */
    List<String> dataSetMemberships(String urn);

    Optional<JsonNode> fetch(String urn);

    List<String> fetchArtifactRefUrns(String urn);

    /**
     * Logical URNs of every artifact, of every type. Backs the whole-graph rebuild: the
     * dependency graph indexes the durable per-type {@code artifact_reference} edges across all
     * artifact kinds, not just Elements.
     */
    List<String> listAllUrns();


    List<Map<String, String>> mappingsForElement(String datasetUrn, String role);

    default String storeXsdElement(String urn, String xsd) {
        return storeXsdElement(urn, xsd, extractImportRefs(xsd));
    }

    default String storeXsdElement(String urn, String xsd, Set<String> refs) {
        return storeXsdElement(urn, xsd, refs, null, VersionBump.PATCH);
    }

    default String storeXsdElement(String urn, String xsd, Set<String> refs, VersionBump bump) {
        return storeXsdElement(urn, xsd, refs, null, bump);
    }

    default String storeXsdElement(String urn, String xsd, Set<String> refs, String explicitVersion) {
        return storeXsdElement(urn, xsd, refs, explicitVersion, VersionBump.PATCH);
    }

    /**
     * @param explicitVersion when non-blank, stored verbatim instead of Model Forge's usual
     *     version authority — see {@link #storeElement}'s equivalent parameter
     * @return the concrete versioned URN (pin) the write resolved to
     */
    String storeXsdElement(String urn, String xsd, Set<String> refs, String explicitVersion, VersionBump bump);

    void rebuildNamespaceIndex();

    Set<String> extractImportRefs(String xsd);

    Optional<JsonNode> fetchElementOrXsd(String urn);

    Optional<String> resolveReference(String referenceUrn);

    List<ArtifactSearchResult> searchArtifacts(ArtifactSearchCriteria criteria);
}

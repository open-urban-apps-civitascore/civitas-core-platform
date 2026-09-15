package de.civitascore.modelforge.core.port;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.VersionBump;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

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
     * Runs {@code work} so that every registry write inside it commits or rolls back together.
     *
     * <p>This is the seam that lets an application-layer operation spanning several writes — a
     * multi-element import, say — be atomic without the application layer depending on a
     * transaction framework. An implementation that has no transactions (an in-memory test double)
     * keeps the default, which simply runs the work; a store that does have them overrides it. The
     * individual write methods join the surrounding transaction rather than opening their own, so
     * nesting is safe.
     *
     * <p>Only durable state is covered. An in-memory projection such as the dependency graph is not
     * rolled back, so publish to it <em>after</em> this method returns, never inside {@code work}.
     */
    default <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

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

    default String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 String explicitVersion, VersionBump bump) {
        return storeElement(name, schema, refs, associationTargets, explicitVersion, bump, null);
    }

    /**
     * @param refs               {@code $ref} composition edges (schema-ref)
     * @param associationTargets concrete {@code x-core-ref} association (foreign-key) edges
     * @param explicitVersion    when non-blank, stored verbatim instead of Model Forge's usual
     *     version authority (1.0.0 for a new artifact, otherwise the next SemVer from
     *     {@code bump}) — the escape hatch for imports (e.g. XÖV) that must preserve an upstream
     *     version identity. Accepted as given, with no ordering check against the current version.
     * @param bumpFromVersion    the existing version {@code bump} counts from; {@code null} counts
     *     from the artifact's newest version. A caller whose versions of this artifact form
     *     independent lines names the version it revises here, so the bump stays inside that line.
     * @return the concrete versioned URN (pin) the write resolved to
     */
    String storeElement(String name, JsonNode schema, Set<String> refs, Set<String> associationTargets,
                         String explicitVersion, VersionBump bump, String bumpFromVersion);

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
        return storeDataStructure(urn, manifest, VersionBump.PATCH, null);
    }

    default String storeDataStructure(String urn, JsonNode manifest, VersionBump bump) {
        return storeDataStructure(urn, manifest, bump, null);
    }

    /**
     * @param bumpFromVersion the existing version {@code bump} counts from; {@code null} counts from
     *     the artifact's newest version. A caller whose grouping versions form independent lines
     *     names the version it revises here, so the bump stays inside that line.
     * @return the concrete versioned URN (pin) the write resolved to
     */
    String storeDataStructure(String urn, JsonNode manifest, VersionBump bump, String bumpFromVersion);

    /**
     * Copies the artifact's current version to a new version numbered at {@code bump} — content,
     * every stored representation and every reference edge — and makes it current. Always writes a
     * version: the content is unchanged by definition, so the byte-identical short-circuit the store
     * methods rely on would otherwise make this a no-op.
     *
     * @return the versioned URN the new version was written under
     */
    String bumpVersion(String logicalUrn, VersionBump bump);

    void deleteArtifact(String urn);

    /**
     * Logical URNs of artifacts whose stored references point at the given artifact and therefore
     * block its deletion. Every reference type blocks — a grouping that merely lists the artifact
     * ({@code datastructure-ref}) protects it just as a hard dependency does, so a "content" cannot
     * be deleted while any "container" still references it. Only the artifact's own self-references
     * do not block. Empty when nothing blocks the delete.
     *
     * <p>Only a referrer's current version counts. A reference held by a superseded version records
     * what that version declared and does not constrain its target.
     */
    List<String> blockingDependents(String urn);

    /**
     * Non-DataSet dependents that block deletion (referential integrity). DataSet membership is
     * counted separately by the deletion policy via {@link #dataSetMemberships}.
     */
    List<String> nonDataSetBlockingDependents(String urn);

    /**
     * Like {@link #nonDataSetBlockingDependents(String)}, restricted to the references that hold one
     * version of the target. A reference pinned to another version constrains that version, not this
     * one; a reference naming no version holds every version. A null {@code targetVersion} asks for
     * the artifact as a whole, which is the question a delete asks.
     */
    List<String> nonDataSetBlockingDependents(String urn, String targetVersion);

    /** Logical URNs of the DataSets this artifact is a member of ({@code dataset-ref} in-edges). */
    List<String> dataSetMemberships(String urn);

    /**
     * Logical URNs of the artifacts this one owns — the set a cascading delete may take with it. A
     * grouping owns its Elements, a pipeline its Mapping, a Data Set its pipelines and mappings;
     * every other edge names something used rather than owned.
     */
    List<String> ownedMemberUrns(String urn);

    /**
     * Like {@link #ownedMemberUrns(String)}, read from one version of the owner. Each member is
     * returned as that owner stored it, version and all. A null {@code ownerVersion} reads the
     * owner's current version.
     */
    List<String> ownedMemberUrns(String urn, String ownerVersion);

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

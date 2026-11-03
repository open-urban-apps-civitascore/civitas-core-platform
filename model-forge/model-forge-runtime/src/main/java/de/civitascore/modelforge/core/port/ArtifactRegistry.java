package de.civitascore.modelforge.core.port;

import tools.jackson.databind.JsonNode;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.VersionBump;
import java.util.Collection;
import java.util.LinkedHashSet;
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
     * rolled back, so publish to it through {@link #afterCommit(Runnable)}, never inside
     * {@code work}.
     */
    default <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }

    /**
     * Runs {@code action} once the surrounding transaction has committed, and never when it rolls
     * back. The surrounding transaction may be a host's own one that {@link #inTransaction} joined,
     * so the action waits for that commit, not for {@code inTransaction} to return. Without a
     * surrounding transaction, the action runs at once; an implementation that has no transactions
     * keeps that default.
     */
    default void afterCommit(Runnable action) {
        action.run();
    }

    /**
     * Holds the write lock of the artifact until the surrounding transaction ends, also when the
     * registry does not hold the artifact yet. The store methods take the same lock, so a caller
     * that reads an artifact before it writes it sees no concurrent write in between. Callers that
     * lock several artifacts take the locks in one fixed order to prevent deadlocks. An
     * implementation that has no transactions has no concurrent writers and keeps the default.
     */
    default void lockForWrite(String logicalUrn) {
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

    default String storeElement(JsonNode schema, Set<String> refs) {
        return storeElement(schema, refs, Set.of(), null, VersionBump.PATCH);
    }

    default String storeElement(JsonNode schema, Set<String> refs, VersionBump bump) {
        return storeElement(schema, refs, Set.of(), null, bump);
    }

    default String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets) {
        return storeElement(schema, refs, associationTargets, null, VersionBump.PATCH);
    }

    default String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 VersionBump bump) {
        return storeElement(schema, refs, associationTargets, null, bump);
    }

    default String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 String explicitVersion) {
        return storeElement(schema, refs, associationTargets, explicitVersion, VersionBump.PATCH);
    }

    default String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets,
                                 String explicitVersion, VersionBump bump) {
        return storeElement(schema, refs, associationTargets, explicitVersion, bump, null);
    }

    /**
     * @param refs               {@code $ref} composition edges (schema-ref)
     * @param associationTargets concrete {@code x-core-ref} association (foreign-key) edges
     * @param explicitVersion    when non-blank, stored verbatim instead of Model Forge's usual
     *     version authority (1.0.0 for a new artifact, or the next major after the highest version
     *     its URN ever had; otherwise the next SemVer from {@code bump}) — the escape hatch for
     *     imports (e.g. XÖV) that must preserve an upstream version identity. Accepted as given,
     *     with no ordering check against the current version or the numbers deleted before.
     * @param bumpFromVersion    the existing version {@code bump} counts from; {@code null} counts
     *     from the artifact's newest version. A caller whose versions of this artifact form
     *     independent lines names the version it revises here, so the bump stays inside that line.
     * @return the concrete versioned URN (pin) the write resolved to
     */
    String storeElement(JsonNode schema, Set<String> refs, Set<String> associationTargets,
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
     * Copies one stored version of an artifact to a new version — every stored representation and
     * every Reference — and makes the copy current. Always writes a version: the content is
     * unchanged apart from {@code retargets}, so the byte-identical short-circuit the store methods
     * rely on would otherwise make this a no-op.
     *
     * <p>The copy is numbered at the next MAJOR version above the highest version the artifact ever
     * had, or above {@code countAbove} when that is higher. Copies of several artifacts that each pass the
     * highest version of the others therefore share one number.
     *
     * @param sourcePin the Versioned URN of the version to copy
     * @param countAbove a version the new number must exceed; {@code null} for none
     * @param retargets Versioned URNs to replace in the copy, old to new: in the targets of its
     *     References and in every {@code $ref} value of its JSON content
     * @return the Pin of the copy
     * @throws IllegalArgumentException when the artifact or the source version is unknown
     * @throws de.civitascore.modelforge.contract.RegistryConflictException when the new version
     *     exists already
     */
    String copyVersion(String sourcePin, String countAbove, Map<String, String> retargets);

    void deleteArtifact(String urn);

    /** The versions the registry holds of the artifact, lowest first; empty when it holds none. */
    List<String> versionsOf(String urn);

    /**
     * The versioned URNs of every version, of any artifact and whether current or not, that owns
     * any of these pinned versions, as {@link #ownedMemberUrns(String, String)} reads ownership.
     */
    List<String> versionOwners(Collection<String> pinnedUrns);

    /**
     * Deletes the given versions of the artifact, with their representations and outgoing
     * references, and moves its current version to the highest version left. A reference that
     * pinned a deleted version stays, unresolved. The numbers stay taken.
     *
     * @throws IllegalArgumentException when no version would be left; that is
     *     {@link #deleteArtifact}
     */
    void deleteVersions(String urn, Collection<String> versions);

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

    /**
     * Live non-DataSet referrers of versions selected for deletion. References from versions in
     * {@code removedPins} do not block. An unpinned reference from {@code siblingUrns} does not block
     * while another version of its target survives; explicit pins from surviving versions do.
     * A referrer is live when current, an Element or Data Structure version, or pinned by a reference.
     */
    List<String> versionDeletionBlockers(Collection<String> removedPins, Collection<String> siblingUrns);

    /** Pinned member versions owned by the given versions and by no version outside that set. */
    List<String> exclusivelyOwnedMemberVersions(Collection<String> ownerPins);

    /** Logical URNs of the DataSets this artifact is a member of ({@code dataset-ref} in-edges). */
    List<String> dataSetMemberships(String urn);

    /**
     * Logical URNs of the artifacts this one owns — the set a cascading delete may take with it.
     * Ownership is carried by the edge rather than the target's kind, so an artifact merely used is
     * not owned.
     */
    List<String> ownedMemberUrns(String urn);

    /**
     * Like {@link #ownedMemberUrns(String)}, read from one version of the owner. Each member is
     * returned as that owner stored it, version and all. A null {@code ownerVersion} reads the
     * owner's current version.
     */
    List<String> ownedMemberUrns(String urn, String ownerVersion);

    /** Logical URNs of the Elements any version of the Data Structure contains. */
    List<String> dataStructureMembersOfAnyVersion(String urn);

    /** Logical URNs of the Data Structures that list the Element in any version. */
    List<String> dataStructuresListing(String elementUrn);

    /** The {@code $defs} keys any version of the Data Structure holds. */
    Set<String> dataStructureMemberKeys(String dataStructureUrn);

    Optional<JsonNode> fetch(String urn);

    List<String> fetchArtifactRefUrns(String urn);

    /**
     * Every stored reference edge, keyed by the <em>versioned</em> URN that holds it, targets
     * verbatim and in document order. Backs the whole-graph rebuild, in one read.
     *
     * <p>Keyed per version, not per artifact: a reference may pin a superseded version, and only a
     * node of its own carries what that version declared. A version holding no reference
     * contributes no entry.
     */
    Map<String, List<String>> referenceEdgesByVersion();

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

    /**
     * Of the given reference URNs, the subset this registry holds — the bulk form of
     * {@link #resolveReference}, so a caller asking about many URNs is not one round trip per URN.
     *
     * <p>The rule per URN is exactly {@code resolveReference(urn).isPresent()}: a <em>pinned</em>
     * URN is held only when that concrete version exists; a logical or {@code :latest} URN is held
     * when the artifact exists at any version. Returned strings are the caller's own input, never
     * normalised, so the missing subset is a plain set difference.
     *
     * <p>Null and blank entries are ignored and an empty input touches no store. This default
     * answers by looping {@link #resolveReference}; an adapter that can do it in one query
     * overrides it.
     */
    default Set<String> heldUrns(Collection<String> urns) {
        if (urns == null || urns.isEmpty()) {
            return Set.of();
        }
        Set<String> held = new LinkedHashSet<>();
        for (String urn : urns) {
            if (urn != null && !urn.isBlank() && resolveReference(urn).isPresent()) {
                held.add(urn);
            }
        }
        return held;
    }

    List<ArtifactSearchResult> searchArtifacts(ArtifactSearchCriteria criteria);

    /**
     * Records {@code name} as the display name of the artifact {@code urn} names, for a document that
     * carries no {@code title} of its own. A no-op when the registry does not hold the artifact.
     */
    void nameArtifact(String urn, String name);

    /**
     * The display name of each held artifact among {@code logicalUrns}, keyed by logical URN. An
     * artifact the registry does not hold has no entry; an empty input touches no store.
     */
    Map<String, String> displayNames(Collection<String> logicalUrns);
}

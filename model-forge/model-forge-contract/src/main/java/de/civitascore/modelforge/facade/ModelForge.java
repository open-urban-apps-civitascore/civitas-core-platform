package de.civitascore.modelforge.facade;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.contract.ArtifactView;
import de.civitascore.modelforge.contract.ArtifactWriteResult;
import de.civitascore.modelforge.contract.BumpVersionCommand;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.DependencyClosureView;
import de.civitascore.modelforge.contract.DependencyGraphView;
import de.civitascore.modelforge.contract.DependencyQuery;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.ImportSmartDataModelCommand;
import de.civitascore.modelforge.contract.ImportXRepositoryCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.SchemaViewQuery;
import de.civitascore.modelforge.contract.ValidateInstanceCommand;
import de.civitascore.modelforge.contract.ValidateSchemaCommand;
import de.civitascore.modelforge.contract.ValidationFailedException;
import de.civitascore.modelforge.contract.NonConformingArtifact;
import de.civitascore.modelforge.contract.ValidationResult;
import de.civitascore.modelforge.contract.XRepositoryHit;
import de.civitascore.modelforge.contract.XRepositorySearchQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Public Java entry point for embedded Model Forge usage.
 *
 * <p>Methods are grouped below as: import, read, validate, dependency graph, search, write,
 * delete. The API is uniform in its argument and return shapes:
 * <ul>
 *   <li><b>Arguments</b> — every operation takes a single argument: either the {@link ArtifactId}
 *       identity value object (for the identity-only operations {@link #getArtifact} and
 *       {@link #deleteArtifact}), or a dedicated {@code Command}/{@code Query} record. No operation
 *       takes a raw {@code JsonNode} or a loose primitive; adding a parameter therefore means adding
 *       a field to the record, never changing a signature (see {@link SchemaViewQuery#maxDepth()}
 *       and {@link DependencyQuery#maxDepth()}, both added that way).
 *   <li><b>Returns</b> — reads return a typed view ({@link ArtifactView}), never a raw
 *       {@code JsonNode}; writes return a typed result ({@link ImportResult} /
 *       {@link ArtifactWriteResult}); an absent single result is an {@link Optional}.
 * </ul>
 */
public interface ModelForge {

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Imports one JSON Schema document. The returned {@link ImportResult#rootArtifactId()} is a
     * <em>versioned</em> URN pinning the concrete version the registry assigned — store it to
     * reference exactly this version later (Model Forge owns version assignment; see
     * {@link ImportResult}).
     *
     * <p>The import stores the Elements (splitting {@code $defs}) and their automatic
     * DataStructure grouping. Composition into a DataSet is the caller's concern: build or update
     * the DataSet manifest through {@link #saveArtifact(SaveArtifactCommand)}.
     *
     * @throws ValidationFailedException when the document fails schema validation or has an
     *     unresolved {@code x-core-ref} foreign key
     */
    ImportResult importSchema(ImportSchemaCommand command);

    /**
     * Imports a JSON Schema from the public Smart Data Models catalogue.
     *
     * @throws ValidationFailedException when the fetched document fails schema validation or has
     *     an unresolved {@code x-core-ref} foreign key
     */
    ImportResult importFromSmartDataModels(ImportSmartDataModelCommand command);

    /**
     * Downloads and imports one artifact from the XRepository (xOEV) catalog — either converted
     * to JSON Schema Elements, or stored as a raw XSD artifact (see
     * {@link ImportXRepositoryCommand#importAsXsd()}). When converting, every type the XSD
     * declares is imported atomically as one document, so {@link ImportResult#importedArtifactIds()}
     * lists all of them — the same single-document convention as {@link #importSchema}.
     *
     * @throws ValidationFailedException when the (converted or raw) document fails schema
     *     validation or has an unresolved {@code x-core-ref} foreign key
     */
    ImportResult importFromXRepository(ImportXRepositoryCommand command);

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Reads an artifact's authored content. Accepts both a logical (version-free) URN — reads the
     * current version — and a versioned URN — reads exactly that version. See {@link ArtifactId}.
     */
    Optional<ArtifactView> getArtifact(ArtifactId artifactId);

    /**
     * Bundled view: dependencies embedded under {@code $defs}. The query's {@link ArtifactId}
     * may be logical (current version) or versioned (that exact version).
     */
    Optional<ArtifactView> getBundledView(SchemaViewQuery query);

    /**
     * Inlined view: same artifact as {@link #getBundledView(SchemaViewQuery)}, but with every
     * CORE-URN {@code $ref} recursively inlined instead of embedded under {@code $defs}. See
     * {@code ViewService} for when to prefer one over the other.
     */
    Optional<ArtifactView> getInlinedView(SchemaViewQuery query);

    /**
     * Of the given ids, the subset the registry holds — one call in place of a {@link #getArtifact}
     * per id, and without reading any content.
     *
     * <p>A <em>versioned</em> id is held only when that concrete version exists; a logical or
     * {@code :latest} id is held when the artifact exists at any version. Returned ids are the
     * caller's own, verbatim, so the missing subset is a plain set difference. An empty input yields
     * an empty result.
     *
     * <p>Takes a collection rather than a Command record, as {@link #orphans(ArtifactKind)} takes a
     * bare kind: the question carries no parameters beyond the ids themselves.
     */
    Set<ArtifactId> existing(Collection<ArtifactId> artifactIds);

    // ── Validate ─────────────────────────────────────────────────────────────

    ValidationResult validateSchema(ValidateSchemaCommand command);

    ValidationResult validateInstance(ValidateInstanceCommand command);

    // ── Dependency graph ─────────────────────────────────────────────────────

    DependencyGraphView dependencies(DependencyQuery query);

    /** Reverse of {@link #dependencies}: artifacts that reference this one. */
    DependencyGraphView dependents(DependencyQuery query);

    /** Mappings that use this artifact as their source, as {@code maps-to} edges. */
    DependencyGraphView mapsTo(DependencyQuery query);

    /** Mappings that use this artifact as their target, as {@code mapped-from} edges. */
    DependencyGraphView mappedFrom(DependencyQuery query);

    /**
     * The transitive dependency closure of one artifact together with the part of it that does not
     * resolve — "is everything this model participates in actually there", answered in one call
     * rather than an existence probe per member.
     *
     * <p>The query's {@link DependencyQuery#maxDepth()} is <b>required</b> here, unlike on
     * {@link #dependencies}: a closure walks as far as it is told to, so the bound belongs to the
     * caller that knows what it is willing to spend. Omitting it would leave the API either walking
     * the whole graph on a caller's request or answering one hop while reading as complete — the
     * first unbounded, the second silently wrong. A depth of zero or less yields an empty closure.
     *
     * <p>See {@link DependencyClosureView} for the members' URN form and the root's exclusion.
     * Traversal terminates on a cyclic graph.
     *
     * @throws IllegalArgumentException when the query carries no {@code maxDepth}
     */
    DependencyClosureView closure(DependencyQuery query);

    // ── Search ───────────────────────────────────────────────────────────────

    /** Searches the local artifact registry. */
    List<ArtifactSummary> search(ArtifactSearchQuery query);

    /** Searches the external XRepository (xOEV) catalog; import a hit via {@link #importFromXRepository}. */
    List<XRepositoryHit> searchXRepository(XRepositorySearchQuery query);

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Creates a <em>new</em> Mapping, Pipeline, DataSource, DataSink, DataSet or DataStructure.
     * Model Forge mints the URN from the command's display name (clean {@code name} segment plus a
     * short disambiguator segment, so equal names never collide), stamps it into the document's {@code id} and
     * returns the <em>versioned</em> pin plus the artifact's outgoing
     * {@link ArtifactWriteResult#dependencies() dependency lists}. Store the returned id and pass
     * it to {@link #saveArtifact(SaveArtifactCommand)} for follow-up versions. Elements are
     * created via {@link #importSchema(ImportSchemaCommand)} instead.
     *
     * @throws IllegalArgumentException when {@code command.kind()} is {@code ELEMENT}
     * @throws ValidationFailedException when the content fails schema validation or has an
     *     unresolved {@code x-core-ref} foreign key
     */
    ArtifactWriteResult createArtifact(CreateArtifactCommand command);

    /**
     * Stores a new version of an artifact and returns the <em>versioned</em> pin the registry
     * assigned plus the version's outgoing {@link ArtifactWriteResult#dependencies() dependency
     * lists}. The version segment of the command's {@code artifactId} is not authoritative —
     * Model Forge assigns the SemVer version from the command's {@code versionBump} — so the
     * returned pin, not the input, is what a caller stores.
     *
     * @throws ValidationFailedException when the content fails schema validation or has an
     *     unresolved {@code x-core-ref} foreign key
     */
    ArtifactWriteResult saveArtifact(SaveArtifactCommand command);

    /**
     * Carries an artifact's current version forward as a new version numbered at the requested change
     * class, with its content, every stored format and its reference edges unchanged, and makes it
     * current. The new version is built from the stored rows, so a caller never reads content out and
     * writes it back — a round trip that would drop the edges the content does not spell out.
     *
     * <p>This <em>always</em> mints a version, unlike every other write, which returns the existing
     * pin when the content is byte-identical. Carrying unchanged content forward is the whole point.
     *
     * @throws IllegalArgumentException when the command's id pins a version, or the artifact is
     *     unknown, or it holds no version to carry forward
     */
    ArtifactWriteResult bumpVersion(BumpVersionCommand command);

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Deletes the artifact (all versions) behind a logical URN. Equivalent to
     * {@link #deleteArtifact(ArtifactId, boolean) deleteArtifact(artifactId, false)}.
     *
     * @throws de.civitascore.modelforge.contract.ArtifactInUseException when any other artifact
     *     still references this one — the model stays intact; remove or update the listed dependents
     *     first. The policy is the one defined on
     *     {@link #deleteArtifact(ArtifactId, boolean, boolean)}: every reference type blocks
     *     unconditionally (a grouping protects its member too) except DataSet membership, which is
     *     count-based; self-references never block.
     */
    default void deleteArtifact(ArtifactId artifactId) {
        deleteArtifact(artifactId, false, false);
    }

    default void deleteArtifact(ArtifactId artifactId, boolean cascade) {
        deleteArtifact(artifactId, cascade, false);
    }

    /**
     * Deletes the artifact (all versions) behind a logical URN under the DataSet-aware deletion
     * policy (see the deletion-policy concept), optionally cascading into its members.
     *
     * <p>Non-DataSet references block unconditionally (referential integrity). DataSet membership is
     * count-based: 0 → delete; 1 → delete and auto-unlink from that DataSet's manifest; ≥2 → blocked
     * (remove from the other DataSets first).
     *
     * <p>With {@code cascade}, the artifacts the target <em>owns</em> go too — a grouping's
     * Elements, a pipeline's Mapping, a DataSet's pipelines and mappings — each only while nothing
     * else holds it, under the same two rules. Ownership is read off the reference, so what a
     * DataSet merely groups (its data structures, sources and sinks) stays. {@code force} overrides
     * both blocks and unlinks from every DataSet; it may dangle references, so administrative
     * repair only.
     *
     * @throws de.civitascore.modelforge.contract.ArtifactInUseException when a non-DataSet artifact
     *     still references the target, or it is a member of ≥2 DataSets, and {@code force} is false.
     */
    void deleteArtifact(ArtifactId artifactId, boolean cascade, boolean force);

    /**
     * What stands in the way of deleting the artifact, without attempting the delete. Asked of the
     * whole set a cascade removes, so a grouping does not report the Elements it owns. Empty when
     * nothing stands in the way.
     *
     * <p>Memberships count for the named artifact only, from the second onwards.
     *
     * <p>A versioned URN asks about that version alone; a logical URN about the artifact as a whole,
     * which is the question a delete asks. Answered from the stored references, not the in-memory
     * graph.
     */
    List<String> deletionBlockers(ArtifactId artifactId);

    // ── DataSet membership ─────────────────────────────────────────────────────

    /**
     * Artifacts of the given kind that belong to no DataSet (no {@code dataset-ref} in-edge) — the
     * generic "orphans by type" query (e.g. all Mappings / Pipelines / DataSources in no DataSet), so
     * they can be reviewed, assigned, or cleaned up.
     */
    List<ArtifactSummary> orphans(ArtifactKind kind);

    /**
     * Stored Elements whose schema does not conform to JSON Schema 2020-12 — the ones written before
     * the write paths enforced conformance, which a re-save would now refuse.
     *
     * <p>Reads of these artifacts keep working; this is the inventory to review before treating the
     * registry as uniformly conforming. An empty list means every stored Element conforms.
     */
    List<NonConformingArtifact> nonConformingElements();

    /**
     * Explicitly adds a reusable artifact to a DataSet's manifest (a {@code dataset-ref} member),
     * independent of any Pipeline that uses it. Idempotent; no-op if already a member.
     */
    void linkToDataSet(ArtifactId dataSet, ArtifactId member);

    /** Explicitly removes an artifact from a DataSet's manifest. Idempotent. */
    void unlinkFromDataSet(ArtifactId dataSet, ArtifactId member);
}

package de.civitascore.modelforge.graph;

import de.civitascore.modelforge.core.port.ArtifactRegistry;
import de.civitascore.modelforge.urn.UrnParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-memory dependency graph over <strong>all</strong> artifact types.
 *
 * Rebuilt at startup from the durable {@code artifact_reference} table and updated whenever an
 * artifact is stored. Serves as the runtime index; the durable source of dependency edges is
 * {@code artifact_reference}, whose rows are extracted per artifact type by the persistence layer
 * (Element {@code $ref}/{@code xs:import}, Mapping source/target, Pipeline nodes, DataStructure
 * {@code elementRefs}, DataSet {@code *Refs}). This service does not re-derive edges from content —
 * it mirrors those per-type rows, so every artifact kind's relations are navigable, not just
 * Elements'.
 *
 * <p>Nodes are <strong>versioned</strong> URNs, one per version that holds references, and edges
 * are kept verbatim (a pinned {@code …:1.0.0} or the {@code …:latest} token). A query by a logical
 * or {@code …:latest} URN — and a {@code latest} edge target — is resolved to the target's current
 * version at read time (via {@code ArtifactRegistry.resolveReference}); a pinned URN is used as-is.
 *
 *   A → B  means: artifact A references artifact B  (A depends on B)
 */
public class DependencyGraphService {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphService.class);

    // outgoing:  urn → set of URNs it references
    private final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    // incoming:  urn → set of URNs that reference it
    private final Map<String, Set<String>> dependents   = new ConcurrentHashMap<>();

    // Serialises register/remove so forward and reverse edges stay consistent.
    private final ReentrantLock graphLock = new ReentrantLock();

    private final ArtifactRegistry registry;

    public DependencyGraphService(ArtifactRegistry registry) {
        this.registry = registry;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Rebuild the full dependency graph from the durable {@code artifact_reference} rows of every
     * artifact, of every type.
     *
     * <p>Edges are read from the persisted per-type reference table (not re-derived from content),
     * so the in-memory graph stays consistent with the durable source and covers every artifact
     * kind. The full reference graph — including cycles — is retained, so the graph survives a
     * restart unchanged.
     *
     * <p>Keyed per version, from {@link ArtifactRegistry#referenceEdgesByVersion()}. Also rebuilds
     * the XSD namespace→URN index (required before xs:import resolution).
     */
    public void rebuild() {
        // Clear both maps atomically under the lock so a concurrent register()/remove() cannot
        // interleave between the two clears and leave a forward edge without its reverse edge.
        graphLock.lock();
        try {
            dependencies.clear();
            dependents.clear();
        } finally {
            graphLock.unlock();
        }

        // rebuildNamespaceIndex already parallelises internally
        registry.rebuildNamespaceIndex();

        Map<String, List<String>> edges = registry.referenceEdgesByVersion();
        int rowsRead = edges.values().stream().mapToInt(List::size).sum();
        graphLock.lock();
        try {
            edges.forEach(this::registerLocked);
        } finally {
            graphLock.unlock();
        }

        // Every version the read returned must have become a node. Registration is the only step
        // between the two counts, so a mismatch is a defect here, not a short read.
        if (dependencies.size() != edges.size()) {
            log.warn("Dependency graph indexed {} of the {} version(s) read",
                dependencies.size(), edges.size());
        }
        log.info("Dependency graph built from artifact_reference: {} node(s), {} edge(s) from {} row(s)",
            dependencies.size(),
            dependencies.values().stream().mapToInt(Set::size).sum(),
            rowsRead);
    }

    /**
     * Register (or refresh) an artifact's outgoing edges from its durable {@code artifact_reference}
     * rows — the sync primitive every write path shares, for every artifact type.
     *
     * <p>A URN that names a version is registered under that version. A logical or {@code latest}
     * URN resolves to the current one. A write therefore has to pass the pin it was given back:
     * a version written onto a line that is not the newest never becomes current, and resolving
     * would index a different version than the one just stored.
     */
    public void registerFromRegistry(String urn) {
        String node = resolveToVersioned(urn);
        Set<String> refs = new LinkedHashSet<>(registry.fetchArtifactRefUrns(node));
        register(node, refs);
    }

    /**
     * Register or update outgoing references for a versioned schema URN.
     *
     * <p>The {@code fromUrn} (a versioned node) and its {@code toUrns} (verbatim pinned or
     * {@code latest} references) are stored as-is — no logical normalisation. Re-registering
     * the same versioned node replaces its edge set; a new version is a distinct node.
     *
     * <p>Called whenever an Element is stored or imported.
     */
    public void register(String fromUrn, Set<String> toUrns) {
        graphLock.lock();
        try {
            registerLocked(fromUrn, toUrns);
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Forward/reverse edge update for {@code fromUrn}. The caller <strong>must</strong>
     * hold {@link #graphLock} so the forward and reverse maps stay consistent.
     */
    private void registerLocked(String fromUrn, Collection<String> toUrns) {
        Set<String> refs = new LinkedHashSet<>(toUrns);   // verbatim (pinned :version or :latest)

        // Remove old reverse entries for this exact node (re-registration replaces its edges).
        Set<String> old = dependencies.getOrDefault(fromUrn, Set.of());
        old.forEach(dep -> dependents.computeIfPresent(dep,
            (k, v) -> { v.remove(fromUrn); return v.isEmpty() ? null : v; }));

        dependencies.put(fromUrn, refs);

        for (String ref : refs) {
            dependents.computeIfAbsent(ref, k -> ConcurrentHashMap.newKeySet()).add(fromUrn);
        }
    }

    /**
     * The concrete versioned node a query or edge-target URN maps to: a pinned {@code :version}
     * is returned as-is (no lookup); a logical or {@code :latest} URN resolves to the target's
     * current version via the registry, falling back to the input when no registry is configured
     * or the target is absent.
     */
    private String resolveToVersioned(String urn) {
        if (UrnParser.versionFromUrn(urn) != null && !UrnParser.isLatest(urn)) return urn;
        return registry.resolveReference(urn).orElse(urn);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Direct outgoing references (schemas this schema depends on), as concrete versioned URNs.
     * A {@code latest}/logical edge target is resolved to its current version at read time.
     */
    public Set<String> getDependencies(String urn) {
        String node = resolveToVersioned(urn);
        Set<String> direct = dependencies.get(node);
        if (direct == null) {
            // Defensive fallback to the logical-keyed node (e.g. one registered before its version
            // was resolvable). Apply it only when the query targets a version that actually exists —
            // a logical/:latest input, or a pinned version the registry can resolve — so a bogus
            // pinned version (e.g. …:9.9.9 that was never stored) returns empty rather than another
            // version's edges.
            boolean targetsExistingVersion = UrnParser.versionFromUrn(urn) == null
                || UrnParser.isLatest(urn)
                || registry.resolveReference(urn).isPresent();
            direct = targetsExistingVersion
                ? dependencies.getOrDefault(UrnParser.logicalUrn(node), Set.of())
                : Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String target : direct) {
            out.add(resolveToVersioned(target));
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Direct incoming references (schemas that depend on this schema). Unions the dependents that
     * pin this exact version, those that track it via {@code :latest}, and those that reference the
     * logical URN — so a {@code latest} reference counts as a dependent of the current version.
     *
     * <p>Includes referrers whose declaring version is superseded — not the rule deletion applies,
     * which counts a referrer's current version only and reads the durable rows.
     */
    public Set<String> getDependents(String urn) {
        String node = resolveToVersioned(urn);
        String logical = UrnParser.logicalUrn(node);
        // Reverse-dependency sets are ConcurrentHashMap.newKeySet() (no defined iteration order),
        // so sort by URN into a TreeSet for a stable, deterministic display order. Contents are
        // unchanged; only the ordering becomes deterministic.
        Set<String> out = new TreeSet<>();
        out.addAll(dependents.getOrDefault(node, Set.of()));
        out.addAll(dependents.getOrDefault(UrnParser.withVersion(logical, UrnParser.LATEST), Set.of()));
        out.addAll(dependents.getOrDefault(logical, Set.of()));
        return Collections.unmodifiableSet(new LinkedHashSet<>(out));
    }

    /**
     * Transitive dependencies reachable within {@code maxDepth} hops (level-bounded BFS).
     * {@code maxDepth = 1} returns the direct dependencies; {@code <= 0} returns empty;
     * {@code Integer.MAX_VALUE} walks the full transitive closure. Cycle-safe via the visited set.
     */
    public Set<String> getTransitiveDependencies(String urn, int maxDepth) {
        return transitiveVisited(urn, maxDepth, this::getDependencies);
    }

    /**
     * Reverse of {@link #getTransitiveDependencies}: artifacts that transitively reference
     * {@code urn}, reachable within {@code maxDepth} hops. Same bounds and cycle-safety.
     */
    public Set<String> getTransitiveDependents(String urn, int maxDepth) {
        return transitiveVisited(urn, maxDepth, this::getDependents);
    }

    /**
     * What a depth-bounded walk reached, and whether the bound stopped it short: {@code truncated}
     * is true when an artifact the walk never reached lies immediately beyond it.
     */
    public record BoundedWalk(Set<String> visited, boolean truncated) {
        public BoundedWalk {
            // Order-preserving copy: the closure contract promises the walk's discovery order.
            visited = Collections.unmodifiableSet(new LinkedHashSet<>(visited));
        }
    }

    /**
     * {@link #getTransitiveDependencies} plus whether the bound cut the walk short — what a caller
     * needs to tell an exhausted closure from a truncated one.
     */
    public BoundedWalk getTransitiveDependenciesBounded(String urn, int maxDepth) {
        return transitive(urn, maxDepth, this::getDependencies);
    }

    private static Set<String> transitiveVisited(String urn, int maxDepth, java.util.function.Function<String, Set<String>> direct) {
        return transitive(urn, maxDepth, direct).visited();
    }

    private static BoundedWalk transitive(String urn, int maxDepth, java.util.function.Function<String, Set<String>> direct) {
        Set<String> visited = new LinkedHashSet<>();
        if (maxDepth <= 0) {
            return new BoundedWalk(Collections.unmodifiableSet(visited), !direct.apply(urn).isEmpty());
        }
        Set<String> frontier = new LinkedHashSet<>(direct.apply(urn)); // level 1
        Set<String> beyond = new LinkedHashSet<>();
        for (int level = 1; level <= maxDepth && !frontier.isEmpty(); level++) {
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (String node : frontier) {
                if (visited.add(node)) {
                    // The last level is visited but not expanded; collect its references so the
                    // bound can report what it left behind.
                    (level < maxDepth ? nextFrontier : beyond).addAll(direct.apply(node));
                }
            }
            frontier = nextFrontier;
        }
        beyond.removeAll(visited);
        return new BoundedWalk(Collections.unmodifiableSet(visited), !beyond.isEmpty());
    }

    /**
     * Remove a node and all its edges from the graph.
     *
     * <p>Cleans all four directions:
     * <ol>
     *   <li>Remove {@code urn} from the {@code dependencies} maps of schemas that depended on it.
     *   <li>Remove {@code urn}'s entry from the {@code dependents} map.
     *   <li>Remove {@code urn} from the {@code dependents} maps of schemas it depended on.
     *   <li>Remove {@code urn}'s entry from the {@code dependencies} map.
     * </ol>
     */
    public void remove(String urn) {
        String logical = UrnParser.logicalUrn(urn);

        graphLock.lock();
        try {
            // Removing an artifact drops every one of its version nodes, plus any edge that
            // targeted it by version, :latest, or the logical URN (all share the logical form).
            Set<String> nodes = new LinkedHashSet<>();
            dependencies.keySet().forEach(k -> { if (logical.equals(UrnParser.logicalUrn(k))) nodes.add(k); });
            dependents.keySet().forEach(k -> { if (logical.equals(UrnParser.logicalUrn(k))) nodes.add(k); });

            for (String node : nodes) {
                // Drop the node's forward edges and detach it from its targets' dependents.
                Set<String> outgoing = dependencies.remove(node);
                if (outgoing != null) {
                    outgoing.forEach(dep -> dependents.computeIfPresent(dep,
                        (k, v) -> { v.remove(node); return v.isEmpty() ? null : v; }));
                }
                // Drop the node from the reverse index and detach it from its dependents' edges.
                Set<String> incoming = dependents.remove(node);
                if (incoming != null) {
                    incoming.forEach(dependent -> dependencies.computeIfPresent(dependent, (k, v) -> {
                        // Copy-on-write: readers iterate these sets lock-free (BFS traversals);
                        // in-place mutation could throw under a concurrent delete.
                        Set<String> copy = new LinkedHashSet<>(v);
                        copy.remove(node);
                        return copy.isEmpty() ? null : copy;
                    }));
                }
            }
        } finally {
            graphLock.unlock();
        }
    }
}

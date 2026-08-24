package de.civitascore.modelforge.graph;

import de.civitascore.modelforge.core.port.ArtifactRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyGraphServiceTest {

    // Versioned URNs used in tests
    private static final String A_V = "urn:core:platform:civitas:element:common:Observation:ulhry9fjx6:1.0.0";
    private static final String B_V = "urn:core:platform:civitas:element:common:SensorReading:d28s38wfmi:1.0.0";
    private static final String C_V = "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0";
    private static final String D_V = "urn:core:platform:civitas:element:common:Address:enzw5n1szv:1.0.0";

    // The graph keys nodes by their versioned URN (no logical normalisation), so queries and
    // assertions use the versioned form — these alias the versioned constants for readability.
    private static final String A = A_V;
    private static final String B = B_V;
    private static final String C = C_V;
    private static final String D = D_V;

    private DependencyGraphService graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraphService(mock(ArtifactRegistry.class));
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_singleEdge_dependencyAndDependentRegistered() {
        graph.register(B_V, Set.of(C_V));

        assertThat(graph.getDependencies(B)).containsExactly(C);
        assertThat(graph.getDependents(C)).containsExactly(B);
    }

    @Test
    void register_keysNodesByVersionedUrn() {
        graph.register(B_V, Set.of(C_V));

        // The versioned URN is the node key; edges are kept verbatim (no logical normalisation).
        assertThat(graph.getDependencies(B_V)).containsExactly(C_V);
        assertThat(graph.getDependents(C_V)).containsExactly(B_V);
    }

    @Test
    void getDependencies_fallsBackToLogicalNode() {
        // CRIT-1 backstop: a node registered under the logical URN (no version) stays reachable
        // when a pinned-version query resolves to a versioned key that has no exact node. The
        // fallback only applies when the queried version actually exists in the registry.
        String logical = "urn:core:platform:civitas:element:common:SensorReading:d28s38wfmi";
        var registry = mock(ArtifactRegistry.class);
        when(registry.resolveReference(B_V)).thenReturn(java.util.Optional.of(B_V));
        var g = new DependencyGraphService(registry);
        g.register(logical, Set.of(C_V));

        assertThat(g.getDependencies(B_V)).containsExactly(C_V); // B_V = logical + ":1.0.0"
    }

    @Test
    void register_updatesEdges_removesOldEntries() {
        graph.register(B_V, Set.of(C_V));
        assertThat(graph.getDependents(C)).containsExactly(B);

        // Update B's dependencies — C is no longer referenced
        graph.register(B_V, Set.of(D_V));

        assertThat(graph.getDependencies(B)).containsExactly(D);
        assertThat(graph.getDependents(C)).isEmpty();
        assertThat(graph.getDependents(D)).containsExactly(B);
    }

    @Test
    void register_multipleDeps_allRegistered() {
        graph.register(A_V, Set.of(B_V, C_V));

        assertThat(graph.getDependencies(A)).containsExactlyInAnyOrder(B, C);
        assertThat(graph.getDependents(B)).containsExactly(A);
        assertThat(graph.getDependents(C)).containsExactly(A);
    }

    // ── getDependencies ───────────────────────────────────────────────────────

    @Test
    void getDependencies_unknownUrn_returnsEmpty() {
        assertThat(graph.getDependencies(A)).isEmpty();
    }

    @Test
    void getDependencies_noOutgoingEdges_returnsEmpty() {
        graph.register(C_V, Set.of());
        assertThat(graph.getDependencies(C)).isEmpty();
    }

    // ── getTransitiveDependencies ─────────────────────────────────────────────

    @Test
    void getTransitiveDependencies_singleHop_returnsDep() {
        graph.register(B_V, Set.of(C_V));

        assertThat(graph.getTransitiveDependencies(B, Integer.MAX_VALUE)).containsExactly(C);
    }

    @Test
    void getTransitiveDependencies_twoHop_returnsBothDeps() {
        // A → B → C
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(C_V));

        Set<String> trans = graph.getTransitiveDependencies(A, Integer.MAX_VALUE);
        assertThat(trans).containsExactlyInAnyOrder(B, C);
    }

    @Test
    void getTransitiveDependencies_diamond_deduplicates() {
        // A → B, A → C, B → D, C → D  — D appears once
        graph.register(A_V, Set.of(B_V, C_V));
        graph.register(B_V, Set.of(D_V));
        graph.register(C_V, Set.of(D_V));

        Set<String> trans = graph.getTransitiveDependencies(A, Integer.MAX_VALUE);
        assertThat(trans).containsExactlyInAnyOrder(B, C, D);
    }

    @Test
    void getTransitiveDependencies_cycle_terminatesWithoutInfiniteLoop() {
        // A → B → A  (cycle)
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(A_V));

        // Should terminate and return what was reachable before revisiting
        Set<String> trans = graph.getTransitiveDependencies(A, Integer.MAX_VALUE);
        assertThat(trans).containsExactlyInAnyOrder(B, A);
    }

    @Test
    void getTransitiveDependencies_leafNode_returnsEmpty() {
        graph.register(C_V, Set.of());
        assertThat(graph.getTransitiveDependencies(C, Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    void getTransitiveDependencies_depthBounded() {
        // A → B → C → D
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(C_V));
        graph.register(C_V, Set.of(D_V));

        assertThat(graph.getTransitiveDependencies(A, 0)).isEmpty();
        assertThat(graph.getTransitiveDependencies(A, 1)).containsExactly(B);
        assertThat(graph.getTransitiveDependencies(A, 2)).containsExactlyInAnyOrder(B, C);
        assertThat(graph.getTransitiveDependencies(A, 3)).containsExactlyInAnyOrder(B, C, D);
        assertThat(graph.getTransitiveDependencies(A, Integer.MAX_VALUE)).containsExactlyInAnyOrder(B, C, D);
    }

    @Test
    void getTransitiveDependencies_depthBounded_cycleSafe() {
        // A → B → A (cycle): bounded BFS must terminate
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(A_V));
        assertThat(graph.getTransitiveDependencies(A, 5)).containsExactlyInAnyOrder(A, B);
    }

    // ── getImpact ─────────────────────────────────────────────────────────────

    // ── hasAnyDependents ──────────────────────────────────────────────────────

    // ── remove ────────────────────────────────────────────────────────────────

    @Test
    void remove_versionedUrn_deletesAllEdges() {
        graph.register(B_V, Set.of(C_V));
        graph.remove(B_V);

        assertThat(graph.getDependents(C)).isEmpty();
        assertThat(graph.getDependencies(B)).isEmpty();
    }

    @Test
    void remove_cleansReverseIndex() {
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(C_V));

        graph.remove(B_V);

        assertThat(graph.getDependencies(B)).isEmpty();
        assertThat(graph.getDependents(B)).isEmpty();
        // A's forward edge to B is also cleaned up
        assertThat(graph.getDependencies(A)).isEmpty();
        assertThat(graph.getDependents(C)).isEmpty();
    }

    // ── toDisplayList ─────────────────────────────────────────────────────────

    // ── Thread safety ─────────────────────────────────────────────────────────

    @Test
    void concurrentRegisterAndRemove_graphRemainsConsistent() throws InterruptedException {
        // Concurrent register + remove on the same URN must leave the graph in a
        // consistent state: forward and reverse edges must agree.
        // Invariant: if C is in dependencies[B], then B must be in dependents[C].
        int iterations = 200;
        var executor = Executors.newFixedThreadPool(4);
        var latch = new CountDownLatch(iterations * 2);

        try {
            for (int i = 0; i < iterations; i++) {
                executor.submit(() -> {
                    try { graph.register(B_V, Set.of(C_V)); } finally { latch.countDown(); }
                });
                executor.submit(() -> {
                    try { graph.remove(B_V); } finally { latch.countDown(); }
                });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS))
                .as("all concurrent register/remove tasks completed in time")
                .isTrue();
        } finally {
            // Without the finally, a failed latch assertion leaks the pool's threads for the rest of
            // the JVM — the ring test below already does this.
            executor.shutdownNow();
        }

        // After all concurrent ops, forward and reverse edges must agree.
        Set<String> bDeps = graph.getDependencies(B);
        for (String dep : bDeps) {
            assertThat(graph.getDependents(dep))
                .as("if %s is in dependencies[B] then B must be in dependents[%s]", dep, dep)
                .contains(B);
        }
        // C must not list B as a dependent if B does not list C as a dependency
        if (!bDeps.contains(C)) {
            assertThat(graph.getDependents(C))
                .as("B must not be a stale entry in dependents[C] if C is not in dependencies[B]")
                .doesNotContain(B);
        }
    }

    // ── register: full cyclic graph (no acyclic subset) ────────────────────────
    // The registry persists the COMPLETE reference graph including cycles, so the
    // in-memory graph keeps every edge — the former Registry acyclic-subset derivation
    // (registerAndAcyclicSubset) is gone.

    @Test
    void register_twoCycle_keepsBothDirections() {
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(A_V));
        assertThat(graph.getDependencies(A)).containsExactly(B);
        assertThat(graph.getDependencies(B)).containsExactly(A);
        assertThat(graph.getTransitiveDependencies(A, Integer.MAX_VALUE)).containsExactlyInAnyOrder(A, B);
    }

    @Test
    void register_selfReference_retained() {
        graph.register(A_V, Set.of(A_V));
        assertThat(graph.getDependencies(A)).containsExactly(A);
    }

    @Test
    void register_threeCycle_fullyRetained() {
        // A → B → C → A : every edge is kept; nothing is dropped to break the cycle.
        graph.register(A_V, Set.of(B_V));
        graph.register(B_V, Set.of(C_V));
        graph.register(C_V, Set.of(A_V));
        assertThat(graph.getDependencies(C)).containsExactly(A);
        assertThat(graph.getTransitiveDependencies(A, Integer.MAX_VALUE)).containsExactlyInAnyOrder(A, B, C);
    }

    /**
     * Concurrency guard: registration runs as one atomic step under {@code graphLock}.
     * Hammer {@link DependencyGraphService#register} from many threads forming a ring
     * (each node references the next) and assert that (a) it never deadlocks or throws and
     * (b) the full ring is retained — cycles are kept, not broken.
     */
    @Test
    void register_concurrentRing_consistentAndNoDeadlock() throws InterruptedException {
        int n = 16;
        String[] urns = new String[n];
        for (int i = 0; i < n; i++) {
            urns[i] = "urn:core:platform:civitas:element:common:Node:hf6brenbic" + i + ":1.0.0";
        }

        var executor = Executors.newFixedThreadPool(n);
        var ready = new CountDownLatch(n);
        var go = new CountDownLatch(1);
        var failures = new java.util.concurrent.atomic.AtomicInteger();

        try {
            for (int i = 0; i < n; i++) {
                final int idx = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        // Node i → Node (i+1) % n : a single ring, registered concurrently.
                        graph.register(urns[idx], Set.of(urns[(idx + 1) % n]));
                    } catch (Throwable t) {
                        failures.incrementAndGet();
                    }
                });
            }
            ready.await();
            go.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                .as("no deadlock — all registrations complete").isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures.get()).as("no exceptions in any thread").isZero();
        // Every edge of the ring is retained — the full cycle survives (getDependencies
        // returns the verbatim versioned URNs).
        for (int i = 0; i < n; i++) {
            assertThat(graph.getDependencies(urns[i]))
                .containsExactly("urn:core:platform:civitas:element:common:Node:hf6brenbic" + ((i + 1) % n) + ":1.0.0");
        }
    }

    // ── registerFromRegistry / rebuild across artifact types ───────────────────
    // The durable artifact_reference table holds per-type edges (Mapping source/target,
    // Pipeline nodes, DataStructure/DataSet *Refs). The graph mirrors those rows for EVERY
    // artifact type — not just Elements — so all kinds are navigable.

    private static final String MAPPING_V  = "urn:core:platform:civitas:mapping:common:m1:mdqlihwds3:1.0.0";
    private static final String MAPPING_L  = "urn:core:platform:civitas:mapping:common:m1:mdqlihwds3";
    private static final String DS_V       = "urn:core:platform:civitas:datastructure:common:d1:vbrfl8zinc:1.0.0";
    private static final String DS_L       = "urn:core:platform:civitas:datastructure:common:d1:vbrfl8zinc";

    @Test
    void registerFromRegistry_indexesAMappingsSourceAndTargetEdges() {
        ArtifactRegistry registry = mock(ArtifactRegistry.class);
        // resolveReference(logical) → current versioned URN; fetchArtifactRefUrns → the durable
        // per-type edges the persistence layer extracted from the mapping's source/target.
        when(registry.resolveReference(MAPPING_L)).thenReturn(Optional.of(MAPPING_V));
        when(registry.fetchArtifactRefUrns(MAPPING_V)).thenReturn(List.of(A_V, B_V));
        var g = new DependencyGraphService(registry);

        g.registerFromRegistry(MAPPING_V);

        assertThat(g.getDependencies(MAPPING_V)).containsExactlyInAnyOrder(A_V, B_V);
        assertThat(g.getDependents(A_V)).containsExactly(MAPPING_V);
        assertThat(g.getDependents(B_V)).containsExactly(MAPPING_V);
    }

    @Test
    void rebuild_indexesEveryArtifactTypeFromDurableReferences() {
        ArtifactRegistry registry = mock(ArtifactRegistry.class);
        when(registry.listAllUrns()).thenReturn(List.of(MAPPING_L, DS_L));
        when(registry.resolveReference(MAPPING_L)).thenReturn(Optional.of(MAPPING_V));
        when(registry.resolveReference(DS_L)).thenReturn(Optional.of(DS_V));
        when(registry.fetchArtifactRefUrns(MAPPING_V)).thenReturn(List.of(A_V));   // mapping → element A
        when(registry.fetchArtifactRefUrns(DS_V)).thenReturn(List.of(A_V, B_V));   // datastructure → A, B
        var g = new DependencyGraphService(registry);

        g.rebuild();

        assertThat(g.getDependencies(MAPPING_V)).containsExactly(A_V);
        assertThat(g.getDependencies(DS_V)).containsExactlyInAnyOrder(A_V, B_V);
        // A is now depended on by both the mapping and the datastructure — cross-type dependents.
        assertThat(g.getDependents(A_V)).containsExactlyInAnyOrder(MAPPING_V, DS_V);
        assertThat(g.getDependents(B_V)).containsExactly(DS_V);
    }

    @Test
    void registerFromRegistry_noRegistry_isNoOp() {
        var g = new DependencyGraphService(mock(ArtifactRegistry.class));
        g.registerFromRegistry(MAPPING_V);
        assertThat(g.getDependencies(MAPPING_V)).isEmpty();
    }

    // ── toDisplayList ─────────────────────────────────────────────────────────
}

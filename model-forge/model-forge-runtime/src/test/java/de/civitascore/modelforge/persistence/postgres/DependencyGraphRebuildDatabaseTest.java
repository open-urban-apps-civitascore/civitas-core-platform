package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rebuilt graph answers for a superseded version, not only for the current one.
 *
 * <p>A fresh {@link DependencyGraphService} over the same database is what a restart produces, so
 * these tests assert on what the rebuild reads rather than on what a write left in the map.
 */
class DependencyGraphRebuildDatabaseTest extends AbstractRegistryDatabaseTest {

    private String leafPin;
    private String readingLogical;
    private String readingV1;
    private String readingV2;
    private String stationPin;

    @BeforeEach
    void storeAStationPinningASupersededReading() {
        leafPin = registry.storeElement("Leaf", objectSchema(), Set.of());
        readingV1 = registry.storeElement("Reading", composing(leafPin), Set.of(leafPin));
        readingLogical = UrnParser.logicalUrn(readingV1);
        // Written under the same $id, so this is a second version and not a second artifact — the
        // registry mints a fresh URN for a schema that carries none.
        readingV2 = registry.storeElement("Reading", identified(readingLogical), Set.of());
        stationPin = registry.storeElement("Station", composing(readingV1), Set.of(readingV1));
    }

    @Test
    @DisplayName("The fixture really holds two versions of one artifact")
    void theFixtureHoldsTwoVersionsOfOneArtifact() {
        // Two artifacts instead of two versions would leave version 1 current, and every test
        // below would pass without the rebuild reading a superseded version at all.
        assertThat(UrnParser.logicalUrn(readingV2)).isEqualTo(readingLogical);
        assertThat(versionsOf(readingLogical)).hasSize(2);
        assertThat(currentVersionOf(readingLogical)).isEqualTo(UrnParser.versionFromUrn(readingV2));
    }

    @Test
    @DisplayName("A rebuilt graph keeps the edges a superseded version declares")
    void rebuiltGraphKeepsTheEdgesOfASupersededVersion() {
        var graph = new DependencyGraphService(registry);
        graph.rebuild();

        assertThat(graph.getDependencies(readingV1)).containsExactly(leafPin);
        assertThat(graph.getDependencies(readingV2))
            .as("version 2 declares nothing and must not inherit version 1's edges")
            .isEmpty();
    }

    @Test
    @DisplayName("A walk through a pinned superseded version still reaches what lies below it")
    void walkThroughAPinnedSupersededVersionReachesTheLeaf() {
        var graph = new DependencyGraphService(registry);
        graph.rebuild();

        assertThat(graph.getTransitiveDependencies(stationPin, Integer.MAX_VALUE))
            .as("an empty answer at the pinned version reads as a leaf, leaving the rest unexamined")
            .contains(readingV1, leafPin);
    }

    @Test
    @DisplayName("A restart does not change the answer the graph gives")
    void restartDoesNotChangeTheAnswer() {
        // The nodes the element write path publishes, as ElementCommandService does per store.
        var graph = new DependencyGraphService(registry);
        graph.register(leafPin, Set.of());
        graph.register(readingV1, Set.of(leafPin));
        graph.register(readingV2, Set.of());
        graph.register(stationPin, Set.of(readingV1));
        var before = graph.getTransitiveDependencies(stationPin, Integer.MAX_VALUE);

        graph.rebuild();

        assertThat(graph.getTransitiveDependencies(stationPin, Integer.MAX_VALUE)).isEqualTo(before);
    }

    @Test
    @DisplayName("The bulk read agrees with the per-version read, version for version")
    void bulkReadAgreesWithThePerVersionRead() {
        Map<String, List<String>> bulk = registry.referenceEdgesByVersion();

        // Only the two versions that hold references, and each with what the single-version read
        // returns for it — same targets, same order.
        assertThat(bulk).containsOnlyKeys(readingV1, stationPin);
        bulk.forEach((versionedUrn, targets) ->
            assertThat(targets).isEqualTo(registry.fetchArtifactRefUrns(versionedUrn)));
    }

    @Test
    @DisplayName("A write onto an older version line registers the version it wrote")
    void writeOntoAnOlderLineRegistersTheVersionItWrote() {
        // What editing the model of an older DataStructureVersion does: the bump counts from that
        // version's own number, so it stays inside its major and never becomes current.
        String v1 = registry.storeElement("Holder", composing(leafPin), Set.of(leafPin));
        String logical = UrnParser.logicalUrn(v1);
        registry.storeElement("Holder", identified(logical), Set.of(), VersionBump.MAJOR);
        // Every write carries the same $id, or the registry mints a separate artifact instead of
        // adding a version — and a separate artifact is current on its own and proves nothing.
        String revised = registry.storeElement(
            "Holder", identifiedComposing(logical, leafPin), Set.of(leafPin), Set.of(),
            null, VersionBump.MINOR, UrnParser.versionFromUrn(v1));

        assertThat(UrnParser.logicalUrn(revised)).isEqualTo(logical);
        assertThat(currentVersionOf(logical))
            .as("the revision stays on the older line, so it must not become current")
            .isNotEqualTo(UrnParser.versionFromUrn(revised));

        var graph = new DependencyGraphService(registry);
        graph.registerFromRegistry(revised);

        assertThat(graph.getDependencies(revised))
            .as("resolving to the current version would index a version the write did not touch")
            .containsExactly(leafPin);
    }

    @Test
    @DisplayName("A version's edges come back in sort_order, not in row or URN order")
    void edgesOfAVersionComeBackInSortOrder() {
        List<String> alphabetical = Stream.of(
                registry.storeElement("PartA", objectSchema(), Set.of()),
                registry.storeElement("PartB", objectSchema(), Set.of()),
                registry.storeElement("PartC", objectSchema(), Set.of()))
            .sorted()
            .toList();
        String holder = registry.storeElement(
            "Holder", objectSchema(), new LinkedHashSet<>(alphabetical));

        // The write path numbers sort_order by insertion, so stored order, row order and URN order
        // all agree. Renumber to a rotation to tell them apart. The renumber runs in URN order,
        // not in the new order: an update rewrites the row at the end of the table, so renumbering
        // in the expected order would make row order agree with sort_order again.
        List<String> expected = List.of(alphabetical.get(2), alphabetical.get(0), alphabetical.get(1));
        alphabetical.forEach(target -> setSortOrder(holder, target, expected.indexOf(target)));

        assertThat(registry.referenceEdgesByVersion().get(holder)).isEqualTo(expected);
    }

    /** Renumbers one stored reference, so sort_order stops matching the order rows were written. */
    private void setSortOrder(String fromVersionedUrn, String targetUrn, int sortOrder) {
        jdbc.sql("""
                update model_forge.artifact_reference set sort_order = :sort
                 where target_urn = :target
                   and from_version_id = (select av.id
                                            from model_forge.artifact_version av
                                            join model_forge.artifact a on a.id = av.artifact_id
                                           where a.logical_urn = :logical and av.version = :version)
                """)
            .param("sort", sortOrder)
            .param("target", targetUrn)
            .param("logical", UrnParser.logicalUrn(fromVersionedUrn))
            .param("version", UrnParser.versionFromUrn(fromVersionedUrn))
            .update();
    }

    /** The current version recorded for a logical artifact. */
    private String currentVersionOf(String logicalUrn) {
        return jdbc.sql("select current_version from model_forge.artifact where logical_urn = :urn")
            .param("urn", logicalUrn)
            .query(String.class)
            .single();
    }

    private JsonNode objectSchema() {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "name": { "type": "string" } }
            }
            """);
    }

    /** An object schema written under an existing identity, so the write adds a version to it. */
    private JsonNode identified(String logicalUrn) {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "%s",
              "type": "object",
              "properties": { "label": { "type": "string" } }
            }
            """.formatted(logicalUrn));
    }

    /** {@link #composing} under an existing identity, so the write adds a version to it. */
    private JsonNode identifiedComposing(String logicalUrn, String referencedPin) {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "%s",
              "type": "object",
              "properties": { "part": { "$ref": "%s" } },
              "required": ["part"]
            }
            """.formatted(logicalUrn, referencedPin));
    }

    private JsonNode composing(String referencedPin) {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "part": { "$ref": "%s" } },
              "required": ["part"]
            }
            """.formatted(referencedPin));
    }
}

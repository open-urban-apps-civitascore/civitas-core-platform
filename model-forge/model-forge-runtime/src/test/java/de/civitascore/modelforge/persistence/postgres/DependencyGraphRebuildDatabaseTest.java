package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

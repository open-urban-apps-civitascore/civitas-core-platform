package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The registry's identity and versioning contract against a real database: one logical artifact per
 * logical URN, a new immutable version per content change, earlier versions preserved verbatim, and
 * an identical rewrite absorbed.
 */
class ArtifactVersioningDatabaseTest extends AbstractRegistryDatabaseTest {

    @Test
    @DisplayName("Reusing the returned logical URN updates that artifact instead of creating a second one")
    void reusingLogicalUrnUpdatesTheSameArtifact() {
        String firstPin = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);

        String secondPin = storeUnder(logical, "humidity", VersionBump.MINOR);

        assertThat(rows("artifact"))
            .as("a second write under the same logical URN must not mint a second logical artifact")
            .hasSize(1);
        assertThat(UrnParser.logicalUrn(secondPin)).isEqualTo(logical);
        assertThat(versionsOf(logical)).containsExactly("1.0.0", "1.1.0");
    }

    @Test
    @DisplayName("A content change adds a version and leaves the earlier version's stored bytes untouched")
    void contentChangeDoesNotOverwriteTheEarlierVersion() {
        String firstPin = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);
        String originalBytes = storedContent(logical, "1.0.0");

        storeUnder(logical, "humidity", VersionBump.MINOR);

        assertThat(storedContent(logical, "1.0.0"))
            .as("the earlier version's representation must be immutable")
            .isEqualTo(originalBytes);
        assertThat(storedContent(logical, "1.1.0")).contains("humidity").doesNotContain("temperature");
    }

    @Test
    @DisplayName("An earlier version stays readable by its pin after a later version is written")
    void earlierVersionRemainsReadableAfterALaterWrite() {
        String firstPin = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);

        storeUnder(logical, "humidity", VersionBump.MAJOR);

        JsonNode earlier = registry.fetch(firstPin).orElseThrow();
        assertThat(earlier.path("properties").has("temperature")).isTrue();
        assertThat(earlier.path("properties").has("humidity")).isFalse();
    }

    @Test
    @DisplayName("Re-storing byte-identical content returns the existing pin and adds no version")
    void identicalContentIsAbsorbed() {
        String firstPin = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);
        // Idempotence is keyed on the stored bytes, so the identity has to be carried for the
        // rewrite to be identical: a store without an $id mints a fresh UUID instead.
        String carriedPin = storeUnder(logical, "temperature", VersionBump.MINOR);

        String rewritePin = storeUnder(logical, "temperature", VersionBump.MINOR);

        assertThat(rewritePin).as("an identical rewrite must resolve to the existing version").isEqualTo(carriedPin);
        assertThat(versionsOf(logical)).containsExactly("1.0.0", "1.1.0");
        assertThat(rows("artifact_representation")).hasSize(2);
    }

    @Test
    @DisplayName("A store that carries no identity mints a new logical artifact rather than revising one")
    void nameOnlyStoreMintsAFreshIdentity() {
        String firstPin = registry.storeElement(schema(null, "temperature"), Set.of());
        String secondPin = registry.storeElement(schema(null, "temperature"), Set.of());

        assertThat(UrnParser.logicalUrn(secondPin)).isNotEqualTo(UrnParser.logicalUrn(firstPin));
        assertThat(rows("artifact")).hasSize(2);
    }

    @Test
    @DisplayName("The stored representation is the authored document, with no identity stamped into it")
    void storedRepresentationKeepsTheAuthoredDocument() {
        String pin = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(pin);

        // The logical URN lives in the artifact row, not in the document, which is what leaves the
        // registry free to assign a version.
        assertThat(storedContent(logical, "1.0.0")).doesNotContain("$id");
        assertThat(jdbc.sql("select logical_urn from model_forge.artifact").query(String.class).single())
            .isEqualTo(logical);
    }

    @Test
    @DisplayName("Bumping a pinned older version copies its content and numbers the copy from the highest version")
    void bumpOfAPinnedOlderVersionCopiesItsContentAboveTheHighest() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);
        String firstBytes = storedContent(logical, "1.0.0");
        String secondBytes = storedContent(logical, "2.0.0");

        String copyPin = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");

        assertThat(copyPin).isEqualTo(UrnParser.withVersion(logical, "3.0.0"));
        assertThat(storedContent(logical, "3.0.0")).isEqualTo(firstBytes);
        assertThat(currentVersionOf(logical)).isEqualTo("3.0.0");
        assertThat(storedContent(logical, "1.0.0")).isEqualTo(firstBytes);
        assertThat(storedContent(logical, "2.0.0")).isEqualTo(secondBytes);
    }

    @Test
    @DisplayName("A copy of a pinned version carries that version's reference edges, not the current version's")
    void bumpOfAPinnedVersionCopiesItsOwnReferences() {
        String stationV1 = registry.storeElement(schema(null, "stationId"), Set.of());
        String station = UrnParser.logicalUrn(stationV1);
        String stationV2 = storeUnder(station, "stationCode", VersionBump.MINOR);
        String structure = urns.urn("datastructure", UUID.randomUUID());
        String structureV1 = registry.storeDataStructure(structure, structureOf(stationV1), VersionBump.PATCH, null);
        registry.storeDataStructure(structure, structureOf(stationV2), VersionBump.MAJOR, null);

        String copyPin = registry.bumpVersion(structure, VersionBump.MAJOR, "1.0.0");

        assertThat(registry.fetchArtifactRefUrns(copyPin))
            .containsExactlyElementsOf(registry.fetchArtifactRefUrns(structureV1))
            .containsExactly(stationV1);
    }

    @Test
    @DisplayName("Bumping the same pinned version twice mints a new highest version each time")
    void repeatedBumpOfTheSameVersionMintsANewVersionEachTime() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);

        String firstCopy = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");
        String secondCopy = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");

        assertThat(UrnParser.versionFromUrn(firstCopy)).isEqualTo("3.0.0");
        assertThat(UrnParser.versionFromUrn(secondCopy)).isEqualTo("4.0.0");
    }

    @Test
    @DisplayName("A major bump counts from the highest version, not from the major line of the source")
    void majorBumpCountsFromTheHighestVersion() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        registry.storeElement(schema(logical, "humidity"), Set.of(), Set.of(), "2.3.0", VersionBump.PATCH, null);

        String copyPin = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");

        assertThat(UrnParser.versionFromUrn(copyPin)).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("A minor bump of an older pinned version lands in the highest line")
    void minorBumpOfAnOlderPinLandsInTheHighestLine() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        registry.storeElement(schema(logical, "humidity"), Set.of(), Set.of(), "2.3.0", VersionBump.PATCH, null);

        String copyPin = registry.bumpVersion(logical, VersionBump.MINOR, "1.0.0");

        assertThat(UrnParser.versionFromUrn(copyPin)).isEqualTo("2.4.0");
        assertThat(storedContent(logical, "2.4.0")).isEqualTo(storedContent(logical, "1.0.0"));
    }

    @Test
    @DisplayName("Bumping a pinned version the artifact does not hold is refused and writes nothing")
    void bumpOfAnUnknownPinnedVersionIsRefused() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        var before = snapshot();

        assertThatThrownBy(() -> registry.bumpVersion(logical, VersionBump.MAJOR, "9.0.0"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void copyingAnOlderRootCopiesItsGroupingAndRepinsOnlyThatRoot() {
        String member = registry.storeElement(schema(null, "child"), Set.of());
        String original = registry.storeElement(schema(null, "temperature"), Set.of(member));
        String logical = UrnParser.logicalUrn(original);
        String grouping = UrnParser.withArtifactType(logical, "datastructure");
        JsonNode manifest = structureOf(original);
        ((ObjectNode) manifest).put("$ref", original);
        ((ObjectNode) manifest.path("$defs"))
            .putObject("Child").put("$ref", member);
        String originalGrouping = registry.storeDataStructure(grouping, manifest);
        ArtifactVersionRepository versionRows = new ArtifactVersionRepository(jdbc);
        UUID rootId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(logical)
            .orElseThrow().id(), "1.0.0").orElseThrow().id();
        // A secondary format and an edge not written in the schema must survive the copy.
        new ArtifactRepresentationRepository(jdbc).insert(new ArtifactRepresentationRow(
            UUID.randomUUID(), rootId, "example", "text/plain", null, "original bytes",
            "example-hash", "stored", Instant.now()));
        UUID groupId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(grouping)
            .orElseThrow().id(), "1.0.0").orElseThrow().id();
        ArtifactReferenceRepository edges = new ArtifactReferenceRepository(jdbc);
        List<ReferenceRow> originalEdges = new ArrayList<>(edges.rowsForVersion(groupId));
        originalEdges.add(new ReferenceRow(member, "schema-ref", "extra", 7));
        edges.replaceForVersion(groupId, originalEdges);
        String rootBytes = storedJson(logical, "1.0.0");
        String groupBytes = storedContent(grouping, "1.0.0");
        String later = storeUnder(logical, "humidity", VersionBump.MAJOR);
        registry.storeDataStructure(grouping, structureOf(later), VersionBump.MAJOR);

        String first = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");
        String second = registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0");
        String third = registry.bumpVersion(logical, VersionBump.MAJOR, "3.0.0");

        assertThat(first).endsWith(":3.0.0");
        assertThat(second).endsWith(":4.0.0");
        assertThat(third).endsWith(":5.0.0");
        for (String copy : List.of(first, second, third)) {
            String version = UrnParser.versionFromUrn(copy);
            String groupCopy = UrnParser.withVersion(grouping, version);
            assertThat(storedJson(logical, version)).isEqualTo(rootBytes);
            assertThat(registry.fetchArtifactRefUrns(copy)).containsExactly(member);
            assertThat(registry.fetch(groupCopy).orElseThrow().path("$ref").asText()).isEqualTo(copy);
            assertThat(registry.fetch(groupCopy).orElseThrow().path("$defs").path("Station")
                .path("$ref").asText()).isEqualTo(copy);
            assertThat(registry.fetch(groupCopy).orElseThrow().path("$defs").path("Child")
                .path("$ref").asText()).isEqualTo(member);
            UUID copyId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(logical)
                .orElseThrow().id(), version).orElseThrow().id();
            assertThat(new ArtifactRepresentationRepository(jdbc).find(copyId, "example")
                .orElseThrow().contentText()).isEqualTo("original bytes");
            UUID groupingId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(grouping)
                .orElseThrow().id(), version).orElseThrow().id();
            assertThat(edges.rowsForVersion(groupingId)).containsExactlyElementsOf(originalEdges.stream()
                .map(r -> original.equals(r.targetUrn())
                    ? new ReferenceRow(copy, r.referenceType(), r.referenceName(), r.sortOrder()) : r)
                .toList());
        }
        assertThat(storedJson(logical, "1.0.0")).isEqualTo(rootBytes);
        assertThat(storedContent(grouping, "1.0.0")).isEqualTo(groupBytes);
        assertThat(registry.fetchArtifactRefUrns(originalGrouping)).contains(original, member);
        assertThat(currentVersionOf(grouping)).isEqualTo("5.0.0");
    }

    @Test
    void groupingConflictRollsBackTheRootCopyAndAllItsEdges() {
        String root = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(root);
        String grouping = UrnParser.withArtifactType(logical, "datastructure");
        registry.storeDataStructure(grouping, structureOf(root));
        registry.storeDataStructure(grouping, structureOf("%s:latest".formatted(logical)), VersionBump.MAJOR);
        var before = snapshot();

        assertThatThrownBy(() -> registry.bumpVersion(logical, VersionBump.MAJOR, "1.0.0"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("conflicts");

        assertThat(snapshot()).isEqualTo(before);
    }

    private String storedJson(String logical, String version) {
        UUID artifact = new ArtifactRepository(jdbc).findByLogicalUrn(logical).orElseThrow().id();
        UUID id = new ArtifactVersionRepository(jdbc).find(artifact, version).orElseThrow().id();
        return new ArtifactRepresentationRepository(jdbc).find(id, RegistryMapping.FORMAT_JSONSCHEMA)
            .orElseThrow().contentJson();
    }

    /** Writes under an existing logical URN by carrying it as the schema's {@code $id}. */
    private String storeUnder(String logicalUrn, String property, VersionBump bump) {
        return registry.storeElement(schema(logicalUrn, property), Set.of(), Set.of(), null, bump, null);
    }

    private String currentVersionOf(String logicalUrn) {
        return jdbc.sql("select current_version from model_forge.artifact where logical_urn = :urn")
            .param("urn", logicalUrn)
            .query(String.class)
            .single();
    }

    /** A DataStructure grouping a single pinned Element member. */
    private JsonNode structureOf(String memberPin) {
        return mapper.readTree("""
            { "title": "Station structure", "$defs": { "Station": { "$ref": "%s" } } }
            """.formatted(memberPin));
    }

    /** A minimal JSON Schema, optionally claiming an existing logical identity via {@code $id}. */
    private JsonNode schema(String id, String property) {
        String idLine = id == null ? "" : "\"$id\": \"" + id + "\",";
        return mapper.readTree("""
            {
              %s
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "%s": { "type": "number" } }
            }
            """.formatted(idLine, property));
    }
}

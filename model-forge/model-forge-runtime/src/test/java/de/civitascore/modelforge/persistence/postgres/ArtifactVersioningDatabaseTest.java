package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.application.ElementCommandService;
import de.civitascore.modelforge.application.ElementQueryService;
import de.civitascore.modelforge.application.EmbeddedModelForgeOperations;
import de.civitascore.modelforge.application.ReferenceExistenceValidator;
import de.civitascore.modelforge.application.SchemaImportService;
import de.civitascore.modelforge.application.SmartDataModelsService;
import de.civitascore.modelforge.application.ViewService;
import de.civitascore.modelforge.application.XRepositoryService;
import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.CopyVersionCommand;
import de.civitascore.modelforge.contract.RegistryConflictException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

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
    @DisplayName("Copying an older version copies its content and numbers the copy above the highest version")
    void copyOfAnOlderVersionCopiesItsContentAboveTheHighest() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);
        String firstBytes = storedContent(logical, "1.0.0");
        String secondBytes = storedContent(logical, "2.0.0");

        String copyPin = registry.copyVersion(UrnParser.withVersion(logical, "1.0.0"), null, Map.of());

        assertThat(copyPin).isEqualTo(UrnParser.withVersion(logical, "3.0.0"));
        assertThat(storedContent(logical, "3.0.0")).isEqualTo(firstBytes);
        assertThat(currentVersionOf(logical)).isEqualTo("3.0.0");
        assertThat(storedContent(logical, "1.0.0")).isEqualTo(firstBytes);
        assertThat(storedContent(logical, "2.0.0")).isEqualTo(secondBytes);
    }

    @Test
    @DisplayName("A copy of a version carries that version's References, not the current version's")
    void copyOfAVersionCopiesItsOwnReferences() {
        String stationV1 = registry.storeElement(schema(null, "stationId"), Set.of());
        String station = UrnParser.logicalUrn(stationV1);
        String stationV2 = storeUnder(station, "stationCode", VersionBump.MINOR);
        String structure = urns.urn("datastructure", UUID.randomUUID());
        String structureV1 = registry.storeDataStructure(structure, structureOf(stationV1), VersionBump.PATCH, null);
        registry.storeDataStructure(structure, structureOf(stationV2), VersionBump.MAJOR, null);

        String copyPin = registry.copyVersion(structureV1, null, Map.of());

        assertThat(registry.fetchArtifactRefUrns(copyPin))
            .containsExactlyElementsOf(registry.fetchArtifactRefUrns(structureV1))
            .containsExactly(stationV1);
    }

    @Test
    @DisplayName("Copying the same version twice mints a new highest version each time")
    void repeatedCopyOfTheSameVersionMintsANewVersionEachTime() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);

        String firstCopy = registry.copyVersion(UrnParser.withVersion(logical, "1.0.0"), null, Map.of());
        String secondCopy = registry.copyVersion(UrnParser.withVersion(logical, "1.0.0"), null, Map.of());

        assertThat(UrnParser.versionFromUrn(firstCopy)).isEqualTo("3.0.0");
        assertThat(UrnParser.versionFromUrn(secondCopy)).isEqualTo("4.0.0");
    }

    @Test
    @DisplayName("A copy counts from the highest version, not from the major line of the source")
    void copyCountsFromTheHighestVersion() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        registry.storeElement(schema(logical, "humidity"), Set.of(), Set.of(), "2.3.0", VersionBump.PATCH, null);

        String copyPin = registry.copyVersion(UrnParser.withVersion(logical, "1.0.0"), null, Map.of());

        assertThat(UrnParser.versionFromUrn(copyPin)).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("A copy counts above the given version when that is higher than the artifact's highest")
    void copyCountsAboveTheGivenVersionWhenItIsHigher() {
        String pin = registry.storeElement(schema(null, "temperature"), Set.of());

        String copyPin = registry.copyVersion(pin, "4.2.0", Map.of());

        assertThat(UrnParser.versionFromUrn(copyPin)).isEqualTo("5.0.0");
        assertThat(currentVersionOf(UrnParser.logicalUrn(pin))).isEqualTo("5.0.0");
    }

    @Test
    @DisplayName("Copying a version the artifact does not hold is refused and writes nothing")
    void copyOfAnUnknownVersionIsRefused() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        var before = snapshot();

        assertThatThrownBy(() -> registry.copyVersion(UrnParser.withVersion(logical, "9.0.0"), null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Copying an older root Element copies the grouping version that holds it and retargets only that root")
    void copyingAnOlderRootCopiesItsGroupingAndRetargetsOnlyThatRoot() {
        String member = registry.storeElement(schema(null, "child"), Set.of());
        String original = registry.storeElement(schema(null, "temperature"), Set.of(member));
        String logical = UrnParser.logicalUrn(original);
        String grouping = groupingOf(logical);
        JsonNode manifest = structureOf(original);
        ((ObjectNode) manifest).put("$ref", original);
        ((ObjectNode) manifest.path("$defs"))
            .putObject("Child").put("$ref", member);
        String originalGrouping = registry.storeDataStructure(grouping, manifest);
        ArtifactVersionRepository versionRows = new ArtifactVersionRepository(jdbc);
        UUID rootId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(logical)
            .orElseThrow().id(), "1.0.0").orElseThrow().id();
        // A secondary format and a Reference not written in the schema must survive the copy.
        new ArtifactRepresentationRepository(jdbc).insert(new ArtifactRepresentationRow(
            UUID.randomUUID(), rootId, "example", "text/plain", null, "original bytes",
            "example-hash", "stored", Instant.now()));
        UUID groupId = versionRows.find(new ArtifactRepository(jdbc).findByLogicalUrn(grouping)
            .orElseThrow().id(), "1.0.0").orElseThrow().id();
        ArtifactReferenceRepository references = new ArtifactReferenceRepository(jdbc);
        List<ReferenceRow> originalReferences = new ArrayList<>(references.rowsForVersion(groupId));
        originalReferences.add(new ReferenceRow(member, "schema-ref", "extra", 7));
        references.replaceForVersion(groupId, originalReferences);
        String rootBytes = storedJson(logical, "1.0.0");
        String groupBytes = storedContent(grouping, "1.0.0");
        String later = storeUnder(logical, "humidity", VersionBump.MAJOR);
        registry.storeDataStructure(grouping, structureOf(later), VersionBump.MAJOR);
        ModelForge modelForge = modelForge();

        String first = copy(modelForge, UrnParser.withVersion(logical, "1.0.0"));
        String second = copy(modelForge, UrnParser.withVersion(logical, "1.0.0"));
        String third = copy(modelForge, UrnParser.withVersion(logical, "3.0.0"));

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
            assertThat(references.rowsForVersion(groupingId)).containsExactlyElementsOf(originalReferences.stream()
                .map(r -> original.equals(r.targetUrn())
                    ? new ReferenceRow(copy, r.referenceType(), r.referenceName(), r.sortOrder()) : r)
                .toList());
        }
        assertThat(storedJson(logical, "1.0.0")).isEqualTo(rootBytes);
        assertThat(storedContent(grouping, "1.0.0")).isEqualTo(groupBytes);
        assertThat(registry.fetchArtifactRefUrns(originalGrouping)).contains(original, member);
        assertThat(currentVersionOf(logical)).isEqualTo("5.0.0");
        assertThat(currentVersionOf(grouping)).isEqualTo("5.0.0");
    }

    @Test
    @DisplayName("A root whose grouping has a higher version is copied with the grouping above that version")
    void rootCopyCountsAboveAGroupingThatIsAhead() {
        String root = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(root);
        String grouping = groupingOf(logical);
        JsonNode imported = structureOf(root);
        ((ObjectNode) imported).put("$ref", root);
        registry.storeDataStructure(grouping, imported);
        JsonNode folded = structureOf(root);
        ((ObjectNode) folded).put("title", "Station structure, edited");
        registry.storeDataStructure(grouping, folded, VersionBump.MAJOR);

        String copy = copy(modelForge(), root);

        String copiedGrouping = UrnParser.withVersion(grouping, "3.0.0");
        assertThat(copy).isEqualTo(UrnParser.withVersion(logical, "3.0.0"));
        assertThat(registry.fetch(copiedGrouping).orElseThrow().path("$ref").asText()).isEqualTo(copy);
        assertThat(currentVersionOf(logical)).isEqualTo("3.0.0");
        assertThat(currentVersionOf(grouping)).isEqualTo("3.0.0");
    }

    @Test
    @DisplayName("A conflicting grouping version fails the whole copy and writes nothing")
    void groupingConflictRollsBackTheRootCopyAndAllItsReferences() {
        String root = registry.storeElement(schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(root);
        String grouping = groupingOf(logical);
        registry.storeDataStructure(grouping, structureOf(root));
        // No write path leaves a version above current_version, so the conflict is planted directly.
        new ArtifactVersionRepository(jdbc).insert(new ArtifactVersionRow(UUID.randomUUID(),
            new ArtifactRepository(jdbc).findByLogicalUrn(grouping).orElseThrow().id(), "2.0.0",
            RegistryMapping.FORMAT_CORE_JSON, null, null, Instant.now(), null));
        var before = snapshot();
        ModelForge modelForge = modelForge();

        assertThatThrownBy(() -> copy(modelForge, root))
            .isInstanceOf(RegistryConflictException.class)
            .hasMessageContaining("conflicts");

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("A copy rolled back by its host transaction leaves no rows and no graph nodes")
    void rolledBackCopyLeavesNoRowsAndNoGraphNodes() {
        String root = registry.storeElement(schema(null, "temperature"), Set.of());
        String grouping = groupingOf(UrnParser.logicalUrn(root));
        registry.storeDataStructure(grouping, structureOf(root));
        var before = snapshot();
        DependencyGraphService graph = new DependencyGraphService(registry);
        ModelForge modelForge = modelForge(graph);

        assertThatThrownBy(() -> registry.inTransaction(() -> {
            copy(modelForge, root);
            throw new IllegalStateException("host failure after the copy");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(graph.getDependencies(UrnParser.withVersion(grouping, "2.0.0"))).isEmpty();
    }

    @Test
    @DisplayName("A copy publishes its graph nodes once the host transaction commits, not before")
    void committedCopyPublishesGraphNodesAfterCommit() {
        String root = registry.storeElement(schema(null, "temperature"), Set.of());
        String grouping = groupingOf(UrnParser.logicalUrn(root));
        registry.storeDataStructure(grouping, structureOf(root));
        DependencyGraphService graph = new DependencyGraphService(registry);
        ModelForge modelForge = modelForge(graph);
        String groupingCopy = UrnParser.withVersion(grouping, "2.0.0");
        String rootCopy = UrnParser.withVersion(UrnParser.logicalUrn(root), "2.0.0");

        Set<String> beforeCommit = registry.inTransaction(() -> {
            copy(modelForge, root);
            return graph.getDependencies(groupingCopy);
        });

        assertThat(beforeCommit).isEmpty();
        assertThat(graph.getDependencies(groupingCopy)).containsExactly(rootCopy);
    }

    private ModelForge modelForge() {
        return modelForge(new DependencyGraphService(registry));
    }

    private ModelForge modelForge(DependencyGraphService graph) {
        return new EmbeddedModelForgeOperations(
            mock(SchemaImportService.class),
            new ElementQueryService(registry),
            mock(ElementCommandService.class),
            mock(ViewService.class),
            mock(ModelValidator.class),
            mock(CoreSchemaValidator.class),
            new ReferenceExistenceValidator(registry, new SchemaRefExtractor()),
            graph,
            registry,
            mock(SmartDataModelsService.class),
            mock(XRepositoryService.class),
            urns);
    }

    private static String copy(ModelForge modelForge, String urn) {
        return modelForge.copyVersion(new CopyVersionCommand(new ArtifactId(urn))).artifactId().value();
    }

    private String groupingOf(String elementLogicalUrn) {
        return UrnParser.logicalUrn(urns.dataStructure(UrnParser.idFromUrn(elementLogicalUrn)));
    }

    private String storedJson(String logical, String version) {
        UUID artifact = new ArtifactRepository(jdbc).findByLogicalUrn(logical).orElseThrow().id();
        UUID id = new ArtifactVersionRepository(jdbc).find(artifact, version).orElseThrow().id();
        return new ArtifactRepresentationRepository(jdbc).find(id, RegistryMapping.FORMAT_JSONSCHEMA)
            .orElseThrow().contentJson();
    }

    @Test
    @DisplayName("A deleted version's number is never given out again, by a write or by a bump")
    void aDeletedNumberIsNeverReassigned() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);
        storeUnder(logical, "pressure", VersionBump.MAJOR);

        registry.deleteVersions(logical, List.of("3.0.0"));

        assertThat(currentVersionOf(logical)).isEqualTo("2.0.0");
        assertThat(UrnParser.versionFromUrn(storeUnder(logical, "wind", VersionBump.MAJOR))).isEqualTo("4.0.0");
        registry.deleteVersions(logical, List.of("4.0.0"));
        assertThat(UrnParser.versionFromUrn(registry.copyVersion(UrnParser.withVersion(logical, "1.0.0"), null, Map.of())))
            .isEqualTo("5.0.0");
    }

    @Test
    @DisplayName("A major counts from the highest number even when the write names a base")
    void aMajorCountsFromTheHighestNumberDespiteABase() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);

        String major = registry.storeElement(schema(logical, "wind"), Set.of(), Set.of(), null, VersionBump.MAJOR, "1.0.0");
        String minor = registry.storeElement(schema(logical, "rain"), Set.of(), Set.of(), null, VersionBump.MINOR, "1.0.0");

        assertThat(UrnParser.versionFromUrn(major)).isEqualTo("3.0.0");
        assertThat(UrnParser.versionFromUrn(minor)).as("a minor stays in its base's line").isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("Revising a line to another line's content gives a new version in the revised line")
    void revisingALineToAnotherLinesContentStaysInTheRevisedLine() {
        // Without this, the revision returns the other line's pin, and deleting the revised line
        // deletes the other line's model with it.
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MAJOR);

        String revised = registry.storeElement(
            schema(logical, "humidity"), Set.of(), Set.of(), null, VersionBump.MINOR, "1.0.0");
        registry.deleteVersions(logical, List.of("1.0.0", "1.1.0"));

        assertThat(UrnParser.versionFromUrn(revised)).isEqualTo("1.1.0");
        assertThat(versionsOf(logical)).containsExactly("2.0.0");
    }

    @Test
    @DisplayName("Revising a version to its line's latest content returns that latest pin and adds no version")
    void revisingToTheLinesLatestContentIsAbsorbedInThatLine() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        String lineLatest = storeUnder(logical, "humidity", VersionBump.MINOR);
        storeUnder(logical, "pressure", VersionBump.MAJOR);

        String revised = registry.storeElement(
            schema(logical, "humidity"), Set.of(), Set.of(), null, VersionBump.MINOR, "1.0.0");

        assertThat(revised).isEqualTo(lineLatest);
        assertThat(versionsOf(logical)).containsExactly("1.0.0", "1.1.0", "2.0.0");
    }

    @Test
    @DisplayName("An artifact recreated under a deleted one's URN continues after its numbers")
    void aRecreatedArtifactContinuesAfterTheDeletedNumbers() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        storeUnder(logical, "humidity", VersionBump.MINOR);
        registry.deleteArtifact(logical);

        String recreated = storeUnder(logical, "temperature", VersionBump.PATCH);

        assertThat(UrnParser.versionFromUrn(recreated)).isEqualTo("2.0.0");
    }

    @Test
    @DisplayName("Deleting every version is refused; that deletes the artifact")
    void deletingEveryVersionIsRefused() {
        String logical = UrnParser.logicalUrn(registry.storeElement(schema(null, "temperature"), Set.of()));
        var before = snapshot();

        assertThatThrownBy(() -> registry.deleteVersions(logical, List.of("1.0.0")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(snapshot()).isEqualTo(before);
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

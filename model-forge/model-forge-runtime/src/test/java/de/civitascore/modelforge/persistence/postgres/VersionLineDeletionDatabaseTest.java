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
import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.RemoteSchemaRepository;
import de.civitascore.modelforge.facade.ModelForge;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.CoreSchemaValidator;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Deleting one version line, driven through the facade the way the portal stores its models: a
 * {@code $defs} library is the Data Structure itself, a model with a shape of its own is a root
 * Element grouped by the Data Structure of the same UUID on first store and saved as that Element
 * afterwards.
 */
class VersionLineDeletionDatabaseTest extends AbstractRegistryDatabaseTest {

    private static final String STATION_V1 = "{\"type\": \"object\", \"properties\": {\"id\": {\"type\": \"string\"}}}";
    private static final String STATION_V2 = "{\"type\": \"object\", \"properties\": {\"code\": {\"type\": \"string\"}}}";
    private static final String LEGACY = "{\"type\": \"object\", \"properties\": {\"old\": {\"type\": \"string\"}}}";

    private final UUID dataStructureId = UUID.randomUUID();

    private ModelForge modelForge;
    private DependencyGraphService graph;
    private String structure;
    private String station;

    @BeforeEach
    void wireTheFacade() {
        graph = new DependencyGraphService(registry);
        var refExtractor = new SchemaRefExtractor();
        var validator = new ModelValidator();
        var coreSchemaValidator = new CoreSchemaValidator(mapper);
        var references = new ReferenceExistenceValidator(registry, refExtractor);
        modelForge = new EmbeddedModelForgeOperations(
            new SchemaImportService(validator, mapper, registry, urns, refExtractor, graph, references,
                mock(RemoteSchemaRepository.class), coreSchemaValidator),
            new ElementQueryService(registry),
            new ElementCommandService(registry, graph, refExtractor, validator),
            mock(ViewService.class),
            validator,
            coreSchemaValidator,
            references,
            graph,
            registry,
            mock(SmartDataModelsService.class),
            mock(XRepositoryService.class),
            urns);
        structure = UrnParser.logicalUrn(urns.dataStructure(dataStructureId));
        station = UrnParser.logicalUrn(urns.member(dataStructureId, "Station"));
    }

    // ── A $defs library ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Deleting a line removes every minor of it and keeps the other lines")
    void deletingALineRemovesEveryMinorAndKeepsTheOtherLines() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MINOR, "1.0.0", "Station", STATION_V2);
        String second = library(VersionBump.MAJOR, null, "Station", STATION_V1);

        modelForge.deleteVersionLine(new ArtifactId(first));

        assertThat(versionsOf(structure)).containsExactly("2.0.0");
        assertThat(registry.fetch(second)).isPresent();
    }

    @Test
    @DisplayName("Deleting the highest line moves the current version to the highest one left")
    void deletingTheHighestLineMovesTheCurrentVersion() {
        library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MINOR, "1.0.0", "Station", STATION_V2);
        String second = library(VersionBump.MAJOR, null, "Station", STATION_V1);

        modelForge.deleteVersionLine(new ArtifactId(second));

        assertThat(registry.resolveReference(structure)).contains(UrnParser.withVersion(structure, "1.1.0"));
    }

    @Test
    @DisplayName("A member version another line holds survives, one only the line holds goes")
    void aMemberVersionAnotherLineHoldsSurvives() {
        // Line 1 holds Station 1.0.0 and Legacy; line 2 reuses the unchanged Station 1.0.0; the edit
        // of line 1 then changes Station, which mints Station 1.1.0 for line 1 alone.
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1, "Legacy", LEGACY);
        library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MINOR, "1.0.0", "Station", STATION_V2, "Legacy", LEGACY);
        assertThat(versionsOf(station)).containsExactly("1.0.0", "1.1.0");

        modelForge.deleteVersionLine(new ArtifactId(first));

        assertThat(versionsOf(station)).containsExactly("1.0.0");
        assertThat(registry.resolveReference(station)).contains(UrnParser.withVersion(station, "1.0.0"));
        assertThat(registry.fetch(urns.member(dataStructureId, "Legacy"))).as("no version left").isEmpty();
    }

    @Test
    @DisplayName("A reference pinned to a superseded minor of the line refuses the delete")
    void aReferencePinnedToASupersededMinorRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        String edited = library(VersionBump.MINOR, "1.0.0", "Station", STATION_V2);
        String sink = sinkInto(first);
        var before = snapshot();

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(edited))).containsExactly(logical(sink));
        assertThatThrownBy(() -> modelForge.deleteVersionLine(new ArtifactId(edited)))
            .isInstanceOf(ArtifactInUseException.class)
            .hasMessageContaining(logical(sink));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("A reference pinned to another line leaves this line free")
    void aReferencePinnedToAnotherLineLeavesThisLineFree() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        sinkInto(library(VersionBump.MAJOR, null, "Station", STATION_V2));

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).isEmpty();
        modelForge.deleteVersionLine(new ArtifactId(first));

        assertThat(versionsOf(structure)).containsExactly("2.0.0");
    }

    @Test
    @DisplayName("A reference that names no version refuses every line")
    void aReferenceThatNamesNoVersionRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        String sink = sinkInto(structure);

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).containsExactly(logical(sink));
    }

    @Test
    @DisplayName("A reference to a member version only the line holds refuses the delete")
    void aReferenceToAMemberVersionOnlyTheLineHoldsRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        String sink = sinkInto(UrnParser.withVersion(station, "1.0.0"));

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).containsExactly(logical(sink));
    }

    @Test
    @DisplayName("Deleting the only line deletes the artifact and unlinks its Data Sets")
    void deletingTheOnlyLineDeletesTheArtifact() {
        String only = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MINOR, "1.0.0", "Station", STATION_V2);
        String dataSet = registry.storeDataSet(urns.urn("dataset", UUID.randomUUID()),
            mapper.readTree("{\"title\": \"Weather\", \"datastructureRefs\": [\"%s\"]}".formatted(structure)),
            VersionBump.PATCH);

        modelForge.deleteVersionLine(new ArtifactId(only));

        assertThat(registry.fetch(structure)).isEmpty();
        assertThat(registry.fetch(station)).isEmpty();
        assertThat(registry.fetch(logical(dataSet)).orElseThrow().toString()).doesNotContain(structure);
    }

    @Test
    @DisplayName("A line delete inside a unit that fails afterwards removes nothing")
    void aFailedUnitKeepsTheLine() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        var before = snapshot();

        assertThatThrownBy(() -> registry.<Void>inTransaction(() -> {
            modelForge.deleteVersionLine(new ArtifactId(first));
            throw new IllegalStateException("the caller fails after the delete");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(graph.getDependencies(first)).as("the graph shows only what committed").isNotEmpty();
    }

    @Test
    @DisplayName("A pin from a superseded member version of another Data Structure refuses the delete")
    void aPinFromASupersededMemberVersionOfAnotherStructureRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        // Data Structure B's first line holds a member pinning Station 1.0.0; its second line
        // revises that member without the pin, so the pinning version is no longer current.
        UUID otherId = UUID.randomUUID();
        String other = urns.dataStructure(otherId);
        String visitV1 = registry.storeElement(memberOf(otherId, "Visit", "\"station\": {\"$ref\": \"%s\"}"
            .formatted(UrnParser.withVersion(station, "1.0.0"))), Set.of(UrnParser.withVersion(station, "1.0.0")));
        registry.storeDataStructure(other, defs("Visit", visitV1), VersionBump.PATCH, null);
        String visitV2 = registry.storeElement(memberOf(otherId, "Visit", "\"note\": {\"type\": \"string\"}"),
            Set.of(), Set.of(), null, VersionBump.MINOR, null);
        registry.storeDataStructure(other, defs("Visit", visitV2), VersionBump.MAJOR, null);

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).containsExactly(logical(visitV1));
        assertThatThrownBy(() -> modelForge.deleteVersionLine(new ArtifactId(first)))
            .isInstanceOf(ArtifactInUseException.class);
        assertThat(registry.fetch(UrnParser.withVersion(station, "1.0.0"))).isPresent();
    }

    @Test
    @DisplayName("A pin from a root Element version that is neither current nor owned refuses the delete")
    void aPinFromAnOlderRootElementVersionRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        // The edit of the other model's first line pins Station 1.0.0; its grouping owns only the
        // imported 1.0.0, and its second line is current.
        UUID otherId = UUID.randomUUID();
        String otherRoot = UrnParser.logicalUrn(modelForge.importSchema(new ImportSchemaCommand(
            rootDocument(STATION_V1), VersionBump.MAJOR, null, false, null, otherId)).rootArtifactId().value());
        String pinning = saveRoot(otherRoot, VersionBump.MINOR, "1.0.0", """
            {"type": "object", "properties": {"station": {"$ref": "%s"}}}
            """.formatted(UrnParser.withVersion(station, "1.0.0")));
        saveRoot(otherRoot, VersionBump.MAJOR, null, STATION_V2);
        assertThat(UrnParser.versionFromUrn(pinning)).isEqualTo("1.1.0");

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).containsExactly(otherRoot);
    }

    @Test
    @DisplayName("A superseded Data sink version nothing pins any more does not refuse the delete")
    void aSupersededSinkVersionNothingPinsDoesNotRefuse() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        String sink = sinkInto(first);
        registry.storeDataSink(logical(sink),
            mapper.readTree("{\"title\": \"Store\", \"element\": \"%s\"}".formatted(UrnParser.withVersion(structure, "2.0.0"))),
            VersionBump.MINOR);

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).isEmpty();
    }

    @Test
    @DisplayName("A superseded Data sink version a Pipeline still pins refuses the delete")
    void aSupersededSinkVersionAPipelinePinsRefuses() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        String sinkV1 = sinkInto(first);
        registry.storeDataSink(logical(sinkV1),
            mapper.readTree("{\"title\": \"Store\", \"element\": \"%s\"}".formatted(UrnParser.withVersion(structure, "2.0.0"))),
            VersionBump.MINOR);
        registry.storePipeline(urns.urn("pipeline", UUID.randomUUID()), mapper.readTree("""
            {"$schema": "https://civitasconnect.digital/core/pipeline/v1", "title": "Ingest",
             "nodes": [{"id": "write", "kind": "sink", "sinkRef": "%s"}], "edges": []}
            """.formatted(sinkV1)), VersionBump.PATCH);

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(first))).containsExactly(logical(sinkV1));
    }

    @Test
    @DisplayName("After deleting the highest line the next line takes a number never given out")
    void theNextLineTakesANumberNeverGivenOut() {
        library(VersionBump.MAJOR, null, "Station", STATION_V1);
        library(VersionBump.MAJOR, null, "Station", STATION_V2);
        String third = library(VersionBump.MAJOR, null, "Station", LEGACY);

        modelForge.deleteVersionLine(new ArtifactId(third));
        String next = library(VersionBump.MAJOR, null, "Station", STATION_V1);

        assertThat(UrnParser.versionFromUrn(next)).isEqualTo("4.0.0");
    }

    @Test
    @DisplayName("The graph keeps no node for a deleted version")
    void theGraphKeepsNoNodeForADeletedVersion() {
        String first = library(VersionBump.MAJOR, null, "Station", STATION_V1);
        String second = library(VersionBump.MAJOR, null, "Station", STATION_V2);
        assertThat(graph.getDependencies(first)).isNotEmpty();

        modelForge.deleteVersionLine(new ArtifactId(first));

        assertThat(graph.getDependencies(first)).isEmpty();
        assertThat(graph.getDependencies(second)).isNotEmpty();
    }

    // ── A root Element with its grouping ─────────────────────────────────────

    @Test
    @DisplayName("Deleting the first line of a root Element takes its grouping and the members only it holds")
    void deletingTheFirstLineOfARootElementTakesItsGrouping() {
        String root = UrnParser.logicalUrn(rooted(STATION_V1));
        saveRoot(root, VersionBump.MINOR, "1.0.0", STATION_V2);
        String second = saveRoot(root, VersionBump.MAJOR, null, STATION_V1);

        modelForge.deleteVersionLine(new ArtifactId(UrnParser.withVersion(root, "1.1.0")));

        assertThat(versionsOf(root)).containsExactly("2.0.0");
        assertThat(registry.fetch(second)).isPresent();
        assertThat(registry.fetch(structure)).as("its only version held the deleted line").isEmpty();
        assertThat(registry.fetch(station)).as("held by nothing else").isEmpty();
    }

    @Test
    @DisplayName("Deleting a later line of a root Element leaves its grouping and members")
    void deletingALaterLineOfARootElementLeavesItsGrouping() {
        String first = rooted(STATION_V1);
        String root = UrnParser.logicalUrn(first);
        String second = saveRoot(root, VersionBump.MAJOR, null, STATION_V2);

        modelForge.deleteVersionLine(new ArtifactId(second));

        assertThat(versionsOf(root)).containsExactly("1.0.0");
        assertThat(registry.fetch(structure)).isPresent();
        assertThat(registry.fetch(station)).isPresent();
    }

    @Test
    @DisplayName("A reference to a later line of a root Element refuses its delete")
    void aReferenceToALaterLineOfARootElementRefuses() {
        String root = UrnParser.logicalUrn(rooted(STATION_V1));
        String second = saveRoot(root, VersionBump.MAJOR, null, STATION_V2);
        String sink = sinkInto(second);

        assertThat(modelForge.versionLineDeletionBlockers(new ArtifactId(second))).containsExactly(logical(sink));
    }

    @Test
    @DisplayName("Deleting the only line of a root Element deletes it with its grouping")
    void deletingTheOnlyLineOfARootElementDeletesItWithItsGrouping() {
        String first = rooted(STATION_V1);
        saveRoot(UrnParser.logicalUrn(first), VersionBump.MINOR, "1.0.0", STATION_V2);

        modelForge.deleteVersionLine(new ArtifactId(first));

        assertThat(registry.fetch(logical(first))).isEmpty();
        assertThat(registry.fetch(structure)).isEmpty();
        assertThat(registry.fetch(station)).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Stores a {@code $defs} library under the Data Structure's id, members given as key, schema. */
    private String library(VersionBump bump, String bumpFromVersion, String... members) {
        StringBuilder defs = new StringBuilder();
        for (int i = 0; i < members.length; i += 2) {
            defs.append(i == 0 ? "" : ", ").append("\"%s\": %s".formatted(members[i], members[i + 1]));
        }
        JsonNode document = mapper.readTree("""
            {"title": "Weather", "$ref": "#/$defs/Station", "$defs": {%s}}
            """.formatted(defs));
        return modelForge.importSchema(
            new ImportSchemaCommand(document, bump, null, false, bumpFromVersion, dataStructureId))
            .rootArtifactId().value();
    }

    /** First store of a model with a shape of its own: a root Element with a Station member. */
    private String rooted(String stationSchema) {
        return modelForge.importSchema(
            new ImportSchemaCommand(rootDocument(stationSchema), VersionBump.MAJOR, null, false, null, dataStructureId))
            .rootArtifactId().value();
    }

    /** A later store of the rooted model, which the portal saves as a version of the root Element. */
    private String saveRoot(String rootLogical, VersionBump bump, String bumpFromVersion, String stationSchema) {
        return modelForge.saveArtifact(new SaveArtifactCommand(new ArtifactId(rootLogical), ArtifactKind.ELEMENT,
            rootDocument(stationSchema), bump, null, bumpFromVersion)).artifactId().value();
    }

    private JsonNode rootDocument(String stationSchema) {
        return mapper.readTree("""
            {"title": "Weather", "type": "object", "properties": {"station": {"$ref": "#/$defs/Station"}},
             "$defs": {"Station": %s}}
            """.formatted(stationSchema));
    }

    private JsonNode memberOf(UUID dataStructureId, String key, String properties) {
        return mapper.readTree("""
            {"$id": "%s", "type": "object", "properties": {%s}}
            """.formatted(urns.member(dataStructureId, key), properties));
    }

    private JsonNode defs(String key, String memberPin) {
        return mapper.readTree("""
            {"title": "Visits", "$defs": {"%s": {"$ref": "%s"}}}
            """.formatted(key, memberPin));
    }

    private String sinkInto(String target) {
        return registry.storeDataSink(urns.urn("datasink", UUID.randomUUID()),
            mapper.readTree("{\"title\": \"Store\", \"element\": \"%s\"}".formatted(target)), VersionBump.PATCH);
    }

    private static String logical(String urn) {
        return UrnParser.logicalUrn(urn);
    }
}

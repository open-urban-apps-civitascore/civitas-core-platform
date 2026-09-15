package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.application.ElementCommandService;
import de.civitascore.modelforge.contract.ArtifactInUseException;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.graph.SchemaRefExtractor;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What one delete removes and what refuses it, exercised against a real registry.
 *
 * <p>Asserted by what survives the delete rather than by the calls it makes, so the rules hold for
 * the reference graph the write path actually records. Modelled as one weather feed following the
 * shipped CORE-IR schemas, because a payload of the wrong shape declares no references and would
 * let these tests pass while proving nothing.
 */
class DeletionPolicyDatabaseTest extends AbstractRegistryDatabaseTest {

    private ElementCommandService deletes;
    private DependencyGraphService graph;
    private String stationPin;
    private String readingPin;

    @BeforeEach
    void wireTheDeletePolicy() {
        graph = new DependencyGraphService(registry);
        deletes = new ElementCommandService(registry, graph, new SchemaRefExtractor());
        stationPin = registry.storeElement("Station", schema("stationId"), Set.of());
        readingPin = registry.storeElement("Reading", schema("temperature"), Set.of());
    }

    // ── What a delete takes with it ──────────────────────────────────────────

    @Test
    @DisplayName("Deleting a Data Structure removes the Elements only it uses")
    void deletingADataStructureRemovesTheElementsOnlyItUses() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);

        deletes.delete(structurePin, true);

        assertThat(registry.fetch(structurePin)).isEmpty();
        assertThat(registry.fetch(stationPin)).isEmpty();
        assertThat(registry.fetch(readingPin)).isEmpty();
    }

    @Test
    @DisplayName("An Element a second Data Structure also groups survives the delete")
    void anElementASecondDataStructureGroupsSurvives() {
        String sharedPin = registry.storeDataStructure(
            urns.mintDataStructure("SharedStructure"), dataStructureOf(readingPin), VersionBump.PATCH, null);
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);

        deletes.delete(structurePin, true);

        assertThat(registry.fetch(structurePin)).isEmpty();
        assertThat(registry.fetch(stationPin)).as("used by nothing else").isEmpty();
        assertThat(registry.fetch(readingPin)).as("still grouped by %s", sharedPin).isPresent();
    }

    @Test
    @DisplayName("Deleting a Pipeline removes the Mapping no other Pipeline uses, and nothing else")
    void deletingAPipelineRemovesItsMappingAndNothingElse() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);
        String sinkPin = registry.storeDataSink("ReadingStore", dataSink(), VersionBump.PATCH);
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);
        String pipelinePin = registry.storePipeline(
            "WeatherIngest", pipeline(sourcePin, sinkPin, mappingPin), VersionBump.PATCH);

        deletes.delete(pipelinePin, true);

        assertThat(registry.fetch(pipelinePin)).isEmpty();
        assertThat(registry.fetch(mappingPin)).as("left behind it would refuse both endpoints").isEmpty();
        // A Data Source and a Data Sink belong to the Data Set, not to the pipeline writing through them.
        assertThat(registry.fetch(sourcePin)).isPresent();
        assertThat(registry.fetch(sinkPin)).isPresent();
    }

    @Test
    @DisplayName("A Mapping a second Pipeline uses survives its Pipeline's delete")
    void aMappingASecondPipelineUsesSurvives() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);
        String sinkPin = registry.storeDataSink("ReadingStore", dataSink(), VersionBump.PATCH);
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);
        String firstPin = registry.storePipeline(
            "WeatherIngest", pipeline(sourcePin, sinkPin, mappingPin), VersionBump.PATCH);
        registry.storePipeline("WeatherBackfill", pipeline(sourcePin, sinkPin, mappingPin), VersionBump.PATCH);

        deletes.delete(firstPin, true);

        assertThat(registry.fetch(firstPin)).isEmpty();
        assertThat(registry.fetch(mappingPin)).isPresent();
    }

    @Test
    @DisplayName("Deleting a Mapping leaves the two Data Structures it joined")
    void deletingAMappingLeavesTheStructuresItJoined() {
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);

        deletes.delete(mappingPin, true);

        assertThat(registry.fetch(mappingPin)).isEmpty();
        assertThat(registry.fetch(stationPin)).isPresent();
        assertThat(registry.fetch(readingPin)).isPresent();
    }

    @Test
    @DisplayName("Deleting a Data Source removes nothing besides itself")
    void deletingADataSourceRemovesNothingElse() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);

        deletes.delete(sourcePin, true);

        assertThat(registry.fetch(sourcePin)).isEmpty();
        assertThat(registry.fetch(stationPin)).isPresent();
    }

    @Test
    @DisplayName("Deleting a Data Set takes a Mapping no Pipeline wired")
    void deletingADataSetTakesAMappingNoPipelineWired() {
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);
        String dataSetPin = registry.storeDataSet(
            urns.mintDataSet("WeatherSet"), dataSetOfMappings(mappingPin), VersionBump.PATCH);

        deletes.delete(dataSetPin, true);

        // Left behind, it would refuse both endpoints for good: a mapping is addressed only under
        // its Data Set, so once that is gone no route reaches it.
        assertThat(registry.fetch(mappingPin)).isEmpty();
        assertThat(registry.nonDataSetBlockingDependents(stationPin)).isEmpty();
        assertThat(registry.nonDataSetBlockingDependents(readingPin)).isEmpty();
    }

    @Test
    @DisplayName("Deleting a Data Set keeps the Data Structures it grouped")
    void deletingADataSetKeepsTheDataStructuresItGrouped() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);
        String dataSetPin = registry.storeDataSet(
            urns.mintDataSet("WeatherSet"), dataSetOf(structurePin), VersionBump.PATCH);

        deletes.delete(dataSetPin, true);

        assertThat(registry.fetch(structurePin)).isPresent();
        assertThat(registry.fetch(stationPin)).isPresent();
    }

    // ── What refuses a delete ────────────────────────────────────────────────

    @Test
    @DisplayName("A reference refuses the delete and the refusal names what holds it")
    void aReferenceRefusesTheDeleteAndNamesWhatHoldsIt() {
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);

        assertThatThrownBy(() -> deletes.delete(stationPin))
            .isInstanceOf(ArtifactInUseException.class)
            .hasMessageContaining(UrnParser.logicalUrn(mappingPin));
        assertThat(registry.fetch(stationPin)).isPresent();
    }

    @Test
    @DisplayName("A reference a superseded version recorded refuses nothing")
    void aReferenceASupersededVersionRecordedRefusesNothing() {
        // The sink writes into Station, then is repointed at Reading; its first version keeps the
        // Station edge as history.
        String first = registry.storeDataSink("ReadingStore", dataSinkFor(stationPin), VersionBump.PATCH);
        // Writing under the existing identity versions that sink; a bare name mints a new one.
        String repointed = registry.storeDataSink(logical(first), dataSinkFor(readingPin), VersionBump.MINOR);
        assertThat(versionsOf(logical(repointed))).containsExactly("1.0.0", "1.1.0");

        deletes.delete(stationPin);

        assertThat(registry.fetch(stationPin)).isEmpty();
        // The version the sink actually writes into is still protected.
        assertThatThrownBy(() -> deletes.delete(readingPin)).isInstanceOf(ArtifactInUseException.class);
    }

    @Test
    @DisplayName("A member of one Data Set is deleted and unlinked from its manifest")
    void aMemberOfOneDataSetIsDeletedAndUnlinked() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructureOf(readingPin), VersionBump.PATCH, null);
        String dataSetPin = registry.storeDataSet(
            urns.mintDataSet("WeatherSet"), dataSetOf(structurePin), VersionBump.PATCH);

        deletes.delete(structurePin);

        assertThat(registry.fetch(structurePin)).isEmpty();
        assertThat(registry.fetch(dataSetPin)).isPresent();
        assertThat(registry.fetch(logical(dataSetPin)).orElseThrow().toString())
            .as("the manifest must not list a member that is gone")
            .doesNotContain(logical(structurePin));
    }

    @Test
    @DisplayName("A member two Data Sets hold is refused until it is removed from one")
    void aMemberTwoDataSetsHoldIsRefused() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructureOf(readingPin), VersionBump.PATCH, null);
        registry.storeDataSet(urns.mintDataSet("FirstSet"), dataSetOf(structurePin), VersionBump.PATCH);
        String secondSet = registry.storeDataSet(
            urns.mintDataSet("SecondSet"), dataSetOf(structurePin), VersionBump.PATCH);

        assertThatThrownBy(() -> deletes.delete(structurePin)).isInstanceOf(ArtifactInUseException.class);
        assertThat(registry.fetch(structurePin)).isPresent();

        deletes.unlinkFromDataSet(logical(secondSet), logical(structurePin));
        deletes.delete(structurePin);

        assertThat(registry.fetch(structurePin)).isEmpty();
    }

    @Test
    @DisplayName("Removing one member leaves the Data Set's other members reachable")
    void removingOneMemberLeavesTheOtherMembersReachable() {
        String stays = registry.storeMapping("Stays", mapping(), VersionBump.PATCH);
        String goes = registry.storeMapping("Goes", mapping(), VersionBump.PATCH);
        String dataSetUrn = urns.mintDataSet("WeatherSet");
        registry.storeDataSet(dataSetUrn, dataSetOfMappings(stays, goes), VersionBump.PATCH);

        deletes.delete(goes);

        // Unlinking the deleted member re-stores the manifest under a new version. A membership
        // read that missed that version would report the Data Set as empty and lose the member
        // that is still there.
        assertThat(registry.fetch(stays)).isPresent();
        assertThat(registry.dataSetMemberships(stays)).containsExactly(logical(dataSetUrn));
        assertThat(graph.getDependencies(logical(dataSetUrn)))
            .anyMatch(urn -> logical(urn).equals(logical(stays)));
    }

    private String logical(String urn) {
        return UrnParser.logicalUrn(urn);
    }

    private JsonNode schema(String property) {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "%s": { "type": "number" } }
            }
            """.formatted(property));
    }

    private JsonNode mapping() {
        return mapper.readTree("""
            { "title": "Station to Reading", "source": "%s", "target": "%s" }
            """.formatted(stationPin, readingPin));
    }

    /** A pipeline node is a union discriminated by {@code kind}; the ref field follows from it. */
    private JsonNode pipeline(String sourcePin, String sinkPin, String mappingPin) {
        return mapper.readTree("""
            {
              "$schema": "https://civitasconnect.digital/core/pipeline/v1",
              "title": "Weather ingest",
              "nodes": [
                { "id": "read",  "kind": "source",  "sourceRef":  "%s" },
                { "id": "map",   "kind": "mapping", "mappingRef": "%s" },
                { "id": "write", "kind": "sink",    "sinkRef":    "%s" }
              ],
              "edges": [
                { "id": "e1", "source": "read", "target": "map" },
                { "id": "e2", "source": "map",  "target": "write" }
              ]
            }
            """.formatted(sourcePin, mappingPin, sinkPin));
    }

    private JsonNode dataStructure() {
        return dataStructureOf(stationPin, readingPin);
    }

    private JsonNode dataStructureOf(String... elementPins) {
        StringBuilder defs = new StringBuilder();
        for (int i = 0; i < elementPins.length; i++) {
            defs.append(i == 0 ? "" : ",\n        ")
                .append("\"M%d\": { \"$ref\": \"%s\" }".formatted(i, elementPins[i]));
        }
        return mapper.readTree("""
            { "title": "Weather structure", "$defs": { %s } }
            """.formatted(defs));
    }

    private JsonNode dataSource() {
        return mapper.readTree("{\"title\":\"Station feed\",\"element\":\"%s\"}".formatted(stationPin));
    }

    private JsonNode dataSink() {
        return dataSinkFor(readingPin);
    }

    private JsonNode dataSinkFor(String elementPin) {
        return mapper.readTree("{\"title\":\"Reading store\",\"element\":\"%s\"}".formatted(elementPin));
    }

    private JsonNode dataSetOfMappings(String... mappingPins) {
        String refs = "\"" + String.join("\",\"", mappingPins) + "\"";
        return mapper.readTree("""
            { "title": "Weather set", "mappingRefs": [%s] }
            """.formatted(refs));
    }

    private JsonNode dataSetOf(String... structurePins) {
        String refs = structurePins.length == 0
            ? ""
            : "\"" + String.join("\",\"", structurePins) + "\"";
        return mapper.readTree("""
            { "title": "Weather set", "datastructureRefs": [%s] }
            """.formatted(refs));
    }
}

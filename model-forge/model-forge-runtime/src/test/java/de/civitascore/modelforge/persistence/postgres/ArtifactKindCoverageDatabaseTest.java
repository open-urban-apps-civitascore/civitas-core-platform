package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
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
 * Every artifact kind the platform models stores as a versioned artifact, with the references its
 * payload declares.
 *
 * <p>Modelled as one weather feed — Elements grouped by a DataStructure, a Mapping between them, a
 * Pipeline wiring a DataSource to a DataSink, and a DataSet collecting them. The payloads follow the
 * shipped CORE-IR schemas (a pipeline node is a union discriminated by {@code kind}; a DataSet lists
 * the artifact kinds it collects, not Elements), because a payload of the wrong shape declares no
 * references and would let these tests pass while proving nothing. Each kind therefore asserts its
 * stored edges.
 */
class ArtifactKindCoverageDatabaseTest extends AbstractRegistryDatabaseTest {

    private String stationPin;
    private String readingPin;

    @BeforeEach
    void storeTheElementsEverythingElseReferences() {
        stationPin = registry.storeElement("Station", schema("stationId"), Set.of());
        readingPin = registry.storeElement("Reading", schema("temperature"), Set.of());
    }

    @Test
    @DisplayName("All seven modelled kinds store as versioned artifacts and read back by pin")
    void everyKindStoresAsAVersionedArtifact() {
        Map<String, String> pins = storeTheFeed();

        pins.forEach((kind, pin) -> {
            assertThat(UrnParser.versionFromUrn(pin))
                .as("%s must be pinned to a concrete version", kind).isEqualTo("1.0.0");
            assertThat(registry.fetch(pin)).as("%s must be readable by its pin", kind).isPresent();
        });

        assertThat(rows("artifact").stream().map(r -> r.get("artifact_type")).distinct())
            .containsExactlyInAnyOrder(
                "element", "datastructure", "dataset", "mapping", "pipeline", "datasource", "datasink");
    }

    @Test
    @DisplayName("A mapping's source and target each name one specific version")
    void mappingSourceAndTargetNameOneVersionEach() {
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);

        Map<String, List<String>> byType = registry.referencesByType(mappingPin);
        assertThat(byType.get("mapping-source")).containsExactly(stationPin);
        assertThat(byType.get("mapping-target")).containsExactly(readingPin);
        // Both edges are stored pinned, so each names one immutable version, not "whatever is newest".
        assertThat(UrnParser.versionFromUrn(stationPin)).isEqualTo("1.0.0");
        assertThat(UrnParser.versionFromUrn(readingPin)).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("A pipeline records an edge for each node reference it declares")
    void pipelineRecordsItsNodeReferences() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);
        String sinkPin = registry.storeDataSink("ReadingStore", dataSink(), VersionBump.PATCH);
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);

        String pipelinePin = registry.storePipeline(
            "WeatherIngest", pipeline(sourcePin, sinkPin, mappingPin), VersionBump.PATCH);

        assertThat(registry.fetchArtifactRefUrns(pipelinePin))
            .containsExactlyInAnyOrder(sourcePin, sinkPin, mappingPin);
    }

    @Test
    @DisplayName("A DataStructure records an edge to each member Element it groups")
    void dataStructureRecordsItsMembers() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);

        assertThat(registry.fetchArtifactRefUrns(structurePin))
            .containsExactlyInAnyOrder(stationPin, readingPin);
    }

    @Test
    @DisplayName("A DataSource and DataSink each record an edge to the Element describing their payload")
    void dataSourceAndSinkRecordTheirElement() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);
        String sinkPin = registry.storeDataSink("ReadingStore", dataSink(), VersionBump.PATCH);

        assertThat(registry.fetchArtifactRefUrns(sourcePin)).containsExactly(stationPin);
        assertThat(registry.fetchArtifactRefUrns(sinkPin)).containsExactly(readingPin);
    }

    @Test
    @DisplayName("A DataSet records an edge to every artifact it collects")
    void dataSetRecordsItsMembers() {
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);

        String dataSetPin = registry.storeDataSet(
            urns.mintDataSet("WeatherSet"), dataSet(structurePin, mappingPin), VersionBump.PATCH);

        assertThat(registry.fetchArtifactRefUrns(dataSetPin))
            .containsExactlyInAnyOrder(structurePin, mappingPin);
        assertThat(registry.dataSetMemberships(structurePin)).contains(UrnParser.logicalUrn(dataSetPin));
    }

    /** One artifact of each modelled kind, wired as a single weather feed. */
    private Map<String, String> storeTheFeed() {
        String sourcePin = registry.storeDataSource("StationFeed", dataSource(), VersionBump.PATCH);
        String sinkPin = registry.storeDataSink("ReadingStore", dataSink(), VersionBump.PATCH);
        String mappingPin = registry.storeMapping("StationToReading", mapping(), VersionBump.PATCH);
        String structurePin = registry.storeDataStructure(
            urns.mintDataStructure("WeatherStructure"), dataStructure(), VersionBump.PATCH, null);
        return Map.of(
            "element", stationPin,
            "datastructure", structurePin,
            "datasource", sourcePin,
            "datasink", sinkPin,
            "mapping", mappingPin,
            "pipeline", registry.storePipeline(
                "WeatherIngest", pipeline(sourcePin, sinkPin, mappingPin), VersionBump.PATCH),
            "dataset", registry.storeDataSet(
                urns.mintDataSet("WeatherSet"), dataSet(structurePin, mappingPin), VersionBump.PATCH));
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

    /** A mapping names the version it reads from and the version it writes to. */
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

    /** A DataStructure groups its member Elements as a library of URN references. */
    private JsonNode dataStructure() {
        return mapper.readTree("""
            {
              "title": "Weather structure",
              "$defs": {
                "Station": { "$ref": "%s" },
                "Reading": { "$ref": "%s" }
              }
            }
            """.formatted(stationPin, readingPin));
    }

    private JsonNode dataSource() {
        return mapper.readTree("{\"title\":\"Station feed\",\"element\":\"%s\"}".formatted(stationPin));
    }

    private JsonNode dataSink() {
        return mapper.readTree("{\"title\":\"Reading store\",\"element\":\"%s\"}".formatted(readingPin));
    }

    /** A DataSet collects the artifact kinds it publishes, each in its own reference array. */
    private JsonNode dataSet(String structurePin, String mappingPin) {
        return mapper.readTree("""
            {
              "title": "Weather set",
              "datastructureRefs": ["%s"],
              "mappingRefs": ["%s"]
            }
            """.formatted(structurePin, mappingPin));
    }
}

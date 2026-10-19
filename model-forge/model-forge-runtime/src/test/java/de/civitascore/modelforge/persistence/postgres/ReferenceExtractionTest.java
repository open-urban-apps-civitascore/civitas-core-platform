package de.civitascore.modelforge.persistence.postgres;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-artifact-type reference extraction: each type keeps its cross-references in a different
 * shape, and every one must become an {@code artifact_reference} row so all types are navigable
 * in the dependency graph. Blank/non-URN values are skipped, not persisted as dangling edges.
 */
class ReferenceExtractionTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** target_urn → reference_type for concise assertions. */
    private static java.util.Map<String, String> byUrn(List<ReferenceRow> rows) {
        return rows.stream().collect(Collectors.toMap(ReferenceRow::targetUrn, ReferenceRow::referenceType));
    }

    @Test
    void mappingRefs_extractsSourceAndTarget() {
        var rows = ReferenceExtraction.mappingRefs(json("""
            {"source":"urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0","target":"urn:core:p:o:element:c:b920de09-e2a0-8c93-a4c0-347abe16cff6:1.0.0"}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0", "mapping-source"),
            java.util.Map.entry("urn:core:p:o:element:c:b920de09-e2a0-8c93-a4c0-347abe16cff6:1.0.0", "mapping-target"));
    }

    @Test
    void dataSourceRefs_extractsElement() {
        var rows = ReferenceExtraction.dataSourceRefs(json("""
            {"connectionType":"mqtt","element":"urn:core:p:o:element:c:06c8e6a6-80c1-8baa-bba8-f7b474f34b8b:1.0.0"}"""));
        assertThat(byUrn(rows)).containsExactly(
            java.util.Map.entry("urn:core:p:o:element:c:06c8e6a6-80c1-8baa-bba8-f7b474f34b8b:1.0.0", "datasource-element"));
    }

    @Test
    void dataSinkRefs_extractsElement() {
        var rows = ReferenceExtraction.dataSinkRefs(json("""
            {"connectionType":"sql","element":"urn:core:p:o:element:c:06c8e6a6-80c1-8baa-bba8-f7b474f34b8b:1.0.0"}"""));
        assertThat(byUrn(rows)).containsExactly(
            java.util.Map.entry("urn:core:p:o:element:c:06c8e6a6-80c1-8baa-bba8-f7b474f34b8b:1.0.0", "datasink-element"));
    }

    @Test
    void dataSourceRefs_withoutElement_isEmpty() {
        assertThat(ReferenceExtraction.dataSourceRefs(json("""
            {"connectionType":"mqtt"}"""))).isEmpty();
    }

    @Test
    void pipelineRefs_extractsEveryNodeReferenceKindIncludingEnrich() {
        // The enrich node's lookupSourceRef is the edge that was previously dropped.
        var rows = ReferenceExtraction.pipelineRefs(json("""
            {"nodes":[
               {"kind":"source","sourceRef":"urn:core:p:o:datasource:c:d0c29cfe-19fa-8af3-9951-e5fdcbe4f645:1.0.0"},
               {"kind":"enrich","lookupSourceRef":"urn:core:p:o:datasource:c:042ac736-5cd0-8516-809e-6cc0816567e2:1.0.0","lookupKey":"k"},
               {"kind":"mapping","mappingRef":"urn:core:p:o:mapping:c:e58a1e47-919e-8a66-afda-04a290f08d0c:1.0.0"},
               {"kind":"sink","sinkRef":"urn:core:p:o:datasink:c:05e5b74a-63d2-8f1b-9e3b-4797757bddd1:1.0.0"},
               {"kind":"start"},
               {"kind":"filter","expression":"x > 1"}
            ]}"""));
        assertThat(rows.stream().map(ReferenceRow::targetUrn)).containsExactlyInAnyOrder(
            "urn:core:p:o:datasource:c:d0c29cfe-19fa-8af3-9951-e5fdcbe4f645:1.0.0",
            "urn:core:p:o:datasource:c:042ac736-5cd0-8516-809e-6cc0816567e2:1.0.0",
            "urn:core:p:o:mapping:c:e58a1e47-919e-8a66-afda-04a290f08d0c:1.0.0",
            "urn:core:p:o:datasink:c:05e5b74a-63d2-8f1b-9e3b-4797757bddd1:1.0.0");
        // All node edges share the pipeline-node reference type, tagged with the node kind.
        assertThat(rows).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("pipeline-node"));
        assertThat(rows.stream().map(ReferenceRow::referenceName))
            .containsExactlyInAnyOrder("source", "enrich", "mapping", "sink");
    }

    @Test
    void dataSetRefs_extractsAllRefArrays() {
        // A DataSet manifest lists its members by kind-specific *Refs arrays; every one becomes a
        // uniform `dataset-ref` edge (the member kind is carried in the reference name, not the type).
        var rows = ReferenceExtraction.dataSetRefs(json("""
            {"datastructureRefs":["urn:core:p:o:datastructure:c:7cbd8b09-0fb8-833a-bdef-c108ab4c9dc5:1.0.0"],
             "mappingRefs":["urn:core:p:o:mapping:c:e58a1e47-919e-8a66-afda-04a290f08d0c:1.0.0"],
             "pipelineRefs":["urn:core:p:o:pipeline:c:53b2162a-2022-8cbe-8010-8fdb8d01afbe:1.0.0"],
             "dataSourceRefs":["urn:core:p:o:datasource:c:d0c29cfe-19fa-8af3-9951-e5fdcbe4f645:1.0.0"],
             "dataSinkRefs":["urn:core:p:o:datasink:c:05e5b74a-63d2-8f1b-9e3b-4797757bddd1:1.0.0"]}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:datastructure:c:7cbd8b09-0fb8-833a-bdef-c108ab4c9dc5:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:mapping:c:e58a1e47-919e-8a66-afda-04a290f08d0c:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:pipeline:c:53b2162a-2022-8cbe-8010-8fdb8d01afbe:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:datasource:c:d0c29cfe-19fa-8af3-9951-e5fdcbe4f645:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:datasink:c:05e5b74a-63d2-8f1b-9e3b-4797757bddd1:1.0.0", "dataset-ref"));
    }

    @Test
    void dataStructureRefs_extractsDefsMemberRefs() {
        // A DataStructure is a $defs library of URN-$refs (no elementRefs array); each $defs.*.$ref
        // is a member Element URN → a datastructure-ref edge.
        var rows = ReferenceExtraction.dataStructureRefs(json("""
            {"$defs":{"A":{"$ref":"urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0"},"B":{"$ref":"urn:core:p:o:element:c:b920de09-e2a0-8c93-a4c0-347abe16cff6:1.0.0"}}}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0", "datastructure-ref"),
            java.util.Map.entry("urn:core:p:o:element:c:b920de09-e2a0-8c93-a4c0-347abe16cff6:1.0.0", "datastructure-ref"));
    }

    @Test
    void schemaAndXsdRefs_keepOnlyWellFormedUrns() {
        // A non-URN or blank value must be skipped, not stored as a dangling edge.
        var schema = ReferenceExtraction.schemaRefs(Set.of(
            "urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0", "#/$defs/Local", "https://example.com/x.json", ""));
        assertThat(schema.stream().map(ReferenceRow::targetUrn))
            .containsExactly("urn:core:p:o:element:c:8d239491-afe4-8354-9d6d-a47677860094:1.0.0");
        assertThat(schema).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("schema-ref"));

        var xsd = ReferenceExtraction.xsdImportRefs(Set.of("urn:core:p:o:element:c:b920de09-e2a0-8c93-a4c0-347abe16cff6:1.0.0"));
        assertThat(xsd).singleElement()
            .satisfies(r -> assertThat(r.referenceType()).isEqualTo("xsd-import"));
    }

    @Test
    void associationRefs_tagsConcreteXCoreRefTargets() {
        // The by-reference counterpart to schemaRefs' by-value $ref edges — same well-formed-URN
        // filtering, distinct reference_type so composition and association stay distinguishable.
        var rows = ReferenceExtraction.associationRefs(Set.of(
            "urn:core:p:o:element:c:09576d41-b872-8a45-8706-8876f164f039:1.0.0", "#/$defs/Local", ""));
        assertThat(rows.stream().map(ReferenceRow::targetUrn))
            .containsExactly("urn:core:p:o:element:c:09576d41-b872-8a45-8706-8876f164f039:1.0.0");
        assertThat(rows).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("association-ref"));
    }
}

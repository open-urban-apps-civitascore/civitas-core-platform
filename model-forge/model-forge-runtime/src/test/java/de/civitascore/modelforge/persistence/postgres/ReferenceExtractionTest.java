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
            {"source":"urn:core:p:o:element:c:A:daismsxdll:1.0.0","target":"urn:core:p:o:element:c:B:7i4d2lwsn3:1.0.0"}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:element:c:A:daismsxdll:1.0.0", "mapping-source"),
            java.util.Map.entry("urn:core:p:o:element:c:B:7i4d2lwsn3:1.0.0", "mapping-target"));
    }

    @Test
    void dataSourceRefs_extractsElement() {
        var rows = ReferenceExtraction.dataSourceRefs(json("""
            {"connectionType":"mqtt","element":"urn:core:p:o:element:c:Reading:ykms479nr3:1.0.0"}"""));
        assertThat(byUrn(rows)).containsExactly(
            java.util.Map.entry("urn:core:p:o:element:c:Reading:ykms479nr3:1.0.0", "datasource-element"));
    }

    @Test
    void dataSinkRefs_extractsElement() {
        var rows = ReferenceExtraction.dataSinkRefs(json("""
            {"connectionType":"sql","element":"urn:core:p:o:element:c:Reading:ykms479nr3:1.0.0"}"""));
        assertThat(byUrn(rows)).containsExactly(
            java.util.Map.entry("urn:core:p:o:element:c:Reading:ykms479nr3:1.0.0", "datasink-element"));
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
               {"kind":"source","sourceRef":"urn:core:p:o:datasource:c:src:1mrurdxu0y:1.0.0"},
               {"kind":"enrich","lookupSourceRef":"urn:core:p:o:datasource:c:lookup:5hvgdk3sa2:1.0.0","lookupKey":"k"},
               {"kind":"mapping","mappingRef":"urn:core:p:o:mapping:c:m:qiyelzcdyp:1.0.0"},
               {"kind":"sink","sinkRef":"urn:core:p:o:datasink:c:snk:ig78w0m13k:1.0.0"},
               {"kind":"start"},
               {"kind":"filter","expression":"x > 1"}
            ]}"""));
        assertThat(rows.stream().map(ReferenceRow::targetUrn)).containsExactlyInAnyOrder(
            "urn:core:p:o:datasource:c:src:1mrurdxu0y:1.0.0",
            "urn:core:p:o:datasource:c:lookup:5hvgdk3sa2:1.0.0",
            "urn:core:p:o:mapping:c:m:qiyelzcdyp:1.0.0",
            "urn:core:p:o:datasink:c:snk:ig78w0m13k:1.0.0");
        // All node edges share the pipeline-node reference type, tagged with the node kind.
        assertThat(rows).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("pipeline-node"));
        assertThat(rows.stream().map(ReferenceRow::referenceName))
            .containsExactlyInAnyOrder("source", "enrich", "mapping", "sink");
    }

    @Test
    void dataSetRefs_extractsAllRefArrays() {
        var rows = ReferenceExtraction.dataSetRefs(json("""
            {"elementRefs":["urn:core:p:o:element:c:A:daismsxdll:1.0.0"],
             "mappingRefs":["urn:core:p:o:mapping:c:m:qiyelzcdyp:1.0.0"],
             "pipelineRefs":["urn:core:p:o:pipeline:c:p:kxhhnk5pd0:1.0.0"],
             "dataSourceRefs":["urn:core:p:o:datasource:c:src:1mrurdxu0y:1.0.0"],
             "dataSinkRefs":["urn:core:p:o:datasink:c:snk:ig78w0m13k:1.0.0"]}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:element:c:A:daismsxdll:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:mapping:c:m:qiyelzcdyp:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:pipeline:c:p:kxhhnk5pd0:1.0.0", "dataset-ref"),
            java.util.Map.entry("urn:core:p:o:datasource:c:src:1mrurdxu0y:1.0.0", "datasource-ref"),
            java.util.Map.entry("urn:core:p:o:datasink:c:snk:ig78w0m13k:1.0.0", "datasink-ref"));
    }

    @Test
    void dataStructureRefs_extractsElementRefs() {
        var rows = ReferenceExtraction.dataStructureRefs(json("""
            {"elementRefs":["urn:core:p:o:element:c:A:daismsxdll:1.0.0","urn:core:p:o:element:c:B:7i4d2lwsn3:1.0.0"]}"""));
        assertThat(byUrn(rows)).containsOnly(
            java.util.Map.entry("urn:core:p:o:element:c:A:daismsxdll:1.0.0", "datastructure-ref"),
            java.util.Map.entry("urn:core:p:o:element:c:B:7i4d2lwsn3:1.0.0", "datastructure-ref"));
    }

    @Test
    void schemaAndXsdRefs_keepOnlyWellFormedUrns() {
        // A non-URN or blank value must be skipped, not stored as a dangling edge.
        var schema = ReferenceExtraction.schemaRefs(Set.of(
            "urn:core:p:o:element:c:A:daismsxdll:1.0.0", "#/$defs/Local", "https://example.com/x.json", ""));
        assertThat(schema.stream().map(ReferenceRow::targetUrn))
            .containsExactly("urn:core:p:o:element:c:A:daismsxdll:1.0.0");
        assertThat(schema).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("schema-ref"));

        var xsd = ReferenceExtraction.xsdImportRefs(Set.of("urn:core:p:o:element:c:B:7i4d2lwsn3:1.0.0"));
        assertThat(xsd).singleElement()
            .satisfies(r -> assertThat(r.referenceType()).isEqualTo("xsd-import"));
    }

    @Test
    void associationRefs_tagsConcreteXCoreRefTargets() {
        // The by-reference counterpart to schemaRefs' by-value $ref edges — same well-formed-URN
        // filtering, distinct reference_type so composition and association stay distinguishable.
        var rows = ReferenceExtraction.associationRefs(Set.of(
            "urn:core:p:o:element:c:Thing:h2s9s5nlvw:1.0.0", "#/$defs/Local", ""));
        assertThat(rows.stream().map(ReferenceRow::targetUrn))
            .containsExactly("urn:core:p:o:element:c:Thing:h2s9s5nlvw:1.0.0");
        assertThat(rows).allSatisfy(r -> assertThat(r.referenceType()).isEqualTo("association-ref"));
    }
}

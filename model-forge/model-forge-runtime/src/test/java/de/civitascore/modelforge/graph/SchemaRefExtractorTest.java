package de.civitascore.modelforge.graph;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SchemaRefExtractor}: only CORE URN $ref values are
 * collected, from any nesting depth; HTTP refs and JSON Pointer fragments
 * are ignored; results are a Set in insertion (document) order.
 */
class SchemaRefExtractorTest {

    private static final String GEO =
            "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0";
    private static final String ADDRESS =
            "urn:core:platform:civitas:element:common:Address:enzw5n1szv:1.0.0";
    private static final String SENSOR =
            "urn:core:platform:civitas:element:common:SensorReading:d28s38wfmi:1.0.0";
    private static final String UNIT =
            "urn:core:platform:civitas:element:common:Unit:6chopb0js2:1.0.0";
    private static final String OBSERVATION =
            "urn:core:platform:civitas:element:common:Observation:ulhry9fjx6:1.0.0";
    private static final String STATION =
            "urn:core:platform:civitas:element:common:Station:hkr6gt1ni1:1.0.0";
    private static final String QUANTITY =
            "urn:core:platform:civitas:element:common:Quantity:m7rxt8oz70:1.0.0";

    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaRefExtractor extractor = new SchemaRefExtractor();

    // ── Refs collected from nested positions ─────────────────────────────────

    @Test
    void extractRefs_collectsUrnRefsFromAllNestedPositions() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "location":     { "$ref": "%s" },
                "measurements": {
                  "type": "array",
                  "items": { "$ref": "%s" }
                }
              },
              "allOf": [ { "$ref": "%s" } ],
              "anyOf": [ { "$ref": "%s" } ],
              "oneOf": [ { "$ref": "%s" } ],
              "$defs": {
                "station": { "$ref": "%s" }
              },
              "additionalProperties": { "$ref": "%s" }
            }
            """.formatted(GEO, SENSOR, ADDRESS, UNIT, OBSERVATION, STATION, QUANTITY));

        Set<String> refs = extractor.extractRefs(schema);

        assertThat(refs).containsExactlyInAnyOrder(
                GEO, SENSOR, ADDRESS, UNIT, OBSERVATION, STATION, QUANTITY);
    }

    @Test
    void extractRefs_deeplyNestedRefInsideDefsProperty_isFound() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "$defs": {
                "wrapper": {
                  "type": "object",
                  "properties": {
                    "inner": {
                      "type": "array",
                      "items": {
                        "anyOf": [ { "$ref": "%s" } ]
                      }
                    }
                  }
                }
              }
            }
            """.formatted(GEO));

        assertThat(extractor.extractRefs(schema)).containsExactly(GEO);
    }

    // ── Non-URN refs are ignored ──────────────────────────────────────────────

    @Test
    void extractRefs_ignoresJsonPointerAndHttpRefs() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "type": "object",
              "properties": {
                "local":  { "$ref": "#/$defs/x" },
                "remote": { "$ref": "https://example.com/s.json" },
                "core":   { "$ref": "%s" }
              },
              "$defs": {
                "x": { "type": "string" }
              }
            }
            """.formatted(GEO));

        assertThat(extractor.extractRefs(schema)).containsExactly(GEO);
    }

    @Test
    void extractRefs_schemaWithoutAnyUrnRefs_returnsEmptySet() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "self": { "$ref": "#/$defs/name" }
              }
            }
            """);

        assertThat(extractor.extractRefs(schema)).isEmpty();
    }

    // ── Set semantics: duplicates collapse, insertion order kept ─────────────

    @Test
    void extractRefs_duplicateRefsCollapse_insertionOrderPreserved() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "a": { "$ref": "%s" },
                "b": { "$ref": "%s" },
                "c": { "$ref": "%s" }
              }
            }
            """.formatted(GEO, ADDRESS, GEO));

        Set<String> refs = extractor.extractRefs(schema);

        // LinkedHashSet semantics: GEO appears once, in first-seen document order
        assertThat(refs).containsExactly(GEO, ADDRESS);
    }

    // ── Null / missing nodes are safe ─────────────────────────────────────────

    @Test
    void extractRefs_nullAndMissingNodes_returnEmptySet() {
        assertThat(extractor.extractRefs(null)).isEmpty();
        assertThat(extractor.extractRefs(NullNode.getInstance())).isEmpty();
        assertThat(extractor.extractRefs(MissingNode.getInstance())).isEmpty();
        // path() on a missing property yields a MissingNode
        assertThat(extractor.extractRefs(mapper.createObjectNode().path("nope"))).isEmpty();
    }

    @Test
    void extractRefs_nullValuedPropertiesInsideSchema_areSafe() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "default": null,
              "properties": {
                "a": null,
                "b": { "$ref": "%s" }
              },
              "examples": [ null, { "$ref": "#/$defs/x" } ]
            }
            """.formatted(GEO));

        assertThat(extractor.extractRefs(schema)).containsExactly(GEO);
    }

    // ── x-core-ref type values ────────────────────────────────────────────────

    @Test
    void extractCoreRefTypes_collectsTypeValues_fromNestedPositions() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "type": "object",
              "properties": {
                "stehtAn": { "type": "string", "x-core-ref": { "type": "%s" } },
                "nested": {
                  "type": "object",
                  "properties": {
                    "ref": { "type": "string", "x-core-ref": { "type": "%s" } }
                  }
                }
              }
            }
            """.formatted(GEO, ADDRESS));

        assertThat(extractor.extractCoreRefTypes(schema)).containsExactlyInAnyOrder(GEO, ADDRESS);
    }

    @Test
    void extractCoreRefTypes_collectsBothCategoryAndConcreteTypes() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "a": { "type": "string", "x-core-ref": { "type": "urn:core:type:Element" } },
                "b": { "type": "string", "x-core-ref": { "type": "urn:core:type:DataSource" } },
                "c": { "type": "string", "x-core-ref": { "type": "%s" } }
              }
            }
            """.formatted(GEO));

        // The extractor does not classify — it returns every declared type; the validator
        // decides what is a category marker vs. a concrete target vs. malformed.
        assertThat(extractor.extractCoreRefTypes(schema)).containsExactlyInAnyOrder(
            "urn:core:type:Element", "urn:core:type:DataSource", GEO);
    }

    @Test
    void extractCoreRefTypes_noXCoreRef_returnsEmpty() throws Exception {
        JsonNode schema = mapper.readTree("""
            { "properties": { "name": { "type": "string" } } }
            """);

        assertThat(extractor.extractCoreRefTypes(schema)).isEmpty();
    }

    @Test
    void extractCoreRefTypes_collectsMalformedTypeButSkipsNonObjectAndMissing() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "a": { "x-core-ref": "not-an-object" },
                "b": { "x-core-ref": { "noType": true } },
                "c": { "x-core-ref": { "type": "#/$defs/Foo" } }
              }
            }
            """);

        // A present, non-blank type is collected even if malformed (so the validator can flag it);
        // a non-object x-core-ref and a missing type contribute nothing.
        assertThat(extractor.extractCoreRefTypes(schema)).containsExactly("#/$defs/Foo");
    }

    // ── x-core-ref association (foreign-key) targets — dependency-graph edges ───

    @Test
    void extractCoreRefTargets_collectsOnlyConcreteUrnTargets() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "thing":    { "type": "string", "x-core-ref": { "type": "%s" } },
                "category": { "type": "string", "x-core-ref": { "type": "urn:core:type:Element" } },
                "malformed": { "type": "string", "x-core-ref": { "type": "#/$defs/Foo" } },
                "another":  { "type": "string", "x-core-ref": { "type": "%s" } }
              }
            }
            """.formatted(GEO, ADDRESS));

        // The category marker and the malformed (non-URN) value are excluded — only concrete,
        // existing-artifact targets become dependency-graph edges.
        assertThat(extractor.extractCoreRefTargets(schema)).containsExactlyInAnyOrder(GEO, ADDRESS);
    }

    @Test
    void extractCoreRefTargets_arrayOfAssociations_allCollected() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "things": {
                  "type": "array",
                  "items": { "type": "string", "x-core-ref": { "type": "%s" } }
                }
              }
            }
            """.formatted(SENSOR));

        assertThat(extractor.extractCoreRefTargets(schema)).containsExactly(SENSOR);
    }

    @Test
    void extractCoreRefTargets_noConcreteTargets_returnsEmpty() throws Exception {
        JsonNode schema = mapper.readTree("""
            {
              "properties": {
                "a": { "type": "string", "x-core-ref": { "type": "urn:core:type:Element" } }
              }
            }
            """);

        assertThat(extractor.extractCoreRefTargets(schema)).isEmpty();
    }
}

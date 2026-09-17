package de.civitascore.modelforge.validation;

import de.civitascore.modelforge.contract.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What schema validation accepts and what it reports.
 *
 * <p>{@code validateSchema} checks the document against the 2020-12 meta-schema, compiles it, and
 * checks that its local pointers resolve, and leaves registry references opaque because they
 * resolve against the registry rather than the document.
 */
class SchemaAcceptanceBoundaryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator();

    @Test
    @DisplayName("A well-formed 2020-12 schema is accepted")
    void wellFormedSchemaIsAccepted() {
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "temperature": { "type": "number" } }
            }
            """))).isEmpty();
    }

    @Test
    @DisplayName("A local pointer that resolves nowhere is reported, naming the offending pointer")
    void danglingLocalPointerIsReported() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "properties": { "station": { "$ref": "#/$defs/Missing" } }
            }
            """));

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics.toString()).contains("#/$defs/Missing");
    }

    @Test
    @DisplayName("A CORE-URN reference is left opaque rather than treated as a dangling pointer")
    void coreUrnReferenceIsOpaqueToSchemaValidation() {
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "properties": {
                "station": { "$ref": "urn:core:platform:civitas:element:common:Station:abc1234567:1.0.0" }
              }
            }
            """)))
            .as("registry references resolve against the registry, not against the document")
            .isEmpty();
    }

    @Test
    @DisplayName("A type given as a number is rejected, naming the location in the document")
    void nonConformingTypeIsRejected() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": 42
            }
            """));

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).allSatisfy(d -> assertThat(d.path()).isEqualTo("/type"));
    }

    @Test
    @DisplayName("A type misspelled deep in the document is rejected, naming that location")
    void nonConformingNestedTypeIsRejected() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "temperature": { "type": "nubmer" } }
            }
            """));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics).allSatisfy(
            d -> assertThat(d.path()).isEqualTo("/properties/temperature/type"));
        assertThat(diagnostics.getFirst().message())
            .contains("number")
            .doesNotContain("array expected");
    }

    @Test
    @DisplayName("A keyword given the wrong JSON type is rejected")
    void nonConformingKeywordTypeIsRejected() {
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": "temperature"
            }
            """)))
            .as("`required` takes an array of names, not a single name")
            .isNotEmpty();
    }

    @Test
    @DisplayName("The CORE annotation keywords are accepted by the meta-schema check")
    void coreAnnotationKeywordsRemainAccepted() {
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "x-xsd-source": "station.xsd",
              "properties": {
                "station": {
                  "type": "string",
                  "x-core-ref": { "type": "urn:core:type:Element" },
                  "x-ui-position": { "x": 1, "y": 2 }
                }
              }
            }
            """)))
            .as("the CORE extensions are annotations the 2020-12 vocabulary permits")
            .isEmpty();
    }

    @Test
    @DisplayName("A remote reference is not fetched while checking conformance")
    void remoteReferenceIsNotFetchedByTheConformanceCheck() {
        // A meta-schema check treats the document as data, so a $ref is a string being type-checked
        // rather than an address. Were it dereferenced, the server would issue the request — the
        // SSRF exposure DisallowSchemaLoader exists to prevent on the compile path.
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "station": { "$ref": "http://169.254.169.254/latest/meta-data/" } }
            }
            """)))
            .isNotEmpty();
    }

    @Test
    @DisplayName("One mistake is reported once however many meta-schema paths reach it")
    void oneMistakePerPositionIsReportedOnce() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "a": { "items": 42 }, "b": { "items": 7 } }
            }
            """));

        assertThat(diagnostics)
            .as("two mistakes, though the meta-schema reaches each through eight paths")
            .hasSize(2);
        assertThat(diagnostics).extracting(Diagnostic::path)
            .containsExactlyInAnyOrder("/properties/a/items", "/properties/b/items");
    }

    @Test
    @DisplayName("Mistakes at different positions are each reported")
    void distinctPositionsAreEachReported() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "nubmer",
              "required": "name"
            }
            """));

        assertThat(diagnostics).extracting(Diagnostic::path)
            .containsExactlyInAnyOrder("/type", "/required");
    }

    @Test
    @DisplayName("A document with many mistakes reports a bounded number of them")
    void diagnosticCountIsBounded() {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            properties.append(i > 0 ? "," : "").append("\"p%d\": { \"type\": \"nubmer\" }".formatted(i));
        }

        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { %s }
            }
            """.formatted(properties))))
            .hasSize(ModelValidator.MAX_NONCONFORMING_DIAGNOSTICS);
    }

    @Test
    @DisplayName("An array-valued type reports what the array form objected to, not the name form")
    void arrayValuedTypeReportsTheArrayBranch() {
        // Both messages sit inside the meta-schema's own type anyOf, so a rule that preferred
        // whatever came first would report "not in the enumeration" — false, since the entries
        // are valid names and the real mistake is the duplicate.
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": ["object", "object"]
            }
            """));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().message()).contains("unique items");
    }

    @Test
    @DisplayName("Two mistakes at one position are both reported")
    void twoMistakesAtOnePositionAreBothReported() {
        // nonNegativeInteger is type plus minimum, so one value breaks both and fixing either
        // leaves the other standing; collapsing them costs the author a second round trip.
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "minProperties": -1.5
            }
            """));

        assertThat(diagnostics).hasSize(2);
        assertThat(diagnostics).extracting(Diagnostic::message)
            .anySatisfy(m -> assertThat(m).contains("integer expected"))
            .anySatisfy(m -> assertThat(m).contains("minimum value of 0"));
    }

    @Test
    @DisplayName("A schema using anyOf itself is not mistaken for the meta-schema's own branch")
    void authorAnyOfIsNotMistakenForTheMetaSchemaBranch() {
        List<Diagnostic> diagnostics = validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "properties": { "t": { "anyOf": [ { "type": "nubmer" } ] } }
            }
            """));

        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.getFirst().message()).doesNotContain("array expected");
    }

    @Test
    @DisplayName("An array-valued type that is well formed is accepted")
    void wellFormedArrayValuedTypeIsAccepted() {
        assertThat(validator.validateSchema(json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": ["string", "null"]
            }
            """))).isEmpty();
    }

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }
}

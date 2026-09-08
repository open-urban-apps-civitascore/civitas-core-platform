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

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).allSatisfy(
            d -> assertThat(d.path()).isEqualTo("/properties/temperature/type"));
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

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }
}

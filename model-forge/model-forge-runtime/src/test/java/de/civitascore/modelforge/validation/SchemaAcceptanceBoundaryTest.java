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
 * <p>{@code validateSchema} compiles the document and checks that its local pointers resolve, and
 * leaves registry references opaque because they resolve against the registry rather than the
 * document.
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

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }
}

package de.civitascore.modelforge.validation;

import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.Diagnostic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A typed artifact is checked against the CORE contract published for its kind.
 *
 * <p>Validation by kind is the write-time gate: it applies the kind's contract whatever the document
 * declares in {@code $schema}, so a payload cannot skip the check by naming a different schema.
 * {@code ELEMENT} has no fixed contract — an Element is an arbitrary JSON Schema or XSD — and passes
 * through here by design.
 */
class CoreSchemaValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CoreSchemaValidator validator = new CoreSchemaValidator(mapper);

    // ── Mapping ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming Mapping is accepted")
    void conformingMappingIsAccepted() {
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:StationToReading:abc1234567:1.0.0",
              "fields": { "temperature": "temp" }
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A Mapping missing its field map is rejected")
    void mappingWithoutFieldsIsRejected() {
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:StationToReading:abc1234567:1.0.0"
            }
            """)).isNotEmpty();
    }

    @Test
    @DisplayName("A Mapping whose identity is not a mapping URN is rejected")
    void mappingWithForeignIdentityIsRejected() {
        // An element URN in the identity of a mapping: the contract pins the artifact type.
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:element:common:Station:abc1234567:1.0.0",
              "fields": {}
            }
            """)).isNotEmpty();
    }

    @Test
    @DisplayName("A Mapping is checked against its own contract even when it declares another schema")
    void mappingIsCheckedByKindRegardlessOfDeclaredSchema() {
        // Declaring the plain JSON Schema meta-schema must not sidestep the mapping contract.
        List<Diagnostic> diagnostics = validate(ArtifactKind.MAPPING, """
            { "$schema": "https://json-schema.org/draft/2020-12/schema", "fields": {} }
            """);

        assertThat(diagnostics)
            .as("validation by kind must not depend on what the document declares")
            .isNotEmpty();
    }

    // ── Pipeline ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming Pipeline is accepted")
    void conformingPipelineIsAccepted() {
        assertThat(validate(ArtifactKind.PIPELINE, """
            {
              "$schema": "https://civitasconnect.digital/core/pipeline/v1",
              "id": "urn:core:platform:civitas:pipeline:common:WeatherIngest:abc1234567:1.0.0",
              "nodes": [],
              "edges": []
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A Pipeline without its node and edge lists is rejected")
    void pipelineWithoutGraphIsRejected() {
        assertThat(validate(ArtifactKind.PIPELINE, """
            {
              "$schema": "https://civitasconnect.digital/core/pipeline/v1",
              "id": "urn:core:platform:civitas:pipeline:common:WeatherIngest:abc1234567:1.0.0"
            }
            """)).isNotEmpty();
    }

    // ── DataSet ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming DataSet manifest is accepted")
    void conformingDataSetIsAccepted() {
        assertThat(validate(ArtifactKind.DATA_SET, """
            {
              "$schema": "https://civitasconnect.digital/core-dataset/v1",
              "id": "urn:core:platform:civitas:dataset:common:WeatherSet:abc1234567:1.0.0"
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A DataSet manifest without an identity is rejected")
    void dataSetWithoutIdentityIsRejected() {
        assertThat(validate(ArtifactKind.DATA_SET, """
            { "$schema": "https://civitasconnect.digital/core-dataset/v1" }
            """)).isNotEmpty();
    }

    // ── DataStructure ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming DataStructure is accepted")
    void conformingDataStructureIsAccepted() {
        assertThat(validate(ArtifactKind.DATA_STRUCTURE, """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$id": "urn:core:platform:civitas:datastructure:common:WeatherStructure:abc1234567:1.0.0",
              "type": "object"
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A DataStructure without an identity is rejected")
    void dataStructureWithoutIdentityIsRejected() {
        assertThat(validate(ArtifactKind.DATA_STRUCTURE, """
            { "$schema": "https://json-schema.org/draft/2020-12/schema", "type": "object" }
            """)).isNotEmpty();
    }

    // ── Element ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("An Element has no fixed contract and passes through")
    void elementHasNoFixedContract() {
        // An Element is an arbitrary JSON Schema; ModelValidator checks that it is a schema at all.
        assertThat(validate(ArtifactKind.ELEMENT, """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "temperature": { "type": "number" } }
            }
            """)).isEmpty();
    }

    // ── The declared-schema entry point ─────────────────────────────────────────

    @Test
    @DisplayName("A document declaring a CORE schema is checked against exactly that schema")
    void declaredCoreSchemaIsEnforced() {
        List<Diagnostic> diagnostics = validator.validate(json("""
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:StationToReading:abc1234567:1.0.0"
            }
            """));

        assertThat(diagnostics).as("the declared mapping contract requires a field map").isNotEmpty();
    }

    private List<Diagnostic> validate(ArtifactKind kind, String document) {
        return validator.validate(kind, json(document));
    }

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }
}

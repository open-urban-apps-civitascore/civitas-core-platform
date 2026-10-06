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
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0",
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
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0"
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
              "id": "urn:core:platform:civitas:element:common:b4b1eaaf-8766-827e-b89a-95bf592cc9b2:1.0.0",
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

    @Test
    @DisplayName("A Mapping with a toUuid field is accepted")
    void mappingWithToUuidIsAccepted() {
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0",
              "fields": { "$.id": { "op": "toUuid", "input": { "op": "copy", "sourcePath": "$.stationId" } } }
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A Mapping with a toDateTime nested in another operation is accepted")
    void mappingWithNestedToDateTimeIsAccepted() {
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0",
              "fields": {
                "$.observedAt": {
                  "op": "format",
                  "pattern": "yyyy-MM-dd",
                  "input": { "op": "toDateTime", "input": "$.timestamp", "pattern": "yyyy-MM-dd'T'HH:mm:ssXXX" }
                }
              }
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A Mapping with a toDateTime that has no pattern is rejected")
    void mappingWithToDateTimeWithoutPatternIsRejected() {
        assertThat(validate(ArtifactKind.MAPPING, """
            {
              "$schema": "https://civitasconnect.digital/core/mapping/v1",
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0",
              "fields": { "$.observedAt": { "op": "toDateTime", "input": "$.timestamp" } }
            }
            """)).isNotEmpty();
    }

    // ── Pipeline ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming Pipeline is accepted")
    void conformingPipelineIsAccepted() {
        assertThat(validate(ArtifactKind.PIPELINE, """
            {
              "$schema": "https://civitasconnect.digital/core/pipeline/v1",
              "id": "urn:core:platform:civitas:pipeline:common:8c521919-2300-846f-b710-bbbbb212b47a:1.0.0",
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
              "id": "urn:core:platform:civitas:pipeline:common:8c521919-2300-846f-b710-bbbbb212b47a:1.0.0"
            }
            """)).isNotEmpty();
    }

    // ── DataSource ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming MQTT DataSource is accepted")
    void conformingMqttDataSourceIsAccepted() {
        assertThat(validate(ArtifactKind.DATA_SOURCE, """
            {
              "$schema": "https://civitasconnect.digital/core/datasource/v1",
              "id": "urn:core:platform:civitas:datasource:common:a01d5b29-c93e-8dd4-a525-1cf551ce908a:1.0.0",
              "connectionType": "mqtt",
              "urls": ["mqtt://mosquitto:1883"],
              "topics": ["sensors/+/temp"]
            }
            """)).isEmpty();
    }

    // ── DataSet ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A conforming DataSet manifest is accepted")
    void conformingDataSetIsAccepted() {
        assertThat(validate(ArtifactKind.DATA_SET, """
            {
              "$schema": "https://civitasconnect.digital/core-dataset/v1",
              "id": "urn:core:platform:civitas:dataset:common:b365d0f3-0500-89b2-959e-4446fe69850f:1.0.0"
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
              "$id": "urn:core:platform:civitas:datastructure:common:7de7b7a9-189c-80bd-8994-ccdf52b34d72:1.0.0",
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
              "id": "urn:core:platform:civitas:mapping:common:29d58c4c-50a7-8477-b001-ba6abdcc0164:1.0.0"
            }
            """));

        assertThat(diagnostics).as("the declared mapping contract requires a field map").isNotEmpty();
    }

    // ── DataSink ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A FROST DataSink carrying a port is accepted")
    void frostDataSinkWithAPortIsAccepted() {
        // The port is the write logic of the sink. The branch closes over its properties, so a
        // sink could not carry one before the field was published here.
        assertThat(validate(ArtifactKind.DATA_SINK, """
            {
              "$schema": "https://civitasconnect.digital/core/datasink/v1",
              "id": "urn:core:platform:civitas:datasink:common:dcb6179e-f5c7-87f6-8fd4-3590dd906e8c:1.0.0",
              "connectionType": "frost",
              "port": "ThingTree"
            }
            """)).isEmpty();
    }

    @Test
    @DisplayName("A FROST DataSink with a port outside the closed set is rejected")
    void frostDataSinkWithAnUnknownPortIsRejected() {
        // The value reaches a NiFi processor property. Only the published set may be stored.
        assertThat(validate(ArtifactKind.DATA_SINK, """
            {
              "$schema": "https://civitasconnect.digital/core/datasink/v1",
              "id": "urn:core:platform:civitas:datasink:common:dcb6179e-f5c7-87f6-8fd4-3590dd906e8c:1.0.0",
              "connectionType": "frost",
              "port": "Everything"
            }
            """)).isNotEmpty();
    }

    @Test
    @DisplayName("A FROST DataSink without a port is accepted")
    void frostDataSinkWithoutAPortIsAccepted() {
        // The editor stores a FROST sink before the modeller picks a port; rejecting it here would
        // break that save.
        assertThat(validate(ArtifactKind.DATA_SINK, """
            {
              "$schema": "https://civitasconnect.digital/core/datasink/v1",
              "id": "urn:core:platform:civitas:datasink:common:dcb6179e-f5c7-87f6-8fd4-3590dd906e8c:1.0.0",
              "connectionType": "frost"
            }
            """)).isEmpty();
    }

    private List<Diagnostic> validate(ArtifactKind kind, String document) {
        return validator.validate(kind, json(document));
    }

    private JsonNode json(String raw) {
        return mapper.readTree(raw);
    }
}

package de.civitascore.modelforge.validation;

import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.contract.DiagnosticSeverity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated unit tests for {@link ModelValidator}.
 *
 * <p>Exercises both public modes ({@link ModelValidator#validateSchema} and
 * {@link ModelValidator#validateData}) plus the two guards that were previously
 * untested: the circular {@code $ref} stack-overflow guard and the CORE-URN
 * {@code $ref} neutralisation.
 */
class ModelValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ModelValidator validator = new ModelValidator();

    private JsonNode json(String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid test JSON", e);
        }
    }

    // ── validateSchema ──────────────────────────────────────────────────────────

    @Test
    void validateSchema_acceptsAWellFormedSchema() {
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "name": { "type": "string" } },
              "required": ["name"]
            }
            """);

        assertThat(validator.validateSchema(schema)).isEmpty();
    }

    @Test
    void validateSchema_reportsADanglingLocalRefAsAParseError() {
        // #/$defs/Missing has no target — refs are resolved eagerly so this surfaces
        // at schema-parse time rather than being deferred to data validation.
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "child": { "$ref": "#/$defs/Missing" } }
            }
            """);

        List<Diagnostic> diagnostics = validator.validateSchema(schema);

        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.getFirst();
        assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
        assertThat(d.code()).isEqualTo("schema-parse");
        // The message names the offending pointer and where it sits, which the generic
        // "Invalid JSON Schema" could not. It is built here, not taken from the library, so no
        // third-party exception detail is leaked.
        assertThat(d.message()).isEqualTo("Unresolved local $ref '#/$defs/Missing'");
        assertThat(d.path()).isEqualTo("$.properties.child");
    }

    @Test
    void validateSchema_doesNotStackOverflowOnASelfReferentialRefCycle() {
        // A → B → A. The library guards against most cycles, but a pathological chain
        // could exhaust the stack; either way the method must terminate with diagnostics
        // and never propagate a StackOverflowError to the caller.
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "$ref": "#/$defs/A",
              "$defs": {
                "A": { "$ref": "#/$defs/B" },
                "B": { "$ref": "#/$defs/A" }
              }
            }
            """);

        // The key assertion: this call returns (terminates) instead of crashing.
        List<Diagnostic> diagnostics = validator.validateSchema(schema);

        assertThat(diagnostics).isNotNull();
        // A recursive-self-reference schema is structurally tolerated by 2020-12, so it
        // may validate clean; what matters is that the call does not blow the stack.
        diagnostics.forEach(d -> {
            assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
            assertThat(d.code()).isEqualTo("schema-parse");
        });
    }

    @Test
    void validateSchema_neutralisesCoreUrnRefsSoTheyAreNotResolvedAsLocalPointers() {
        // A urn:core $ref is opaque to the validator (resolved by the registry, not here).
        // It must be stripped, so the schema compiles cleanly even though no such local
        // target exists — proving the URN-ref was neutralised rather than dereferenced.
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "geo": { "$ref": "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0" }
              }
            }
            """);

        assertThat(validator.validateSchema(schema))
            .as("CORE-URN $ref must be neutralised, not resolved")
            .isEmpty();
    }

    @Test
    void validateSchema_keepsSiblingKeywordsWhenNeutralisingACoreUrnRef() {
        // Sibling keywords next to a urn:core $ref must survive neutralisation. Here the
        // surviving "type": "integer" makes the property schema otherwise valid.
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "ref": {
                  "$ref": "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0",
                  "type": "integer"
                }
              }
            }
            """);

        assertThat(validator.validateSchema(schema)).isEmpty();
    }

    // ── validateData ──────────────────────────────────────────────────────────

    @Test
    void validateData_returnsNoDiagnosticsWhenDataConforms() {
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "age": { "type": "integer", "minimum": 0 } },
              "required": ["age"]
            }
            """);
        JsonNode data = json("{ \"age\": 42 }");

        assertThat(validator.validateData(schema, data)).isEmpty();
    }

    @Test
    void validateData_reportsAViolationWithItsInstanceLocation() {
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "age": { "type": "integer", "minimum": 0 } },
              "required": ["age"]
            }
            """);
        // age is a string, violating "type": "integer".
        JsonNode data = json("{ \"age\": \"not-a-number\" }");

        List<Diagnostic> diagnostics = validator.validateData(schema, data);

        assertThat(diagnostics).isNotEmpty();
        Diagnostic d = diagnostics.getFirst();
        assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
        // The code is the schema keyword/messageKey that failed (here: a type mismatch).
        assertThat(d.code()).isEqualTo("type");
        // Instance location points at the offending field.
        assertThat(d.path()).contains("age");
    }

    @Test
    void validateData_reportsAMissingRequiredField() {
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["name"]
            }
            """);
        JsonNode data = json("{}");

        List<Diagnostic> diagnostics = validator.validateData(schema, data);

        assertThat(diagnostics).isNotEmpty();
        assertThat(diagnostics).allSatisfy(d -> {
            assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
            // The code is the failed schema keyword (a missing required property).
            assertThat(d.code()).isEqualTo("required");
        });
    }

    @Test
    void validateData_neutralisesCoreUrnRefsSoAConformingInstanceIsValid() {
        // Regression: validateData must neutralise urn:core $refs before getSchema (like
        // validateSchema). Otherwise networknt tries to resolve the urn ref, throws, and
        // every conforming instance that populates the cross-ref field is reported as a
        // single "validator-error" (valid=false). With the fix the urn-ref is opaque and a
        // conforming instance validates clean (zero diagnostics).
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "geo": { "$ref": "urn:core:platform:civitas:element:common:GeoPoint:3ak90vqrog:1.0.0" }
              }
            }
            """);
        // Instance populates the cross-ref field — this is what tripped the eager urn resolve.
        JsonNode data = json("{ \"geo\": { \"lat\": 51.0, \"lon\": 7.0 } }");

        assertThat(validator.validateData(schema, data))
            .as("CORE-URN $ref must be neutralised in validateData; conforming instance is valid")
            .isEmpty();
    }

    @Test
    void validateData_doesNotStackOverflowOnACircularRefSchema() {
        // Recursive schema validated against a self-nesting document: the guard must keep
        // a pathological recursion from propagating a StackOverflowError.
        JsonNode schema = json("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "next": { "$ref": "#" } }
            }
            """);
        JsonNode data = json("{ \"next\": { \"next\": { \"next\": {} } } }");

        List<Diagnostic> diagnostics = validator.validateData(schema, data);

        // Must terminate; a valid recursive document yields no diagnostics. The severity assertion
        // compared the enum against a String, so it could never have failed even if it ran.
        assertThat(diagnostics).isEmpty();
    }
}

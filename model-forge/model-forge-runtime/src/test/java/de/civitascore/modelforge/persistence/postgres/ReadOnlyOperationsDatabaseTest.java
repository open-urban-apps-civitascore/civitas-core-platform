package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.application.ViewService;
import de.civitascore.modelforge.contract.Diagnostic;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.urn.UrnParser;
import de.civitascore.modelforge.validation.ModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validating a document and reading a view leave the stored state untouched.
 *
 * <p>Neither operation raises an error when it also writes, so asserting that nothing was thrown
 * would prove nothing. Each test captures every registry row before the operation and compares the
 * rows afterwards — a validation that stored what it parsed, or a view that minted a version per
 * read, would inflate the version history with entries no author created.
 */
class ReadOnlyOperationsDatabaseTest extends AbstractRegistryDatabaseTest {

    private ModelValidator validator;
    private ViewService views;
    private String stationPin;
    private String readingPin;

    @BeforeEach
    void storeAFeedAndOpenTheReadSide() {
        stationPin = registry.storeElement("Station", schema("stationId"), Set.of());
        readingPin = registry.storeElement("Reading", readingSchema(), Set.of(stationPin));

        validator = new ModelValidator();
        var graph = new DependencyGraphService(registry);
        // Warm the graph before the snapshot, so its own startup reconciliation cannot be mistaken
        // for a write caused by reading a view.
        graph.rebuild();
        views = new ViewService(registry, graph, mapper);
    }

    @Test
    @DisplayName("Validating a candidate schema stores nothing, whether it passes or is rejected")
    void validatingASchemaStoresNothing() {
        Map<String, List<Map<String, Object>>> before = snapshot();

        List<Diagnostic> accepted = validator.validateSchema(schema("draftProperty"));
        List<Diagnostic> rejected = validator.validateSchema(schemaWithDanglingRef());

        assertThat(accepted).isEmpty();
        assertThat(rejected).as("the rejected candidate must be reported, not stored").isNotEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Validating an instance against a stored schema stores nothing")
    void validatingAnInstanceStoresNothing() {
        JsonNode stored = registry.fetch(stationPin).orElseThrow();
        Map<String, List<Map<String, Object>>> before = snapshot();

        assertThat(validator.validateData(stored, mapper.readTree("{\"stationId\": 42}"))).isEmpty();
        assertThat(validator.validateData(stored, mapper.readTree("{\"stationId\": \"not a number\"}")))
            .isNotEmpty();

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Reading the raw, bundled and inlined views creates no version and changes no row")
    void readingTheViewsChangesNothing() {
        Map<String, List<Map<String, Object>>> before = snapshot();
        List<String> versionsBefore = versionsOf(UrnParser.logicalUrn(readingPin));

        assertThat(registry.fetch(readingPin)).as("raw view").isPresent();
        assertThat(views.bundle(readingPin)).as("bundled view").isPresent();
        assertThat(views.inline(readingPin)).as("inlined view").isPresent();

        assertThat(versionsOf(UrnParser.logicalUrn(readingPin)))
            .as("reading a view must not mint a version")
            .isEqualTo(versionsBefore);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    @DisplayName("Reading a view repeatedly leaves the same single version")
    void repeatedViewReadsDoNotAccumulateVersions() {
        for (int i = 0; i < 3; i++) {
            views.bundle(readingPin);
            views.inline(readingPin);
            registry.fetch(readingPin);
        }

        assertThat(versionsOf(UrnParser.logicalUrn(readingPin))).containsExactly("1.0.0");
        assertThat(rows("artifact_version")).hasSize(2);
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

    /** A reading that composes the station Element by CORE URN, so the views have something to resolve. */
    private JsonNode readingSchema() {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "temperature": { "type": "number" },
                "station": { "$ref": "%s" }
              }
            }
            """.formatted(stationPin));
    }

    /** A pointer that resolves nowhere in the document — one of the faults validation does report. */
    private JsonNode schemaWithDanglingRef() {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "station": { "$ref": "#/$defs/Missing" } }
            }
            """);
    }
}

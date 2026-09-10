package de.civitascore.modelforge.persistence.postgres;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.DisallowSchemaLoader;
import de.civitascore.modelforge.application.ViewService;
import de.civitascore.modelforge.graph.DependencyGraphService;
import de.civitascore.modelforge.validation.JacksonBridge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A bundled view can be consumed without the registry.
 *
 * <p>Structural checks on the bundle cannot establish this on their own: they confirm that a
 * reference has a matching {@code $id} somewhere in the document, not that a validator resolves one
 * to the other. So the bundle is handed to a stock Draft 2020-12 validator configured to refuse
 * every schema load, which is what "without further registry access" means — if a reference had to
 * be fetched, compilation would fail rather than silently succeed.
 */
class BundledViewSelfContainmentDatabaseTest extends AbstractRegistryDatabaseTest {

    /** A validator that knows nothing of the registry and may not load anything. */
    private static final JsonSchemaFactory OFFLINE_VALIDATOR =
        JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder -> builder.schemaLoaders(loaders -> loaders.add(DisallowSchemaLoader.getInstance())));

    private ViewService views;
    private String stationPin;
    private String readingPin;

    @BeforeEach
    void storeAReadingThatComposesAStation() {
        stationPin = registry.storeElement("Station", stationSchema(), Set.of());
        readingPin = registry.storeElement("Reading", readingSchema(), Set.of(stationPin));

        var graph = new DependencyGraphService(registry);
        graph.rebuild();
        views = new ViewService(registry, graph, mapper);
    }

    @Test
    @DisplayName("The bundle embeds its dependency under the identity the reference names")
    void bundleEmbedsTheDependencyUnderTheReferencedIdentity() {
        JsonNode bundle = views.bundle(readingPin).orElseThrow();

        assertThat(bundle.toString())
            .as("the dependency's identity must survive bundling, or no reference can resolve to it")
            .contains(stationPin);
    }

    @Test
    @DisplayName("A stock validator that may load nothing still enforces the embedded dependency")
    void bundleValidatesOfflineAndEnforcesTheDependency() {
        JsonNode bundle = views.bundle(readingPin).orElseThrow();

        JsonSchema compiled = OFFLINE_VALIDATOR.getSchema(JacksonBridge.toJackson2(bundle));

        // A reading whose nested station satisfies the embedded Station schema.
        Set<ValidationMessage> onGoodInstance =
            compiled.validate(JacksonBridge.toJackson2(instance("\"S-1\"")));
        assertThat(onGoodInstance).as("a conforming instance must validate").isEmpty();

        // The same reading with the station's own constraint broken. This only fails if the
        // embedded Station schema is genuinely in force, which is the property under test.
        Set<ValidationMessage> onBadInstance =
            compiled.validate(JacksonBridge.toJackson2(instance("42")));
        assertThat(onBadInstance)
            .as("the embedded dependency's constraints must be enforced without registry access")
            .isNotEmpty();
        // Attribute the failure to the embedded Station schema specifically: an error raised
        // anywhere else would satisfy isNotEmpty() without the reference having resolved.
        assertThat(onBadInstance.toString())
            .as("the violation must be the embedded Station field, not an unrelated error")
            .contains("stationId");
    }

    /** Station constrains its own field, so a violation is attributable to the embedded schema. */
    private JsonNode stationSchema() {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "stationId": { "type": "string" } },
              "required": ["stationId"]
            }
            """);
    }

    private JsonNode readingSchema() {
        return mapper.readTree("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "temperature": { "type": "number" },
                "station": { "$ref": "%s" }
              },
              "required": ["station"]
            }
            """.formatted(stationPin));
    }

    private JsonNode instance(String stationIdLiteral) {
        return mapper.readTree(
            "{\"temperature\": 21.5, \"station\": {\"stationId\": %s}}".formatted(stationIdLiteral));
    }
}

package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The registry's identity and versioning contract against a real database: one logical artifact per
 * logical URN, a new immutable version per content change, earlier versions preserved verbatim, and
 * an identical rewrite absorbed.
 */
class ArtifactVersioningDatabaseTest extends AbstractRegistryDatabaseTest {

    @Test
    @DisplayName("Reusing the returned logical URN updates that artifact instead of creating a second one")
    void reusingLogicalUrnUpdatesTheSameArtifact() {
        String firstPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);

        String secondPin = storeUnder(logical, "humidity", VersionBump.MINOR);

        assertThat(rows("artifact"))
            .as("a second write under the same logical URN must not mint a second logical artifact")
            .hasSize(1);
        assertThat(UrnParser.logicalUrn(secondPin)).isEqualTo(logical);
        assertThat(versionsOf(logical)).containsExactly("1.0.0", "1.1.0");
    }

    @Test
    @DisplayName("A content change adds a version and leaves the earlier version's stored bytes untouched")
    void contentChangeDoesNotOverwriteTheEarlierVersion() {
        String firstPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);
        String originalBytes = storedContent(logical, "1.0.0");

        storeUnder(logical, "humidity", VersionBump.MINOR);

        assertThat(storedContent(logical, "1.0.0"))
            .as("the earlier version's representation must be immutable")
            .isEqualTo(originalBytes);
        assertThat(storedContent(logical, "1.1.0")).contains("humidity").doesNotContain("temperature");
    }

    @Test
    @DisplayName("An earlier version stays readable by its pin after a later version is written")
    void earlierVersionRemainsReadableAfterALaterWrite() {
        String firstPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);

        storeUnder(logical, "humidity", VersionBump.MAJOR);

        JsonNode earlier = registry.fetch(firstPin).orElseThrow();
        assertThat(earlier.path("properties").has("temperature")).isTrue();
        assertThat(earlier.path("properties").has("humidity")).isFalse();
    }

    @Test
    @DisplayName("Re-storing byte-identical content returns the existing pin and adds no version")
    void identicalContentIsAbsorbed() {
        String firstPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(firstPin);
        // Idempotence is keyed on the stored bytes, so the identity has to be carried for the
        // rewrite to be identical: a name-only store mints a fresh disambiguator instead.
        String carriedPin = storeUnder(logical, "temperature", VersionBump.MINOR);

        String rewritePin = storeUnder(logical, "temperature", VersionBump.MINOR);

        assertThat(rewritePin).as("an identical rewrite must resolve to the existing version").isEqualTo(carriedPin);
        assertThat(versionsOf(logical)).containsExactly("1.0.0", "1.1.0");
        assertThat(rows("artifact_representation")).hasSize(2);
    }

    @Test
    @DisplayName("A store that carries no identity mints a new logical artifact rather than revising one")
    void nameOnlyStoreMintsAFreshIdentity() {
        String firstPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String secondPin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());

        assertThat(UrnParser.logicalUrn(secondPin)).isNotEqualTo(UrnParser.logicalUrn(firstPin));
        assertThat(rows("artifact")).hasSize(2);
    }

    @Test
    @DisplayName("The stored representation is the authored document, with no identity stamped into it")
    void storedRepresentationKeepsTheAuthoredDocument() {
        String pin = registry.storeElement("Sensor", schema(null, "temperature"), Set.of());
        String logical = UrnParser.logicalUrn(pin);

        // The logical URN lives in the artifact row, not in the document, which is what leaves the
        // registry free to assign a version.
        assertThat(storedContent(logical, "1.0.0")).doesNotContain("$id");
        assertThat(jdbc.sql("select logical_urn from model_forge.artifact").query(String.class).single())
            .isEqualTo(logical);
    }

    /** Writes under an existing logical URN by carrying it as the schema's {@code $id}. */
    private String storeUnder(String logicalUrn, String property, VersionBump bump) {
        return registry.storeElement(
            "Sensor", schema(logicalUrn, property), Set.of(), Set.of(), null, bump, null);
    }

    /** A minimal JSON Schema, optionally claiming an existing logical identity via {@code $id}. */
    private JsonNode schema(String id, String property) {
        String idLine = id == null ? "" : "\"$id\": \"" + id + "\",";
        return mapper.readTree("""
            {
              %s
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": { "%s": { "type": "number" } }
            }
            """.formatted(idLine, property));
    }
}

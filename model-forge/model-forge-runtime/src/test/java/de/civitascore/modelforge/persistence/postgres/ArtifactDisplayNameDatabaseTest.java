package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.core.port.ArtifactSearchCriteria;
import de.civitascore.modelforge.urn.UrnParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The URN carries no name, so listings, search and the dependency graph label an artifact by the
 * display name the registry records beside it.
 */
class ArtifactDisplayNameDatabaseTest extends AbstractRegistryDatabaseTest {

    @Test
    @DisplayName("A titled document is listed and found by its title")
    void aTitledDocumentIsListedByItsTitle() {
        String logical = UrnParser.logicalUrn(registry.storeElement(element("Station"), Set.of()));

        assertThat(registry.displayNames(List.of(logical))).containsEntry(logical, "Station");
        assertThat(registry.searchArtifacts(
                new ArtifactSearchCriteria("Station", false, "element", null, null, null, 10, 0)))
            .extracting(hit -> hit.logicalId())
            .containsExactly(logical);
    }

    @Test
    @DisplayName("A new version's title renames the artifact")
    void aRetitledVersionRenamesTheArtifact() {
        String logical = UrnParser.logicalUrn(registry.storeElement(element("Station"), Set.of()));

        registry.storeElement(element("Weather station", logical), Set.of(), VersionBump.MINOR);

        assertThat(registry.displayNames(List.of(logical))).containsEntry(logical, "Weather station");
    }

    @Test
    @DisplayName("An untitled artifact is listed by its UUID until it is named")
    void anUntitledArtifactIsListedByItsUuidUntilNamed() {
        String pin = registry.storeMapping(urns.mint("mapping"), mapper.readTree("{\"fields\":{}}"), VersionBump.PATCH);
        String logical = UrnParser.logicalUrn(pin);

        assertThat(registry.displayNames(List.of(logical)))
            .containsEntry(logical, UrnParser.idFromUrn(logical).toString());

        registry.nameArtifact(pin, "Sensor to Observation");

        assertThat(registry.displayNames(List.of(logical))).containsEntry(logical, "Sensor to Observation");
    }

    @Test
    @DisplayName("Only held artifacts have a display name")
    void onlyHeldArtifactsHaveADisplayName() {
        String unknown = UrnParser.logicalUrn(urns.mintElement());

        assertThat(registry.displayNames(List.of(unknown))).isEmpty();
        assertThat(registry.displayNames(List.of())).isEmpty();
    }

    private JsonNode element(String title) {
        return element(title, UrnParser.logicalUrn(urns.mintElement()));
    }

    private JsonNode element(String title, String logicalUrn) {
        return mapper.readTree("""
            {"$schema":"https://json-schema.org/draft/2020-12/schema",
             "$id":"%s","title":"%s","type":"object"}""".formatted(logicalUrn, title));
    }
}

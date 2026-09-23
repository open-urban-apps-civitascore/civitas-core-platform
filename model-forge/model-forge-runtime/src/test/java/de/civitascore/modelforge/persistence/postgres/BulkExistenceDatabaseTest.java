package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.urn.UrnParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asking about many URNs at once answers exactly as asking about each one would.
 *
 * <p>The bulk query re-expresses {@code resolveReference}'s pinned-versus-logical rule in SQL, so
 * the two can drift apart silently — a release gate would then read a present artifact as missing,
 * or the reverse. Every case here compares the two answers rather than asserting the bulk one alone.
 */
class BulkExistenceDatabaseTest extends AbstractRegistryDatabaseTest {

    private String stationV1;
    private String stationV2;
    private String stationLogical;
    private String readingPin;

    @BeforeEach
    void storeTwoArtifactsOneOfThemVersioned() {
        stationV1 = registry.storeElement("Station", schema("stationId"), Set.of());
        stationV2 = registry.storeElement(
            "Station", schema("stationId2"), Set.of(), Set.of(), null, VersionBump.MINOR);
        stationLogical = UrnParser.logicalUrn(stationV1);
        readingPin = registry.storeElement("Reading", schema("temperature"), Set.of());
    }

    /** Both answers for one URN, so a disagreement is the assertion failure. */
    private void assertAgrees(String urn) {
        boolean bulk = registry.heldUrns(List.of(urn)).contains(urn);
        boolean single = registry.resolveReference(urn).isPresent();
        assertThat(bulk).as("bulk and single answer for %s", urn).isEqualTo(single);
    }

    @Test
    @DisplayName("every URN form is answered as resolveReference answers it")
    void theBulkAnswerMatchesResolveReferenceForEveryUrnForm() {
        List<String> probes = List.of(
            stationV1,
            stationV2,
            stationLogical,
            stationLogical + ":latest",
            stationLogical + ":9.9.9",
            readingPin,
            "urn:core:platform:civitas:element:common:Ghost:zzzzzzzzzz",
            "urn:core:platform:civitas:element:common:Ghost:zzzzzzzzzz:1.0.0",
            "not-a-urn-at-all");

        Set<String> held = registry.heldUrns(probes);

        for (String urn : probes) {
            assertThat(held.contains(urn))
                .as("bulk and single answer for %s", urn)
                .isEqualTo(registry.resolveReference(urn).isPresent());
        }
    }

    @Test
    @DisplayName("a pinned URN is held only when that exact version exists")
    void aPinnedUrnNeedsItsOwnVersion() {
        assertThat(registry.heldUrns(List.of(stationV1, stationV2))).containsExactly(stationV1, stationV2);
        assertThat(registry.heldUrns(List.of(stationLogical + ":9.9.9"))).isEmpty();
        assertAgrees(stationLogical + ":9.9.9");
    }

    @Test
    @DisplayName("a logical or latest URN is held while the artifact has any version")
    void aVersionFreeUrnFollowsTheArtifact() {
        assertThat(registry.heldUrns(List.of(stationLogical, stationLogical + ":latest")))
            .containsExactly(stationLogical, stationLogical + ":latest");
        assertAgrees(stationLogical);
        assertAgrees(stationLogical + ":latest");
    }

    @Test
    @DisplayName("the answer carries the caller's own spelling, not the version it resolved to")
    void theAnswerIsVerbatim() {
        String latest = stationLogical + ":latest";

        assertThat(registry.heldUrns(List.of(latest)))
            .as("a caller derives the missing subset by difference, so the spelling must match")
            .containsExactly(latest);
    }

    @Test
    @DisplayName("asking about nothing, or only blanks, reads no store")
    void nothingAskedNothingHeld() {
        assertThat(registry.heldUrns(List.of())).isEmpty();
        assertThat(registry.heldUrns(null)).isEmpty();
        assertThat(registry.heldUrns(List.of("  ", ""))).isEmpty();
    }

    @Test
    @DisplayName("a repeated URN is answered once")
    void duplicatesCollapse() {
        assertThat(registry.heldUrns(List.of(stationV1, stationV1, stationV1)))
            .containsExactly(stationV1);
    }

    @Test
    @DisplayName("an input larger than one statement is answered in full")
    void aBatchWiderThanOneStatementIsAnsweredInFull() {
        List<String> probes = new ArrayList<>(IntStream.range(0, 1200)
            .mapToObj(i -> "urn:core:platform:civitas:element:common:Absent%d:zzzzzzzzzz:1.0.0".formatted(i))
            .toList());
        probes.add(stationV1);
        probes.add(readingPin);
        Collections.shuffle(probes);

        assertThat(registry.heldUrns(probes))
            .as("the chunked reads are unioned, so a real artifact past the first chunk still counts")
            .containsExactlyInAnyOrder(stationV1, readingPin);
    }

    @Test
    @DisplayName("an artifact holding no version is not held")
    void anArtifactWithoutAVersionIsNotHeld() {
        String urn = "urn:core:platform:civitas:element:common:Empty:yyyyyyyyyy";
        // The write path cannot produce a version-less artifact, so insert one directly: it is the
        // state a caller sees if a version write is ever rolled back on its own.
        jdbc.sql("""
                insert into model_forge.artifact (id, logical_urn, artifact_type, name, created_at, updated_at)
                values (gen_random_uuid(), :urn, 'element', 'Empty', now(), now())
                """)
            .param("urn", urn)
            .update();

        assertThat(registry.heldUrns(List.of(urn))).isEmpty();
        assertAgrees(urn);
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
}

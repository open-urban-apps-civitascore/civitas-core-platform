package de.civitascore.modelforge.urn;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the stateless {@link UrnParser}.
 *
 * CORE URN format: urn:core:&lt;scope&gt;:&lt;owner&gt;:&lt;artifact-type&gt;:&lt;domain&gt;:&lt;uuid&gt;[:&lt;version&gt;]
 */
class UrnParserTest {

    private static final UUID ID = UUID.fromString("b75dba9f-a9f2-8b7d-a9e1-f800ff5378f1");
    private static final String VERSIONED =
            "urn:core:platform:civitas:element:common:b75dba9f-a9f2-8b7d-a9e1-f800ff5378f1:1.0.0";
    private static final String LOGICAL =
            "urn:core:platform:civitas:element:common:b75dba9f-a9f2-8b7d-a9e1-f800ff5378f1";
    private static final String LATEST_REF =
            "urn:core:platform:civitas:element:common:b75dba9f-a9f2-8b7d-a9e1-f800ff5378f1:latest";

    // ── requireNoControlChars ─────────────────────────────────────────────────

    @Test
    void requireNoControlChars_acceptsNormalUrnsAndNull() {
        assertThatCode(() -> UrnParser.requireNoControlChars(VERSIONED)).doesNotThrowAnyException();
        assertThatCode(() -> UrnParser.requireNoControlChars(LOGICAL)).doesNotThrowAnyException();
        assertThatCode(() -> UrnParser.requireNoControlChars(null)).doesNotThrowAnyException();
    }

    @Test
    void requireNoControlChars_rejectsCrlfInjection() {
        // CRLF in a URN forwarded as an HTTP header would allow header splitting
        assertThatThrownBy(() -> UrnParser.requireNoControlChars(
                LOGICAL + "\r\nX-Injected: evil"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("control characters");
    }

    @Test
    void requireNoControlChars_rejectsOtherControlCharacters() {
        char nul = (char) 0x00;
        char del = (char) 0x7F;
        assertThatThrownBy(() -> UrnParser.requireNoControlChars(LOGICAL + "\t"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrnParser.requireNoControlChars(LOGICAL + nul))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrnParser.requireNoControlChars(LOGICAL + del))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireNoControlChars_acceptsSpace_onlyControlCharsAreRejected() {
        // Pins current behaviour: space (0x20) is not a control character and passes.
        assertThatCode(() -> UrnParser.requireNoControlChars(
                LOGICAL + " trailing"))
            .doesNotThrowAnyException();
    }

    // ── idFromUrn / versionFromUrn ────────────────────────────────────────────

    @Test
    void idFromUrn_extractsUuidSegment() {
        assertThat(UrnParser.idFromUrn(VERSIONED)).isEqualTo(ID);
        assertThat(UrnParser.idFromUrn(LOGICAL)).isEqualTo(ID);
        assertThat(UrnParser.idFromUrn(LATEST_REF)).isEqualTo(ID);
    }

    @Test
    void idFromUrn_nonUrnOrTooShort_returnsNull() {
        assertThat(UrnParser.idFromUrn("plain-id")).isNull();
        assertThat(UrnParser.idFromUrn("urn:core:too:short")).isNull();
        assertThat(UrnParser.idFromUrn(null)).isNull();
    }

    @Test
    void versionFromUrn_versionedUrn_returnsVersion() {
        assertThat(UrnParser.versionFromUrn(VERSIONED)).isEqualTo("1.0.0");
    }

    @Test
    void versionFromUrn_logicalUrnOrNonUrn_returnsNull() {
        assertThat(UrnParser.versionFromUrn(LOGICAL)).isNull();
        assertThat(UrnParser.versionFromUrn("plain-id")).isNull();
        assertThat(UrnParser.versionFromUrn(null)).isNull();
    }

    // ── latest reference token ────────────────────────────────────────────────

    @Test
    void isLatest_trueOnlyForLatestVersionSegment() {
        assertThat(UrnParser.isLatest(LATEST_REF)).isTrue();
        assertThat(UrnParser.isLatest(VERSIONED)).isFalse();
        assertThat(UrnParser.isLatest(LOGICAL)).isFalse();
        assertThat(UrnParser.isLatest("plain-id")).isFalse();
        assertThat(UrnParser.isLatest(null)).isFalse();
    }

    @Test
    void latestToken_behavesAsAnOrdinaryVersionSegment() {
        // ':latest' is parsed like any version segment; resolution happens at the registry layer.
        assertThat(UrnParser.LATEST).isEqualTo("latest");
        assertThat(UrnParser.versionFromUrn(LATEST_REF)).isEqualTo("latest");
        assertThat(UrnParser.logicalUrn(LATEST_REF)).isEqualTo(LOGICAL);
    }

    // ── logicalUrn / withVersion round-trips ──────────────────────────────────

    @Test
    void logicalUrn_stripsVersionSegment() {
        assertThat(UrnParser.logicalUrn(VERSIONED)).isEqualTo(LOGICAL);
    }

    @Test
    void logicalUrn_alreadyLogicalOrNonUrn_returnsInputUnchanged() {
        assertThat(UrnParser.logicalUrn(LOGICAL)).isEqualTo(LOGICAL);
        assertThat(UrnParser.logicalUrn("plain-id")).isEqualTo("plain-id");
    }

    @Test
    void withVersion_appendsVersionToLogicalUrn() {
        assertThat(UrnParser.withVersion(LOGICAL, "2.0.0")).isEqualTo(LOGICAL + ":2.0.0");
    }

    @Test
    void withVersion_alreadyVersionedUrn_returnsInputUnchanged() {
        assertThat(UrnParser.withVersion(VERSIONED, "2.0.0")).isEqualTo(VERSIONED);
    }

    @Test
    void logicalUrnAndWithVersion_roundTripRestoresVersionedUrn() {
        String logical = UrnParser.logicalUrn(VERSIONED);
        String version = UrnParser.versionFromUrn(VERSIONED);

        assertThat(UrnParser.withVersion(logical, version)).isEqualTo(VERSIONED);
        assertThat(UrnParser.idFromUrn(logical)).isEqualTo(UrnParser.idFromUrn(VERSIONED));
    }

    @Test
    void withArtifactType_swapsOnlyTheTypeSegment() {
        assertThat(UrnParser.withArtifactType(VERSIONED, "datastructure"))
            .isEqualTo("urn:core:platform:civitas:datastructure:common:" + ID + ":1.0.0");
        assertThat(UrnParser.withArtifactType("plain-id", "datastructure")).isNull();
    }

    // ── isUrn ─────────────────────────────────────────────────────────────────

    @Test
    void isUrn_edgeCases() {
        assertThat(UrnParser.isUrn(null)).isFalse();
        assertThat(UrnParser.isUrn("")).isFalse();
        assertThat(UrnParser.isUrn("urn:other:platform:civitas:element:common:X")).isFalse();
        assertThat(UrnParser.isUrn("URN:CORE:platform")).isFalse();  // prefix match is case-sensitive
        assertThat(UrnParser.isUrn("urn:core:")).isFalse();          // prefix alone is not enough
        assertThat(UrnParser.isUrn(VERSIONED)).isTrue();
        assertThat(UrnParser.isUrn(LOGICAL)).isTrue();
    }

    @Test
    void isUrn_rejectsWrongSegmentCounts() {
        // Too few segments: a caller-supplied $id that merely starts with the prefix must not be
        // trusted as an authoritative identity — this is exactly what let a malformed 4-segment
        // "urn:core:datastructure:Model1" flow through as a caller-owned URN and corrupt every
        // downstream operation (null version, double-nested "…:Model1:1.0.0:1.0.0" re-imports).
        assertThat(UrnParser.isUrn("urn:core:too:short")).isFalse();
        assertThat(UrnParser.isUrn("urn:core:datastructure:Model1")).isFalse();

        // Too many segments: e.g. a full URN accidentally embedded in another segment.
        assertThat(UrnParser.isUrn(VERSIONED + ":extra")).isFalse();
    }

    @Test
    void isUrn_rejectsEmptySegments() {
        // A trailing colon is the dangerous case: the default split(":") discards trailing empty
        // segments, so the count looked right and withVersion then produced a double colon.
        assertThat(UrnParser.isUrn(LOGICAL + ":")).isFalse();
        assertThat(UrnParser.withVersion(LOGICAL, "2.0.0")).doesNotContain("::");

        // An empty interior segment carries no identity either.
        assertThat(UrnParser.isUrn("urn:core:platform:civitas:element::" + ID)).isFalse();
        assertThat(UrnParser.isUrn("urn:core::civitas:element:common:" + ID)).isFalse();
    }

    @Test
    void isUrn_rejectsANameSegmentInPlaceOfTheUuid() {
        // A name in the identity would break every reference to an artifact on its first rename.
        String nameBased = "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx";
        assertThat(UrnParser.isUrn(nameBased)).isFalse();
        assertThat(UrnParser.isUrn(nameBased + ":1.0.0")).isFalse();
    }

    @Test
    void isUrn_requiresALowercaseCanonicalUuid() {
        assertThat(UrnParser.isUrn("urn:core:platform:civitas:element:common:" + ID.toString().toUpperCase()))
            .isFalse();
        assertThat(UrnParser.isUrn("urn:core:platform:civitas:element:common:b75dba9fa9f28b7da9e1f800ff5378f1"))
            .isFalse();
    }

    @Test
    void isUrn_acceptsSemVerTwoSegmentAndLatestVersions_rejectsOthers() {
        assertThat(UrnParser.isUrn(LOGICAL + ":6.0")).isTrue();
        assertThat(UrnParser.isUrn(LOGICAL + ":1.0.0-rc.1")).isTrue();
        assertThat(UrnParser.isUrn(LATEST_REF)).isTrue();
        assertThat(UrnParser.isUrn(LOGICAL + ":GeoPoint")).isFalse();
        assertThat(UrnParser.isUrn(LOGICAL + ":newest")).isFalse();
    }

    @Test
    void requireUrn_rejectsANonUrnNamingTheExpectedFormat() {
        assertThatThrownBy(() -> UrnParser.requireUrn("plain-id", "Element id"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Element id 'plain-id'")
            .hasMessageContaining("<uuid>");
        assertThat(UrnParser.requireUrn(VERSIONED, "Element id")).isEqualTo(VERSIONED);
    }

    // ── Derived identities ────────────────────────────────────────────────────

    @Test
    void deriveId_isDeterministicAndKeySensitive() {
        UUID a = UrnParser.deriveId("urn:xoev-de:xmeld:Meldeanschrift");
        assertThat(a).isEqualTo(UrnParser.deriveId("urn:xoev-de:xmeld:Meldeanschrift"));
        assertThat(UrnParser.deriveId("something-else")).isNotEqualTo(a);
    }

    @Test
    void deriveId_isANameBasedUuidV8() {
        UUID id = UrnParser.deriveId("urn:xoev-de:xmeld:Meldeanschrift");
        assertThat(id.version()).isEqualTo(8);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void memberId_isStablePerParentAndKey_andDistinctOtherwise() {
        UUID parent = UUID.fromString("0b6f5c1e-8a4d-4f7b-9c2e-3d1a5b7c9e20");
        UUID other = UUID.fromString("1c7a6d2f-9b5e-4a8c-8d3f-4e2b6c8d0f31");

        assertThat(UrnParser.memberId(parent, "Sensor")).isEqualTo(UrnParser.memberId(parent, "Sensor"));
        assertThat(UrnParser.memberId(parent, "Sensor")).isNotEqualTo(UrnParser.memberId(parent, "Reading"));
        assertThat(UrnParser.memberId(parent, "Sensor")).isNotEqualTo(UrnParser.memberId(other, "Sensor"));
        assertThat(UrnParser.memberId(parent, "Sensor")).isNotEqualTo(parent);
    }
}

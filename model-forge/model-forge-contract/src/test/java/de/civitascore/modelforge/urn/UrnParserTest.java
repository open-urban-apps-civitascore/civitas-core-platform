package de.civitascore.modelforge.urn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the stateless {@link UrnParser}.
 *
 * CORE URN format: urn:core:&lt;scope&gt;:&lt;owner&gt;:&lt;model_forge.artifact-type&gt;:&lt;domain&gt;:&lt;name&gt;[:&lt;version&gt;]
 */
class UrnParserTest {

    private static final String VERSIONED =
            "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx:1.0.0";
    private static final String LOGICAL =
            "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx";
    private static final String LATEST_REF =
            "urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx:latest";

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
                "urn:core:platform:civitas:element:common:Geo Point"))
            .doesNotThrowAnyException();
    }

    // ── nameFromUrn / versionFromUrn ──────────────────────────────────────────

    @Test
    void nameFromUrn_extractsNameSegment() {
        assertThat(UrnParser.nameFromUrn(VERSIONED)).isEqualTo("GeoPoint");
        assertThat(UrnParser.nameFromUrn(LOGICAL)).isEqualTo("GeoPoint");
    }

    @Test
    void nameFromUrn_nonUrnOrTooShort_returnsInputUnchanged() {
        assertThat(UrnParser.nameFromUrn("plain-id")).isEqualTo("plain-id");
        assertThat(UrnParser.nameFromUrn("urn:core:too:short")).isEqualTo("urn:core:too:short");
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
        assertThat(UrnParser.nameFromUrn(LATEST_REF)).isEqualTo("GeoPoint");
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
        assertThat(UrnParser.nameFromUrn(logical)).isEqualTo(UrnParser.nameFromUrn(VERSIONED));
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

        // Too many segments: e.g. a full URN accidentally embedded in a name segment.
        assertThat(UrnParser.isUrn(VERSIONED + ":extra")).isFalse();
    }

    // ── Disambiguator segment ─────────────────────────────────────────────────

    @Test
    void disambiguator_extractedFromItsOwnSegment() {
        assertThat(UrnParser.disambiguatorFromUrn(VERSIONED)).isEqualTo("k3f9a2b7qx");
        assertThat(UrnParser.disambiguatorFromUrn(LOGICAL)).isEqualTo("k3f9a2b7qx");
        assertThat(UrnParser.nameFromUrn(VERSIONED)).isEqualTo("GeoPoint");   // name stays clean
        assertThat(UrnParser.disambiguatorFromUrn("plain-id")).isNull();
    }

    @Test
    void deriveDisambiguator_isDeterministicAndUrnSafe() {
        String a = UrnParser.deriveDisambiguator("urn:xoev-de:xmeld:Meldeanschrift");
        assertThat(a).isEqualTo(UrnParser.deriveDisambiguator("urn:xoev-de:xmeld:Meldeanschrift"));
        assertThat(a).hasSize(10).matches("[0-9a-z]{10}");
        assertThat(UrnParser.deriveDisambiguator("something-else")).isNotEqualTo(a);
    }
}

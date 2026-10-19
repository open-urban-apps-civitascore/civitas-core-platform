package de.civitascore.modelforge.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the shared name/title relevance scorer (migrated from the Element search). */
class NameMatchTest {

    @Test
    void ranksExactThenPrefixThenSubstring() {
        assertThat(NameMatch.score("Address", "address", false)).isEqualTo(100);
        assertThat(NameMatch.score("GeoPoint", "geo", false)).isEqualTo(80);
        assertThat(NameMatch.score("SensorReading", "reading", false)).isEqualTo(60);
        assertThat(NameMatch.score("PersonAddress", "address", false)).isEqualTo(60);
    }

    @Test
    void caseInsensitive() {
        assertThat(NameMatch.score("GeoPoint", "GEOPOINT", false)).isEqualTo(100);
    }

    @Test
    void noMatchIsZero() {
        assertThat(NameMatch.score("GeoPoint", "xyzzy", false)).isZero();
    }

    @Test
    void fuzzyOnlyWhenEnabled() {
        // typo via edit distance
        assertThat(NameMatch.score("Observation", "obzervation", false)).isZero();
        assertThat(NameMatch.score("Observation", "obzervation", true)).isGreaterThan(0);
        // subsequence
        assertThat(NameMatch.score("SensorReading", "snsr", false)).isZero();
        assertThat(NameMatch.score("SensorReading", "snsr", true)).isEqualTo(40);
    }

    @Test
    void exactOutranksFuzzyCandidates() {
        assertThat(NameMatch.score("Person", "person", true)).isEqualTo(100);
        assertThat(NameMatch.score("PersonAddress", "person", true)).isEqualTo(80);
    }

    @Test
    void nullOrBlankIsZero() {
        assertThat(NameMatch.score(null, "x", true)).isZero();
        assertThat(NameMatch.score("X", null, true)).isZero();
        assertThat(NameMatch.score("X", "", true)).isZero();
    }
}

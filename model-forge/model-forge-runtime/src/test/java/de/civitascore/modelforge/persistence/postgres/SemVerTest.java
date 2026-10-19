package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for backend SemVer arithmetic. {@code SemVer} lives in the JaCoCo-excluded
 * registry package (IT-covered for the happy path); these no-Docker tests pin the documented
 * edge cases (legacy two-segment input, blank/null, non-numeric and negative segments).
 */
class SemVerTest {

    @Test
    void bumpsEachSegment() {
        assertThat(SemVer.next("1.2.3", VersionBump.PATCH)).isEqualTo("1.2.4");
        assertThat(SemVer.next("1.2.3", VersionBump.MINOR)).isEqualTo("1.3.0");
        assertThat(SemVer.next("1.2.3", VersionBump.MAJOR)).isEqualTo("2.0.0");
    }

    @Test
    void twoSegmentLegacyInputBumpsCleanly() {
        assertThat(SemVer.next("1.0", VersionBump.PATCH)).isEqualTo("1.0.1");
        assertThat(SemVer.next("1.0", VersionBump.MINOR)).isEqualTo("1.1.0");
    }

    @Test
    void blankOrNullBumpsFromZero() {
        assertThat(SemVer.next(null, VersionBump.PATCH)).isEqualTo("0.0.1");
        assertThat(SemVer.next("", VersionBump.MINOR)).isEqualTo("0.1.0");
        assertThat(SemVer.next("   ", VersionBump.MAJOR)).isEqualTo("1.0.0");
    }

    @Test
    void nonNumericSegmentTreatedAsZero() {
        assertThat(SemVer.next("1.x.3", VersionBump.MINOR)).isEqualTo("1.1.0");
        assertThat(SemVer.next("abc", VersionBump.PATCH)).isEqualTo("0.0.1");
    }

    @Test
    void negativeSegmentClampedToZero() {
        assertThat(SemVer.next("-5.2.3", VersionBump.MAJOR)).isEqualTo("1.0.0");
    }

    @Test
    void initialIsOneZeroZero() {
        assertThat(SemVer.INITIAL).isEqualTo("1.0.0");
    }

    @Test
    void compareOrdersNumericallyNotLexicographically() {
        // Guards numeric version ordering: a text sort would rank 1.10.0 below 1.2.0.
        assertThat(SemVer.compare("1.10.0", "1.2.0")).isPositive();
        assertThat(SemVer.compare("2.0.0", "1.0.1")).isPositive();
        assertThat(SemVer.compare("1.0.0", "1.0.1")).isNegative();
        assertThat(SemVer.compare("1.0.0", "1.0.0")).isZero();
    }

    @Test
    void compareTreatsMissingOrNonNumericSegmentsAsZero() {
        assertThat(SemVer.compare("1.0", "1.0.0")).isZero();
        assertThat(SemVer.compare("1.0.1", "1.0")).isPositive();
        assertThat(SemVer.compare(null, "0.0.0")).isZero();
        assertThat(SemVer.compare("x.y.z", "0.0.0")).isZero();
    }
}

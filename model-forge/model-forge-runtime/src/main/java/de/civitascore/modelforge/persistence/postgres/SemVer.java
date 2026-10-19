package de.civitascore.modelforge.persistence.postgres;

import de.civitascore.modelforge.contract.VersionBump;

/**
 * SemVer arithmetic for backend-owned versioning.
 *
 * <pre>
 *   1.2.3 + patch -> 1.2.4
 *   1.2.3 + minor -> 1.3.0
 *   1.2.3 + major -> 2.0.0
 * </pre>
 *
 * The first version of a newly created artifact is {@link #INITIAL} ({@code 1.0.0}).
 */
final class SemVer {

    /** Version assigned to the first version of a newly created artifact. */
    static final String INITIAL = "1.0.0";

    private SemVer() {}

    /**
     * Computes the next version from {@code current} according to {@code bump}.
     *
     * <p>Missing or non-numeric segments are treated as {@code 0}, so a two-segment
     * value such as {@code "1.0"} (the legacy DataSet default) bumps cleanly. A blank
     * or unparseable {@code current} bumps from {@code 0.0.0}.
     */
    static String next(String current, VersionBump bump) {
        int[] v = parse(current);
        return switch (bump) {
            case PATCH -> v[0] + "." + v[1] + "." + (v[2] + 1);
            case MINOR -> v[0] + "." + (v[1] + 1) + ".0";
            case MAJOR -> (v[0] + 1) + ".0.0";
        };
    }

    /**
     * Numeric SemVer ordering of {@code a} against {@code b} (major, then minor, then patch);
     * absent/non-numeric segments count as {@code 0}. Returns a negative, zero or positive value
     * as {@code a} is lower than, equal to, or higher than {@code b} — usable as a
     * {@link java.util.Comparator}.
     */
    static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(x[i], y[i]);
            if (c != 0) return c;
        }
        return 0;
    }

    /** {@code [major, minor, patch]}; absent/non-numeric segments become {@code 0}. */
    private static int[] parse(String version) {
        int[] out = new int[3];
        if (version == null || version.isBlank()) return out;
        String[] parts = version.trim().split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Math.max(0, Integer.parseInt(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                out[i] = 0;
            }
        }
        return out;
    }
}

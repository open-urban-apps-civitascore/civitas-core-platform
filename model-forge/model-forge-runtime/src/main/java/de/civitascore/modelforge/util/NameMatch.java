package de.civitascore.modelforge.util;

import java.util.Locale;

/**
 * Relevance scoring for name/title search, shared by the cross-artifact search.
 *
 * <p>Ranks <em>exact (100) &gt; prefix (80) &gt; substring (60)</em>; with {@code fuzzy} it also
 * matches a subsequence (40, e.g. {@code "snsr" → "Sensor"}) and close edit-distance candidates
 * (typo-tolerant, ≥ 60% similarity, scaled up to ~30). Comparison is case-insensitive; {@code 0}
 * means "no match".
 */
public final class NameMatch {

    private NameMatch() {}

    /** Relevance of {@code candidate} for {@code query} (0 = no match); see the class doc for the scale. */
    public static int score(String candidate, String query, boolean fuzzy) {
        if (candidate == null || query == null) {
            return 0;
        }
        String name = candidate.toLowerCase(Locale.ROOT);
        String q = query.toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return 0;
        }
        if (name.equals(q)) {
            return 100;
        }
        if (name.startsWith(q)) {
            return 80;
        }
        if (name.contains(q)) {
            return 60;
        }
        if (!fuzzy) {
            return 0;
        }
        if (isSubsequence(q, name)) {
            return 40;
        }
        int max = Math.max(q.length(), name.length());
        if (max == 0) {
            return 0;
        }
        double similarity = 1.0 - (double) levenshtein(q, name) / max;
        return similarity >= 0.6 ? (int) Math.round(similarity * 30) : 0;
    }

    /** True if every character of {@code q} occurs in {@code name} in order. */
    private static boolean isSubsequence(String q, String name) {
        int i = 0;
        for (int j = 0; i < q.length() && j < name.length(); j++) {
            if (q.charAt(i) == name.charAt(j)) {
                i++;
            }
        }
        return i == q.length();
    }

    /** Classic Levenshtein edit distance (two-row dynamic programming). */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}

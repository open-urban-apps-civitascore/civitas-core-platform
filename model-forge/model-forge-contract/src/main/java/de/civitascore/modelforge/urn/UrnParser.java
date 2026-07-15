package de.civitascore.modelforge.urn;

import java.util.Arrays;

/**
 * Stateless utility class for parsing CORE URNs.
 *
 * <p>Contains pure functions that extract segments from an existing URN string.
 * None of these methods require Spring configuration or bean state — they work
 * on any string that follows the CORE URN format:
 *
 * <pre>{@code
 *   urn:core:<scope>:<owner>:<artifact-type>:<domain>:<name>:<disambiguator>[:<version>]
 * }</pre>
 *
 * <p>For generating new URNs use {@code UrnService}, which reads the
 * scope/owner/domain configuration from application properties when provided by
 * the Spring Boot starter.
 */
public final class UrnParser {

    /**
     * The version token that references an artifact's latest (highest-SemVer) version.
     * Valid only as a <em>reference</em> target / read id — never as the identity of a written
     * artifact, whose version is always a concrete SemVer.
     */
    public static final String LATEST = "latest";

    private UrnParser() {}

    /**
     * Segment count of a logical CORE URN:
     * {@code urn:core:<scope>:<owner>:<type>:<domain>:<name>:<disambiguator>}.
     */
    private static final int LOGICAL_SEGMENTS = 8;

    /** Segment count of a versioned CORE URN: logical + {@code :<version>}. */
    private static final int VERSIONED_SEGMENTS = 9;

    /**
     * Returns {@code true} if the string is a well-formed CORE URN: the {@code urn:core:} prefix
     * plus exactly the 8 (logical) or 9 (versioned) colon-separated segments the format requires.
     *
     * <p>A prefix match alone is not enough — callers throughout Model Forge use {@code isUrn} to
     * decide whether a caller-supplied {@code $id}/{@code artifactId} is an authoritative identity
     * to keep as-is, or a plain candidate to sanitize and mint a real URN from. A string that only
     * happens to start with {@code urn:core:} (too few or too many segments) must fall through to
     * minting instead of being trusted verbatim — otherwise it silently corrupts every URN
     * operation downstream (segment-index extraction, version parsing, logical/versioned
     * round-tripping).
     */
    public static boolean isUrn(String id) {
        if (id == null || !id.startsWith("urn:core:")) return false;
        int segments = id.split(":").length;
        return segments == LOGICAL_SEGMENTS || segments == VERSIONED_SEGMENTS;
    }

    /**
     * Rejects URN strings that contain ASCII control characters (0x00–0x1F, 0x7F).
     *
     * <p>Prevents injection when the URN is used in SQL, log lines, or URL path segments.
     *
     * @throws IllegalArgumentException if {@code urn} contains control characters
     */
    public static void requireNoControlChars(String urn) {
        if (urn == null) return;
        for (int i = 0; i < urn.length(); i++) {
            char c = urn.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException(
                    "URN contains invalid control characters at position " + i);
            }
        }
    }

    /**
     * Extracts the name segment (index 6) from a versioned or logical CORE URN — the readable
     * display slug, without the disambiguator. Returns the input string unchanged if it is not a
     * CORE URN.
     */
    public static String nameFromUrn(String urn) {
        if (!isUrn(urn)) return urn;
        String[] parts = urn.split(":");
        return parts.length >= 7 ? parts[6] : urn;
    }

    /**
     * Extracts the disambiguator segment (index 7) — the short token that keeps artifacts with an
     * equal display name from colliding onto the same logical URN. Returns {@code null} if the
     * input is not a CORE URN.
     */
    public static String disambiguatorFromUrn(String urn) {
        if (!isUrn(urn)) return null;
        String[] parts = urn.split(":");
        return parts.length >= 8 ? parts[7] : null;
    }

    /**
     * Extracts the version segment (index 8) from a versioned CORE URN.
     * Returns {@code null} if the URN has no version segment.
     */
    public static String versionFromUrn(String urn) {
        if (!isUrn(urn)) return null;
        String[] parts = urn.split(":");
        return parts.length >= 9 ? parts[8] : null;
    }

    /**
     * Whether the URN's version segment is the {@code latest} reference token
     * ({@code urn:core:…:Foo:latest}). {@code latest} tracks the target's current
     * (highest-SemVer) version and is resolved at read time.
     */
    public static boolean isLatest(String urn) {
        return LATEST.equals(versionFromUrn(urn));
    }

    /**
     * Extracts the artifact-type segment (index 4) from a CORE URN.
     * Returns {@code null} if the input is not a well-formed CORE URN.
     */
    public static String artifactTypeFromUrn(String urn) {
        if (!isUrn(urn)) return null;
        String[] parts = urn.split(":");
        return parts.length >= 5 ? parts[4] : null;
    }

    /**
     * Returns the logical (version-free) form of a URN used as Registry ArtifactId.
     *
     * <pre>{@code
     *   urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx:1.0.0
     *     → urn:core:platform:civitas:element:common:GeoPoint:k3f9a2b7qx
     * }</pre>
     *
     * If the URN has no version segment it is returned unchanged.
     */
    public static String logicalUrn(String urn) {
        if (!isUrn(urn)) return urn;
        String[] parts = urn.split(":");
        return parts.length >= 9
            ? String.join(":", Arrays.copyOf(parts, parts.length - 1))
            : urn;
    }

    /**
     * Appends a version to a logical URN.
     *
     * <pre>{@code
     *   urn:core:...:GeoPoint:k3f9a2b7qx  +  "2.0.0"  →  ...:GeoPoint:k3f9a2b7qx:2.0.0
     * }</pre>
     *
     * If the URN already carries a version it is returned unchanged.
     */
    public static String withVersion(String logicalUrn, String version) {
        if (versionFromUrn(logicalUrn) != null) return logicalUrn;
        return logicalUrn + ":" + version;
    }

    /**
     * Canonical slug-sanitiser for CORE URN name segments.
     *
     * <p>Rules applied in order:
     * <ol>
     *   <li>Null or blank input → {@code "unknown"}
     *   <li>Trim whitespace
     *   <li>Replace every char outside {@code [a-zA-Z0-9._-]} with {@code "-"}
     *   <li>Collapse consecutive hyphens to one
     * </ol>
     *
     * Dots are preserved so that names like {@code xinneres.xmeld} remain intact
     * as URN name segments.
     *
     * <p>Case is intentionally preserved so that URN name segments derived from
     * schema titles (e.g. {@code "GeoPoint"}) remain stable across invocations.
     * Callers that require lowercase (e.g. domain identifiers from XRepository)
     * should call {@code .toLowerCase()} on the result themselves.
     *
     * @param name raw candidate string (e.g. schema title, domain identifier)
     * @return safe URN slug, never null or blank
     */
    public static String sanitize(String name) {
        if (name == null || name.isBlank()) return "unknown";
        return name.trim()
                   .replaceAll("[^a-zA-Z0-9._-]", "-")
                   .replaceAll("-{2,}", "-");
    }

    /** base36 alphabet for disambiguator tokens — URN-segment-safe, readable in logs. */
    static final char[] DISAMBIGUATOR_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    /** Disambiguator length; 10 base36 chars ≈ 51 bits — collision-free within a name bucket. */
    static final int DISAMBIGUATOR_LENGTH = 10;

    /**
     * A deterministic disambiguator token for an externally-identified artifact: the same
     * {@code key} always yields the same token, so re-importing the same external standard
     * resolves to the same URN instead of minting a duplicate. Model-Forge-derived identities
     * use a fresh random token instead (see {@code UrnService#mintDisambiguator}).
     */
    public static String deriveDisambiguator(String key) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest((key == null ? "" : key).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            char[] out = new char[DISAMBIGUATOR_LENGTH];
            for (int i = 0; i < out.length; i++) {
                out[i] = DISAMBIGUATOR_ALPHABET[(hash[i] & 0xFF) % DISAMBIGUATOR_ALPHABET.length];
            }
            return new String(out);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

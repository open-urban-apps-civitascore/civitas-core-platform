package de.civitascore.modelforge.urn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stateless utility class for parsing CORE URNs.
 *
 * <p>Contains pure functions that extract segments from an existing URN string.
 * None of these methods require Spring configuration or bean state — they work
 * on any string that follows the CORE URN format:
 *
 * <pre>{@code
 *   urn:core:<scope>:<owner>:<artifact-type>:<domain>:<uuid>[:<version>]
 * }</pre>
 *
 * <p>The {@code uuid} is the artifact's identity. The URN carries no display name, so renaming an
 * artifact never changes the identifier other artifacts reference it by.
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

    /** The namespace {@link #deriveId(String)} derives external identities under. */
    static final UUID CORE_NAMESPACE = UUID.fromString("5a0d5e1c-7b7e-4c43-9a8e-1f3f6c2b9d40");

    /** Segment count of a logical CORE URN: {@code urn:core:<scope>:<owner>:<type>:<domain>:<uuid>}. */
    private static final int LOGICAL_SEGMENTS = 7;

    private static final int TYPE_INDEX = 4;
    private static final int ID_INDEX = 6;
    private static final int VERSION_INDEX = 7;

    private static final Pattern ID =
        Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** XÖV standards use two-segment versions (6.0, 5.5), so the minor and patch are optional. */
    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){0,2}([-+][0-9A-Za-z.-]+)?");

    private UrnParser() {}

    /**
     * Returns {@code true} if the string is a well-formed CORE URN: the {@code urn:core:} prefix,
     * four non-empty segments, a lowercase canonical UUID and, optionally, a version or
     * {@link #LATEST}.
     *
     * <p>Callers throughout Model Forge use {@code isUrn} to decide whether a caller-supplied
     * {@code $id}/{@code artifactId} is an authoritative identity to keep as-is, or has to be
     * replaced by a minted one. A string that only starts with {@code urn:core:} must not be
     * trusted verbatim: segment extraction, version parsing and logical/versioned round-tripping
     * all rely on the shape.
     */
    public static boolean isUrn(String id) {
        return segments(id) != null;
    }

    /**
     * The identity an artifact is written under, or {@link IllegalArgumentException} when {@code urn}
     * is not a CORE URN.
     *
     * <p>A write is the last point where a malformed identity can still be refused cheaply. Past it
     * the value becomes the artifact's {@code logical_urn}, and {@link #logicalUrn} returns a non-URN
     * unchanged — so a malformed value persists as an identity that no URN navigation resolves, and
     * the mistake surfaces far from where it was made.
     *
     * @return {@code urn}, so a caller can guard and assign in one expression
     */
    public static String requireUrn(String urn, String what) {
        if (!isUrn(urn)) {
            throw new IllegalArgumentException(
                what + " '" + urn + "' is not a CORE URN (expected "
                    + "urn:core:<scope>:<owner>:<type>:<domain>:<uuid>"
                    + " with an optional :<version>).");
        }
        return urn;
    }

    /**
     * Whether {@code version} is a concrete version a CORE URN can carry: one to three numeric
     * segments plus an optional SemVer pre-release/build suffix. A value that passes can be appended
     * as the version segment without adding segments. {@link #LATEST} is a reference token, not a
     * version.
     */
    public static boolean isVersion(String version) {
        return version != null && VERSION.matcher(version).matches();
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

    /** The artifact's UUID, or {@code null} if the input is not a CORE URN. */
    public static UUID idFromUrn(String urn) {
        String[] parts = segments(urn);
        return parts == null ? null : UUID.fromString(parts[ID_INDEX]);
    }

    /**
     * The version segment of a versioned CORE URN, a SemVer or {@link #LATEST}.
     * Returns {@code null} if the URN has no version segment or is not a CORE URN.
     */
    public static String versionFromUrn(String urn) {
        String[] parts = segments(urn);
        return parts == null || parts.length == LOGICAL_SEGMENTS ? null : parts[VERSION_INDEX];
    }

    /**
     * Whether the URN's version segment is the {@code latest} reference token
     * ({@code urn:core:…:<uuid>:latest}). {@code latest} tracks the target's current
     * (highest-SemVer) version and is resolved at read time.
     */
    public static boolean isLatest(String urn) {
        return LATEST.equals(versionFromUrn(urn));
    }

    /**
     * Extracts the artifact-type segment from a CORE URN.
     * Returns {@code null} if the input is not a well-formed CORE URN.
     */
    public static String artifactTypeFromUrn(String urn) {
        String[] parts = segments(urn);
        return parts == null ? null : parts[TYPE_INDEX];
    }

    /**
     * Returns the logical (version-free) form of a URN used as Registry ArtifactId.
     *
     * <pre>{@code
     *   urn:core:platform:civitas:element:common:0b6f…e2:1.0.0
     *     → urn:core:platform:civitas:element:common:0b6f…e2
     * }</pre>
     *
     * A URN without a version segment, and a string that is not a CORE URN, is returned unchanged.
     */
    public static String logicalUrn(String urn) {
        String[] parts = segments(urn);
        return parts == null || parts.length == LOGICAL_SEGMENTS
            ? urn
            : String.join(":", Arrays.copyOf(parts, LOGICAL_SEGMENTS));
    }

    /**
     * Appends a version to a logical URN.
     *
     * <pre>{@code
     *   urn:core:…:0b6f…e2  +  "2.0.0"  →  urn:core:…:0b6f…e2:2.0.0
     * }</pre>
     *
     * If the URN already carries a version it is returned unchanged.
     */
    public static String withVersion(String logicalUrn, String version) {
        if (versionFromUrn(logicalUrn) != null) return logicalUrn;
        return logicalUrn + ":" + version;
    }

    /**
     * The same CORE URN with another artifact type, or {@code null} when {@code urn} is not a CORE
     * URN. A DataStructure grouping and the root Element it was built from share their UUID, so
     * this navigates between the two.
     */
    public static String withArtifactType(String urn, String artifactType) {
        String[] parts = segments(urn);
        if (parts == null) return null;
        parts[TYPE_INDEX] = artifactType;
        return String.join(":", parts);
    }

    /**
     * The identity of an externally-identified artifact (e.g. an XÖV standard): the same stable
     * {@code key} always yields the same UUID, so re-importing the standard hits the same URN
     * instead of minting a duplicate.
     */
    public static UUID deriveId(String key) {
        return nameBasedId(CORE_NAMESPACE, key == null ? "" : key);
    }

    /**
     * The identity of the member {@code key} of the artifact {@code parent}, e.g. a DataStructure's
     * {@code $defs} entry. Stable per pair, so a re-imported member versions the existing Element,
     * and distinct for distinct keys of one parent.
     */
    public static UUID memberId(UUID parent, String key) {
        return nameBasedId(parent, key);
    }

    /**
     * A name-based UUIDv8 with SHA-256, as in RFC 9562 Appendix B.2. UUIDv5 would need SHA-1, which
     * BSI TR-02102-1 no longer permits.
     */
    private static UUID nameBasedId(UUID namespace, String name) {
        byte[] input = ByteBuffer.allocate(16 + name.getBytes(StandardCharsets.UTF_8).length)
            .putLong(namespace.getMostSignificantBits())
            .putLong(namespace.getLeastSignificantBits())
            .put(name.getBytes(StandardCharsets.UTF_8))
            .array();
        ByteBuffer hash = ByteBuffer.wrap(sha256(input));
        long msb = (hash.getLong() & ~0xF000L) | 0x8000L;
        long lsb = (hash.getLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Canonical slug-sanitiser for identifiers that end up in a CORE URN segment, such as an
     * XRepository domain identifier.
     *
     * <p>Rules applied in order:
     * <ol>
     *   <li>Null or blank input → {@code "unknown"}
     *   <li>Trim whitespace
     *   <li>Replace every char outside {@code [a-zA-Z0-9._-]} with {@code "-"}
     *   <li>Collapse consecutive hyphens to one
     * </ol>
     *
     * Dots are preserved so that names like {@code xinneres.xmeld} remain intact. Case is
     * preserved; callers that require lowercase call {@code .toLowerCase()} on the result.
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

    /** The URN's segments when it is a well-formed CORE URN, otherwise {@code null}. */
    private static String[] segments(String urn) {
        if (urn == null || !urn.startsWith("urn:core:")) return null;
        // The -1 limit keeps trailing empty segments, which the default split discards.
        String[] parts = urn.split(":", -1);
        if (parts.length != LOGICAL_SEGMENTS && parts.length != LOGICAL_SEGMENTS + 1) return null;
        for (String part : parts) {
            if (part.isEmpty()) return null;
        }
        if (!ID.matcher(parts[ID_INDEX]).matches()) return null;
        if (parts.length > LOGICAL_SEGMENTS
                && !LATEST.equals(parts[VERSION_INDEX])
                && !isVersion(parts[VERSION_INDEX])) {
            return null;
        }
        return parts;
    }
}

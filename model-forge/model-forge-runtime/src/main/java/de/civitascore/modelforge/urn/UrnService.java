package de.civitascore.modelforge.urn;

import java.security.SecureRandom;

/**
 * Generates new CORE URNs from the configured namespace.
 *
 * <p>URN format:
 * {@code urn:core:<scope>:<owner>:<artifact-type>:<domain>:<name>:<disambiguator>:<version>}
 *
 * <p>The <em>disambiguator</em> is a short token in its own segment that keeps two artifacts with
 * an equal display name from colliding onto the same logical URN. It comes in two flavours:
 * <ul>
 *   <li><b>minted</b> — a fresh random token, used whenever Model Forge derives a URN from a
 *       display name (schema titles, {@code $defs} keys, host-supplied names). Equal names get
 *       distinct identities.
 *   <li><b>derived</b> — a deterministic token from a stable external identity
 *       ({@link #disambiguatorFor(String)}), so re-importing the same external standard resolves
 *       to the same URN instead of a duplicate.
 * </ul>
 *
 * <p>For <em>parsing</em> existing URNs use {@link UrnParser}, a stateless utility with no Spring
 * dependency.
 *
 * <p>Spring applications provide this type as a bean from their configuration:
 * <pre>
 *   model-forge.urn.scope:           platform   (default)
 *   model-forge.urn.owner:           civitas    (default)
 *   model-forge.urn.domain:          common     (default)
 *   model-forge.urn.default-version: 1.0.0      (default)
 * </pre>
 */
public class UrnService {

    /** base36 alphabet — URN-segment-safe, no colons, readable in logs. */
    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    /** Disambiguator length; 10 base36 chars ≈ 51 bits — collision-free within a name bucket. */
    private static final int TOKEN_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String scope;
    private final String owner;
    private final String domain;
    private final String defaultVersion;

    public UrnService(String scope, String owner, String domain, String defaultVersion) {
        this.scope          = scope;
        this.owner          = owner;
        this.domain         = domain;
        this.defaultVersion = defaultVersion;
    }

    // ── Minting generators (fresh random disambiguator) ─────────────────────────

    public String mintElement(String name) {
        return mint("element", name, defaultVersion);
    }

    public String mintElement(String name, String version) {
        return mint("element", name, version);
    }

    public String mintDataSet(String name) {
        return mint("dataset", name, defaultVersion);
    }

    public String mintDataStructure(String name) {
        return mint("datastructure", name, defaultVersion);
    }

    public String mintMapping(String name) {
        return mint("mapping", name, defaultVersion);
    }

    public String mintPipeline(String name) {
        return mint("pipeline", name, defaultVersion);
    }

    public String mintDataSource(String name) {
        return mint("datasource", name, defaultVersion);
    }

    public String mintDataSink(String name) {
        return mint("datasink", name, defaultVersion);
    }

    // ── Reproduce an identity from explicit parts ───────────────────────────────
    // Used where the disambiguator is not fresh but taken from an existing artifact — e.g. the
    // DataStructure grouping that must stay stable across re-imports of the same document.

    public String element(String name, String disambiguator, String version) {
        return build("element", name, disambiguator, version);
    }

    public String dataStructure(String name, String disambiguator) {
        return build("datastructure", name, disambiguator, defaultVersion);
    }

    // ── Resolve-or-generate helpers ─────────────────────────────────────────────
    // Return the id unchanged when it is already a CORE URN; otherwise mint a fresh URN from it.

    public String mappingUrn(String id) {
        return UrnParser.isUrn(id) ? id : mintMapping(id);
    }

    public String dataSourceUrn(String id) {
        return UrnParser.isUrn(id) ? id : mintDataSource(id);
    }

    public String dataSinkUrn(String id) {
        return UrnParser.isUrn(id) ? id : mintDataSink(id);
    }

    public String pipelineUrn(String id) {
        return UrnParser.isUrn(id) ? id : mintPipeline(id);
    }

    // ── Disambiguator tokens ────────────────────────────────────────────────────

    /** A fresh random disambiguator for a Model-Forge-derived identity. */
    public String mintDisambiguator() {
        char[] out = new char[TOKEN_LENGTH];
        for (int i = 0; i < out.length; i++) {
            out[i] = BASE36[RANDOM.nextInt(BASE36.length)];
        }
        return new String(out);
    }

    /**
     * A deterministic disambiguator for an externally-identified artifact (e.g. an XÖV standard):
     * the same stable {@code key} always yields the same token, so re-importing the standard hits
     * the same URN instead of minting a duplicate. Delegates to {@link UrnParser#deriveDisambiguator}.
     */
    public String disambiguatorFor(String key) {
        return UrnParser.deriveDisambiguator(key);
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private String mint(String artifactType, String name, String version) {
        return build(artifactType, UrnParser.sanitize(name), mintDisambiguator(), version);
    }

    private String build(String artifactType, String name, String disambiguator, String version) {
        return String.format("urn:core:%s:%s:%s:%s:%s:%s:%s",
            scope, owner, artifactType, domain, name, disambiguator, version);
    }
}

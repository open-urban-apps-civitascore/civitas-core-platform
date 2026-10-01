package de.civitascore.modelforge.urn;

import java.util.UUID;

/**
 * Generates new CORE URNs from the configured namespace.
 *
 * <p>URN format: {@code urn:core:<scope>:<owner>:<artifact-type>:<domain>:<uuid>:<version>}
 *
 * <p>The UUID is the artifact's identity. It comes from one of three places:
 * <ul>
 *   <li><b>given</b> — the id of the host entity the artifact backs, so the host and the registry
 *       name the same thing with the same UUID.
 *   <li><b>minted</b> — a random UUID, for an artifact that has no host entity.
 *   <li><b>derived</b> — a name-based UUID from a stable external identity
 *       ({@link UrnParser#deriveId(String)}) or from a parent and a member key
 *       ({@link UrnParser#memberId(UUID, String)}), so a re-import resolves to the same URN.
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

    // ── Minting (fresh random UUID) ─────────────────────────────────────────────

    public String mintElement() {
        return urn("element", UUID.randomUUID(), defaultVersion);
    }

    public String mintElement(String version) {
        return urn("element", UUID.randomUUID(), version);
    }

    /** A new URN of {@code artifactType} under a random UUID, at the default version. */
    public String mint(String artifactType) {
        return urn(artifactType, UUID.randomUUID(), defaultVersion);
    }

    // ── From a known UUID ───────────────────────────────────────────────────────

    /** The URN of {@code artifactType} for {@code id}, at the default version. */
    public String urn(String artifactType, UUID id) {
        return urn(artifactType, id, defaultVersion);
    }

    public String element(UUID id) {
        return urn("element", id, defaultVersion);
    }

    public String element(UUID id, String version) {
        return urn("element", id, version);
    }

    public String dataStructure(UUID id) {
        return urn("datastructure", id, defaultVersion);
    }

    /** The logical URN of the Element that is member {@code key} of the artifact {@code parent}. */
    public String member(UUID parent, String key) {
        return UrnParser.logicalUrn(element(UrnParser.memberId(parent, key)));
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private String urn(String artifactType, UUID id, String version) {
        return String.format("urn:core:%s:%s:%s:%s:%s:%s",
            scope, owner, artifactType, domain, id, version);
    }
}

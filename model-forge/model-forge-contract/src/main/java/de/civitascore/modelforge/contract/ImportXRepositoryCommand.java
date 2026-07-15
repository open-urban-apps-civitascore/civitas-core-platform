package de.civitascore.modelforge.contract;

/**
 * Imports one artifact from the XRepository (xOEV) catalog by its version identifier.
 * {@code importAsXsd = true} stores the raw XSD as-is; {@code false} (default behaviour when
 * unset) converts it to JSON Schema Elements first.
 *
 * @param preserveUpstreamVersion when {@code true}, {@code version} is adopted verbatim as the
 *     stored artifact version instead of Model Forge's usual version authority (1.0.0 for a new
 *     artifact, otherwise the next SemVer bump) — opt-in, off by default.
 */
public record ImportXRepositoryCommand(
    String identifier,
    String version,
    String scope,
    String owner,
    boolean importAsXsd,
    boolean preserveUpstreamVersion
) {
}

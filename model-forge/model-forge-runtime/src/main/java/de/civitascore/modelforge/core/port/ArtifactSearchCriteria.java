package de.civitascore.modelforge.core.port;

/**
 * Filter criteria for a cross-artifact search.
 */
public record ArtifactSearchCriteria(
    String q,
    boolean fuzzy,
    String type,
    String format,
    String property,
    String ref,
    int limit,
    int offset
) {
}

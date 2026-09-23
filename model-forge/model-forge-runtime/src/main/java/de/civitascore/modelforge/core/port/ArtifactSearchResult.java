package de.civitascore.modelforge.core.port;

/**
 * One hit of a cross-artifact search.
 */
public record ArtifactSearchResult(
    String id,
    String logicalId,
    String type,
    String title,
    String version,
    String format,
    int score
) {
}

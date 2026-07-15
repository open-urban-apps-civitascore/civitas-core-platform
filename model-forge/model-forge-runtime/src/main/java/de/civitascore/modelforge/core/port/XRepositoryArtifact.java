package de.civitascore.modelforge.core.port;

/**
 * Search hit returned by an XRepository-compatible catalogue.
 */
public record XRepositoryArtifact(
    String identifier,
    String parentIdentifier,
    String name,
    String version,
    String description,
    String namespace,
    String kind,
    String status
) {
}

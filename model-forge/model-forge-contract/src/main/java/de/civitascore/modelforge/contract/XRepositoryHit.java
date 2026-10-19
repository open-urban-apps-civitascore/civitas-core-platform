package de.civitascore.modelforge.contract;

/** One XRepository (xOEV) catalog search result. */
public record XRepositoryHit(
    String identifier,
    String parentIdentifier,
    String name,
    String version,
    String description,
    String kind,
    String status
) {
}

package de.civitascore.modelforge.contract;

/**
 * Cross-artifact search filter. Any field left {@code null}/blank is unfiltered;
 * {@code limit <= 0} falls back to a facade-chosen default page size.
 */
public record ArtifactSearchQuery(
    String text,
    String type,
    String format,
    int limit,
    int offset
) {
}

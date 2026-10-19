package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * One search result row: enough to list and link to an artifact without fetching its content.
 */
public record ArtifactSummary(
    ArtifactId artifactId,
    String type,
    String title,
    String version,
    String format
) {

    public ArtifactSummary {
        Objects.requireNonNull(artifactId, "artifactId");
    }
}

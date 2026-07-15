package de.civitascore.modelforge.application;

import java.util.List;

public record XRepositorySearchPage(
    List<XRepositoryArtifactSummary> items,
    long total,
    int page,
    int size
) {
    public record XRepositoryArtifactSummary(
        String identifier,
        String parentIdentifier,
        String name,
        String version,
        String description,
        String namespace,
        String kind,
        String status
    ) {}
}

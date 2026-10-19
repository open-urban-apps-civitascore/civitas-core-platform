package de.civitascore.modelforge.core.port;

import java.util.List;
import java.util.Objects;

/**
 * Paged XRepository search result independent of the concrete HTTP client.
 */
public record XRepositorySearchResult(
    List<XRepositoryArtifact> items,
    long total
) {

    public XRepositorySearchResult {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }
}

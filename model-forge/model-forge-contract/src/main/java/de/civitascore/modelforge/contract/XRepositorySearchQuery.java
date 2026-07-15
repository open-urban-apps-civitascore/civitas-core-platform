package de.civitascore.modelforge.contract;

/** Search filter for the XRepository (xOEV) catalog. {@code page} is 0-based. */
public record XRepositorySearchQuery(String query, int page, int size) {
}

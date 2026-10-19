package de.civitascore.modelforge.core.port;

/**
 * Core-facing boundary for XRepository search and XSD download.
 */
public interface XRepositoryCatalog {

    XRepositorySearchResult search(String query, int page, int size);

    String downloadXsd(String identifier);
}

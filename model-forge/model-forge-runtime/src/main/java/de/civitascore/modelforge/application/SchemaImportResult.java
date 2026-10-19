package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.Diagnostic;
import java.util.List;

/**
 * @param resourceId         the primary (root) artifact's pin; {@code null} on failure
 * @param importedResourceIds every artifact this import created, root first — a {@code $defs}
 *     import can create several Elements at once. The 2-arg constructor derives this as
 *     {@code [resourceId]} (or empty when {@code resourceId} is {@code null}), correct for the
 *     single-artifact paths (XSD import, failure diagnostics); a multi-artifact import passes the
 *     full list explicitly.
 */
public record SchemaImportResult(String resourceId, List<String> importedResourceIds, List<Diagnostic> diagnostics) {

    public SchemaImportResult(String resourceId, List<Diagnostic> diagnostics) {
        this(resourceId, resourceId != null ? List.of(resourceId) : List.of(), diagnostics);
    }
}

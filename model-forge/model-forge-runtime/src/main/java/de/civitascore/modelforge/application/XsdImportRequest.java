package de.civitascore.modelforge.application;

/**
 * Internal service input for a raw XSD import (no JSON Schema conversion) — the XSD counterpart to
 * {@link SchemaImportRequest}. Unlike JSON Schema, XSD carries no {@code $id}/{@code title} of its
 * own, so {@code name} is required and {@code artifactId} is the caller-owned escape hatch for an
 * explicit identity (e.g. an XRepository "Kennung"-derived URN).
 *
 * @param name            display name; also the source for a minted identity when {@code artifactId} is absent
 * @param version         version segment; only interpolated into a minted identity, or (with
 *                         {@link #preserveVersion}) adopted verbatim as the stored version
 * @param xsdContent      the raw XSD document text
 * @param artifactId      an explicit, caller-owned CORE URN identity; {@code null} mints one from {@code name}
 * @param preserveVersion when {@code true}, {@code version} is adopted verbatim as the stored
 *     Element's version instead of Model Forge's usual version authority — see
 *     {@code ArtifactRegistry#storeElement}'s equivalent parameter
 */
public record XsdImportRequest(String name, String version, String xsdContent, String artifactId,
                                boolean preserveVersion) {
    public XsdImportRequest(String name, String version, String xsdContent, String artifactId) {
        this(name, version, xsdContent, artifactId, false);
    }
}

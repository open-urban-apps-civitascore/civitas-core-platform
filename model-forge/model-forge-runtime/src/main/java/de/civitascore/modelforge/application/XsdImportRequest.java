package de.civitascore.modelforge.application;

/**
 * Internal service input for a raw XSD import (no JSON Schema conversion) — the XSD counterpart to
 * {@link SchemaImportRequest}. Unlike JSON Schema, XSD carries no {@code $id}/{@code title} of its
 * own, so {@code name} and the caller-owned {@code artifactId} (e.g. an XRepository
 * "Kennung"-derived URN) are required.
 *
 * @param name            display name
 * @param version         the upstream version, refused when malformed; stored only with {@code preserveVersion}
 * @param xsdContent      the raw XSD document text
 * @param artifactId      the caller-owned CORE URN identity
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

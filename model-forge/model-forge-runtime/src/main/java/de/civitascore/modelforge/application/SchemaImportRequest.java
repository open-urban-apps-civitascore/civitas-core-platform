package de.civitascore.modelforge.application;

import de.civitascore.modelforge.contract.VersionBump;
import tools.jackson.databind.JsonNode;

/**
 * Internal service input for a JSON Schema import — the {@code SchemaImportService} counterpart to
 * the public {@link de.civitascore.modelforge.contract.ImportSchemaCommand} facade command (the
 * naming convention is {@code Command} for public contract inputs, {@code Request} for internal
 * service inputs). It additionally carries {@code version}/{@code preserveVersion}, which the
 * public API deliberately hides.
 *
 * <p>{@code $id}/{@code title} on the document itself name the artifact — Model Forge mints an
 * identity when either is absent (see {@code SchemaImportService#buildElements}); there is no
 * separate name/artifactId field here.
 *
 * @param schema          the JSON Schema document to import
 * @param version         explicit version — only meaningful together with {@link #preserveVersion}
 * @param preserveVersion when {@code true}, {@code version} is adopted verbatim as every produced
 *     Element's stored version instead of Model Forge's usual version authority — the opt-in
 *     escape hatch for imports (e.g. XÖV) that must preserve an upstream version identity
 * @param bump the change class to apply to the grouping and to every member whose content changed.
 *     A member that is byte-identical mints no version at all, so the bump never inflates history.
 *     Ignored when {@code preserveVersion} adopts an upstream version instead. Defaults to
 *     {@code PATCH}, matching the write path's own default.
 */
public record SchemaImportRequest(
        JsonNode schema, String version, boolean preserveVersion, VersionBump bump) {

    public SchemaImportRequest {
        if (bump == null) {
            bump = VersionBump.PATCH;
        }
    }

    public SchemaImportRequest(JsonNode schema) {
        this(schema, null, false, VersionBump.PATCH);
    }

    public SchemaImportRequest(JsonNode schema, VersionBump bump) {
        this(schema, null, false, bump);
    }

    public SchemaImportRequest(JsonNode schema, String version, boolean preserveVersion) {
        this(schema, version, preserveVersion, VersionBump.PATCH);
    }
}

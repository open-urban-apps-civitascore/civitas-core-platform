package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to import one JSON Schema document into Model Forge. The import stores the Elements
 * (splitting {@code $defs}) plus their automatic DataStructure grouping — composition into a
 * DataSet is the caller's concern: update the DataSet manifest via
 * {@link de.civitascore.modelforge.facade.ModelForge#saveArtifact(SaveArtifactCommand)}.
 *
 * <p>Model Forge owns version assignment. A version segment inside the document's {@code $id}s is
 * advisory; {@link #version} together with {@link #preserveVersion} is the only way to claim one.
 * {@link #bumpFromVersion} does not claim a version — it only names which existing one the bump
 * counts from.
 *
 * @param schema the JSON Schema document (required)
 * @param bump the change class to apply to the grouping and to every member whose content changed.
 *     A member that is byte-identical mints no version at all, so the bump never inflates history.
 *     Defaults to {@code PATCH}.
 * @param version explicit version — only meaningful together with {@link #preserveVersion}
 * @param preserveVersion when {@code true}, {@code version} is adopted verbatim instead of Model
 *     Forge's version authority. Intended for the first write of an artifact — one that claims a
 *     version an artifact already holds fails as a version collision rather than being coerced.
 * @param bumpFromVersion the existing version of the grouping that {@code bump} counts from, for a
 *     caller that revises one particular version rather than the newest. Applies to the
 *     DataStructure grouping only: a member Element records a content history, so its bump always
 *     counts from its own newest version. {@code null} counts from the grouping's newest version.
 */
public record ImportSchemaCommand(
    JsonNode schema, VersionBump bump, String version, boolean preserveVersion,
    String bumpFromVersion) {

    public ImportSchemaCommand {
        Objects.requireNonNull(schema, "schema");
        if (bump == null) {
            bump = VersionBump.PATCH;
        }
    }

    public ImportSchemaCommand(JsonNode schema) {
        this(schema, VersionBump.PATCH, null, false, null);
    }

    public ImportSchemaCommand(JsonNode schema, VersionBump bump) {
        this(schema, bump, null, false, null);
    }

    public ImportSchemaCommand(JsonNode schema, VersionBump bump, String bumpFromVersion) {
        this(schema, bump, null, false, bumpFromVersion);
    }
}

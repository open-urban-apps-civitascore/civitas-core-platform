package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to create or update one artifact. XSD content is carried as a
 * {@code StringNode} wrapping the raw XML, matching {@link ArtifactView#content()} for XSD
 * artifacts; every other kind carries its native JSON content directly.
 *
 * @param dataSet optional CORE URN of a DataSet to link the saved artifact into as a member; when
 *                non-null, Model Forge adds it to that DataSet's manifest. {@code null} means no
 *                DataSet membership (the default).
 * @param bumpFromVersion the artifact's existing version that {@code versionBump} counts from, for
 *                a caller that revises one particular version rather than the newest. {@code null}
 *                counts from the artifact's newest version (the default).
 */
public record SaveArtifactCommand(
    ArtifactId artifactId,
    ArtifactKind kind,
    JsonNode content,
    VersionBump versionBump,
    String dataSet,
    String bumpFromVersion
) {

    public SaveArtifactCommand {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(versionBump, "versionBump");
    }

    /** Backward-compatible: save without linking the artifact into any DataSet. */
    public SaveArtifactCommand(ArtifactId artifactId, ArtifactKind kind, JsonNode content, VersionBump versionBump) {
        this(artifactId, kind, content, versionBump, null, null);
    }

    /** Save with DataSet membership, numbering from the artifact's newest version. */
    public SaveArtifactCommand(ArtifactId artifactId, ArtifactKind kind, JsonNode content,
                               VersionBump versionBump, String dataSet) {
        this(artifactId, kind, content, versionBump, dataSet, null);
    }
}

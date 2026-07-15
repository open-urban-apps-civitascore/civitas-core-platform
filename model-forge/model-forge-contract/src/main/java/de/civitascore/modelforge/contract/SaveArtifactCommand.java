package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * Command to create or update one artifact. XSD content is carried as a
 * {@code StringNode} wrapping the raw XML, matching {@link ArtifactView#content()} for XSD
 * artifacts; every other kind carries its native JSON content directly.
 */
public record SaveArtifactCommand(
    ArtifactId artifactId,
    ArtifactKind kind,
    JsonNode content,
    VersionBump versionBump
) {

    public SaveArtifactCommand {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(versionBump, "versionBump");
    }
}

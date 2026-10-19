package de.civitascore.modelforge.contract;

import tools.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * JSON representation of one resolved artifact.
 */
public record ArtifactView(ArtifactId artifactId, JsonNode content) {

    public ArtifactView {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(content, "content");
    }
}

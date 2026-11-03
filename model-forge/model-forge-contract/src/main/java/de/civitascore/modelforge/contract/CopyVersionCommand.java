package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * Command to copy one version of an artifact to a new MAJOR version, leaving its content untouched.
 *
 * @param artifactId the artifact and the source version to copy. A Versioned URN names the source;
 *     a logical URN or {@code :latest} names the current version.
 */
public record CopyVersionCommand(ArtifactId artifactId) {

    public CopyVersionCommand {
        Objects.requireNonNull(artifactId, "artifactId");
    }
}

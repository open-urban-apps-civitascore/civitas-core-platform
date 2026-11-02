package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * Command to copy one version of an artifact to a new version at a requested change class, leaving
 * its content untouched.
 *
 * @param artifactId the artifact and the source version to copy. A pinned version names the source;
 *     a logical URN or {@code :latest} names the current version. The pin selects the content only,
 *     never the number base: the new version always counts from the artifact's highest version.
 * @param bump the change class the new version is numbered at. Defaults to {@code PATCH}.
 */
public record BumpVersionCommand(ArtifactId artifactId, VersionBump bump) {

    public BumpVersionCommand {
        Objects.requireNonNull(artifactId, "artifactId");
        if (bump == null) {
            bump = VersionBump.PATCH;
        }
    }

    public BumpVersionCommand(ArtifactId artifactId) {
        this(artifactId, VersionBump.PATCH);
    }
}

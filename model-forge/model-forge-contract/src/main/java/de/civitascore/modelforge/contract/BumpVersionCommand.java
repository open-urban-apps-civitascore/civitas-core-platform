package de.civitascore.modelforge.contract;

import java.util.Objects;

/**
 * Command to carry an artifact's current version forward as a new version at a requested change
 * class, leaving its content untouched.
 *
 * @param artifactId the artifact to bump, as a logical URN or {@code :latest}. A pinned version is
 *     rejected: bumping from a version that is not the current one has no sound answer, and bumping
 *     from the current one instead would ignore what the caller asked for.
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

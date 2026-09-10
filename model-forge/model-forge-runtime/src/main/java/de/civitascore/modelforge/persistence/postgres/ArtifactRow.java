package de.civitascore.modelforge.persistence.postgres;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the {@code model_forge.artifact} table — the logical, version-independent identity.
 *
 * <p>The format is intentionally NOT here: it is a property of each version's
 * representation, not of the model_forge.artifact identity (see {@code model_forge.artifact_representation}).
 * {@code currentVersion} is the newest written version (the default read).
 */
record ArtifactRow(
    UUID id,
    String logicalUrn,
    String artifactType,
    String name,
    String title,
    String description,
    String currentVersion,
    Instant createdAt,
    Instant updatedAt) {}

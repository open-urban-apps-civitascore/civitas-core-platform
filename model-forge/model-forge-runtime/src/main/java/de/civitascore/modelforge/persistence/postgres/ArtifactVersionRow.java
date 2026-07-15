package de.civitascore.modelforge.persistence.postgres;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the {@code model_forge.artifact_version} table — version metadata for one version.
 *
 * <p>The per-format content lives in {@code model_forge.artifact_representation}; this row only
 * carries the version's metadata. {@code primaryFormat} is the authored format of the
 * version ({@code jsonschema} | {@code core-json} | {@code xsd}). {@code title}/
 * {@code description} are versioned metadata (set on rename), {@code null} when unset.
 */
record ArtifactVersionRow(
    UUID id,
    UUID artifactId,
    String version,
    String primaryFormat,
    String title,
    String description,
    Instant createdAt,
    String createdBy) {}

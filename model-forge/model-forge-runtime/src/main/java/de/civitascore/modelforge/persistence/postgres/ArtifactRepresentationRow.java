package de.civitascore.modelforge.persistence.postgres;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the {@code model_forge.artifact_representation} table — format-specific content for a
 * single {@code model_forge.artifact_version}.
 *
 * <p>{@code contentJson} holds the canonical JSON text for JSON-backed formats (null for
 * XSD); {@code contentText} holds the raw XSD text (null for JSON formats).
 * {@code generation} is {@code "stored"} for authored content or {@code "generated"} for
 * a derived representation (e.g. JSON Schema produced from an XSD).
 */
record ArtifactRepresentationRow(
    UUID id,
    UUID versionId,
    String format,
    String contentType,
    String contentJson,
    String contentText,
    String contentHash,
    String generation,
    Instant createdAt) {}

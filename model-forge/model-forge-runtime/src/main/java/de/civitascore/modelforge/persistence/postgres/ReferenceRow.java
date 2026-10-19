package de.civitascore.modelforge.persistence.postgres;

/**
 * A dependency edge to persist for an model_forge.artifact version. The repository assigns the
 * surrogate id, {@code from_version_id}, the resolved {@code target_artifact_id} and the
 * timestamp; callers supply the edge's logical target and metadata.
 *
 * @param targetUrn     logical URN of the referenced model_forge.artifact
 * @param referenceType edge kind (schema-ref, dataset-ref, pipeline-node, xsd-import, …)
 * @param referenceName optional human/sub-discriminator label (may be null)
 * @param sortOrder     position within the source document (preserves array/node order)
 */
record ReferenceRow(String targetUrn, String referenceType, String referenceName, int sortOrder) {}

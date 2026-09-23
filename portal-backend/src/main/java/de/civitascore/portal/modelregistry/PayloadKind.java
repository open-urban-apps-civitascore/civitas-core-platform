package de.civitascore.portal.modelregistry;

/**
 * Host-side artifact category for opaque JSON payloads stored in the Model Forge registry (stage
 * 4). Mirrors the Model Forge {@code ArtifactKind} values the host uses, without leaking the Model
 * Forge type outside the {@code modelregistry} package.
 */
public enum PayloadKind {
  /** Editor-built pipeline definition (engine-neutral graph). */
  PIPELINE,
  /** Connector configuration of a data source. */
  DATA_SOURCE,
  /** Type-specific configuration of a data sink. */
  DATA_SINK,
  /**
   * Declarative field-to-field mapping between two DataStructures, extracted from a pipeline node.
   */
  MAPPING,
  /** Dataset manifest (currently unused — datasets own no opaque JSON payload). */
  DATA_SET
}

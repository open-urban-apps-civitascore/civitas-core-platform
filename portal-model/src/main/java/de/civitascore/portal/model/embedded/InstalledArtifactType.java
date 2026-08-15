package de.civitascore.portal.model.embedded;

/**
 * Artifact types a bundle install can record. Grows with the import increments (pipelines, data
 * sinks) — the provenance table is additive by design.
 *
 * <p>Deliberately portal-owned rather than reusing Model Forge's {@code ArtifactKind}: this module
 * must not depend on the model-forge contract. When later increments add types, the mapping between
 * the two vocabularies belongs in a single place in {@code ModelRegistryGateway}.
 */
public enum InstalledArtifactType {
  DATA_STRUCTURE,
  DATA_SOURCE,

  /**
   * A registry-only artifact: unlike the two above, a mapping has no host shell row, so its
   * provenance line carries a URN but no {@code shellId}.
   */
  MAPPING
}

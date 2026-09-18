package de.civitascore.portal.model.embedded;

/**
 * Artifact types an install can record. Grows with the import increments — the provenance table is
 * additive by design, and the column stores the name as text.
 *
 * <p>Deliberately portal-owned rather than reusing Model Forge's {@code ArtifactKind}: this module
 * must not depend on the model-forge contract. The mapping between the two vocabularies belongs in
 * a single place in {@code ModelRegistryGateway}.
 */
public enum InstalledArtifactType {
  DATA_STRUCTURE,
  DATA_SOURCE,

  /**
   * A registry-only artifact: unlike the two above, a mapping has no host shell row, so its
   * provenance line carries a URN but no {@code shellId}.
   */
  MAPPING,

  /**
   * The dataset an install produced. Recorded as a line rather than in the installation header,
   * because an install need not produce one at all and because a line can carry its own action.
   */
  DATA_SET,

  /**
   * A data sink created on the install's dataset. Has a shell row and a minted configuration
   * artifact in the registry, so the line carries both ids. Always CREATED: a sink has no portable
   * identity a second package could resolve against, so there is nothing to reuse.
   */
  DATA_SINK,

  /**
   * A pipeline created on the install's dataset. Like a sink it carries shell id plus its minted
   * model URN, and is always CREATED for the same reason.
   */
  PIPELINE
}

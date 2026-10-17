package de.civitascore.portal.model.embedded;

/**
 * What an install did with one contained artifact. Recorded because uninstall depends on it — a
 * REUSED artifact was not brought by this package and must never be removed with it.
 */
public enum InstalledArtifactAction {
  /** Newly created by this install, as a copy under a URN minted here. */
  CREATED,

  /**
   * An existing copy on this instance that this install bound to instead of creating its own — a
   * prerequisite the package declared, resolved by origin.
   */
  REUSED
}

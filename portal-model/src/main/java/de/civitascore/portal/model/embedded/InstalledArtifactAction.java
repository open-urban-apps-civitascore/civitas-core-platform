package de.civitascore.portal.model.embedded;

/**
 * What an install did with one contained artifact. Recorded because reference counting on uninstall
 * depends on it — a REUSED artifact was not brought by this package and must never be removed with
 * it.
 */
public enum InstalledArtifactAction {
  /** Newly created by this install. */
  CREATED,

  /** Already installed with identical content; this install only linked it. */
  REUSED
}
